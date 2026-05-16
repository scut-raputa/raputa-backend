package cn.scut.raputa.service;

import cn.scut.raputa.dto.DeviceDiscoveryDTO;
import cn.scut.raputa.dto.DeviceDiscoveryResponseDTO;
import cn.scut.raputa.entity.Device;
import cn.scut.raputa.repository.DeviceRepository;
import cn.scut.raputa.utils.SocketTools;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 设备发现服务
 * 
 * @author RAPUTA Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceDiscoveryService {

    private final DeviceRepository deviceRepository;
    
    private volatile boolean isDiscovering = false;
    private DatagramSocket udpSocket = null;
    
    /**
     * 开始设备发现
     * 
     * @param request 设备发现请求参数
     * @return 设备发现结果
     */
    public CompletableFuture<DeviceDiscoveryResponseDTO> startDeviceDiscovery(DeviceDiscoveryDTO request) {
        if (isDiscovering) {
            return CompletableFuture.completedFuture(
                new DeviceDiscoveryResponseDTO(null, null, null, "DISCOVERING", 
                    System.currentTimeMillis(), "设备发现已在进行中", null)
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            isDiscovering = true;
            try {
                return discoverDevice(request);
            } finally {
                isDiscovering = false;
                closeSocket();
            }
        });
    }
    
    /**
     * 停止设备发现
     */
    public void stopDeviceDiscovery() {
        isDiscovering = false;
        closeSocket();
        log.info("设备发现已停止");
    }
    
    /**
     * 获取设备发现状态
     * 
     * @return 是否正在发现设备
     */
    public boolean isDiscovering() {
        return isDiscovering;
    }
    
    /**
     * 执行设备发现
     * 
     * @param request 请求参数
     * @return 设备信息
     */
    private DeviceDiscoveryResponseDTO discoverDevice(DeviceDiscoveryDTO request) {
        try {
            // 初始化UDP Socket
            udpSocket = new DatagramSocket(request.getPort());
            udpSocket.setSoTimeout(request.getTimeout());
            
            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            
            log.info("开始设备发现，监听端口: {}, 超时时间: {}ms", request.getPort(), request.getTimeout());
            
            int retryCount = 0;
            int maxRetries = 2; // 最大重试次数
            
            while (isDiscovering && retryCount < maxRetries) {
                try {
                    // 接收UDP数据包
                    udpSocket.receive(receivePacket);
                    
                    // 解析接收到的数据
                    byte[] receivedData = receivePacket.getData();
                    List<byte[]> parsedData = SocketTools.anlyBufData(receivedData);
                    
                    if (parsedData.size() >= 4) {
                        byte[] deviceData = parsedData.get(3);
                        String dataString = new String(deviceData, 0, deviceData.length);
                        
                        log.info("解析到的JSON数据: {}", dataString);
                        JsonNode jsonNode = SocketTools.getJsonObject(dataString);
                        JsonNode ipNode = jsonNode.get("ip");
                        String hardwareId = extractHardwareId(jsonNode);
                        
                        if (ipNode != null && !ipNode.isNull()) {
                            String deviceIp = ipNode.asText();
                            
                            if (SocketTools.isValidIpAddress(deviceIp)) {
                                log.info("发现设备，IP地址: {}, 硬件标识: {}", deviceIp, hardwareId);
                                
                                // 安全获取设备名称
                                String deviceName = "Unknown";
                                JsonNode nameNode = jsonNode.get("name");
                                if (nameNode != null && !nameNode.isNull()) {
                                    deviceName = nameNode.asText();
                                }

                                Device discoveredDevice = upsertDiscoveredDevice(deviceIp, deviceName, hardwareId);
                                String discoveredDeviceId = discoveredDevice != null
                                        ? discoveredDevice.getId()
                                        : inferDiscoveredId(hardwareId, deviceIp);
                                String resolvedDeviceName = discoveredDevice != null
                                        ? discoveredDevice.getName()
                                        : fallbackDiscoveredDeviceName(deviceName, deviceIp);
                                String discoveredRtspPath = discoveredDevice != null
                                        ? discoveredDevice.getRtspPath()
                                        : "/stream/audio";
                                
                                return new DeviceDiscoveryResponseDTO(
                                    discoveredDeviceId,
                                    deviceIp,
                                    resolvedDeviceName,
                                    "ONLINE",
                                    System.currentTimeMillis(),
                                    dataString,
                                    discoveredRtspPath
                                );
                            }
                        }
                    }
                    
                } catch (IOException e) {
                    retryCount++;
                    log.warn("设备发现超时，重试次数: {}/{}", retryCount, maxRetries);
                    
                    // 等待扫描间隔时间
                    try {
                        TimeUnit.MILLISECONDS.sleep(request.getScanInterval());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            markDiscoveryDevicesOffline();
            return notFoundResponse("未发现设备，请检查设备是否开机并连接到同一网络");
                
        } catch (SocketException e) {
            log.error("创建UDP Socket失败", e);
            return new DeviceDiscoveryResponseDTO(
                null,
                null,
                null,
                "ERROR",
                System.currentTimeMillis(),
                "网络错误: " + e.getMessage(),
                null
            );
        }
    }
    
    /**
     * 关闭Socket连接
     */
    private void closeSocket() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
            udpSocket = null;
        }
    }

    private DeviceDiscoveryResponseDTO notFoundResponse(String message) {
        return new DeviceDiscoveryResponseDTO(
                null,
                null,
                null,
                "NOT_FOUND",
                System.currentTimeMillis(),
                message,
                null
        );
    }

    private void markDiscoveryDevicesOffline() {
        try {
            List<Device> staleDevices = deviceRepository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                    .filter(device -> "在线".equals(device.getStatus()))
                    .filter(device -> {
                        String accessMode = device.getAccessMode();
                        return accessMode == null
                                || accessMode.isBlank()
                                || "DISCOVERY".equalsIgnoreCase(accessMode);
                    })
                    .toList();
            if (staleDevices.isEmpty()) {
                return;
            }
            staleDevices.forEach(device -> device.setStatus("离线"));
            deviceRepository.saveAll(staleDevices);
            log.info("本次设备发现未收到UDP响应，已将 {} 台发现型设备标记为离线", staleDevices.size());
        } catch (Exception e) {
            log.warn("设备发现失败后更新设备离线状态失败", e);
        }
    }

    private Device upsertDiscoveredDevice(String ip, String name, String hardwareId) {
        try {
            Device device = hardwareId == null || hardwareId.isBlank()
                    ? deviceRepository.findFirstByIp(ip).orElseGet(Device::new)
                    : deviceRepository.findFirstByHardwareId(hardwareId)
                            .orElseGet(() -> deviceRepository.findFirstByIp(ip).orElseGet(Device::new));
            if (device.getId() == null || device.getId().isBlank()) {
                device.setId(generateDiscoveredId(hardwareId, ip));
            }
            device.setIp(ip);
            if (hardwareId != null && !hardwareId.isBlank()) {
                device.setHardwareId(hardwareId);
            }
            if (shouldApplyDiscoveredName(device.getName())) {
                device.setName(fallbackDiscoveredDeviceName(name, ip));
            }
            if (device.getStatus() == null || device.getStatus().isBlank()) {
                device.setStatus("在线");
            }
            device.setStatus("在线");
            device.setEnabled(device.getEnabled() == null ? Boolean.TRUE : device.getEnabled());
            device.setAccessMode("DISCOVERY");
            device.setControlPort(device.getControlPort() == null ? 6667 : device.getControlPort());
            device.setRtspPath(device.getRtspPath() == null || device.getRtspPath().isBlank() ? "/stream/audio" : device.getRtspPath());
            device.setLastSeenAt(LocalDateTime.now(Device.ZONE_CN));
            device.setLastConnectedTime(LocalDateTime.now(Device.ZONE_CN));
            return deviceRepository.save(device);
        } catch (Exception e) {
            log.warn("写入发现设备注册表失败: ip={}, hardwareId={}", ip, hardwareId, e);
            return null;
        }
    }

    private boolean shouldApplyDiscoveredName(String currentName) {
        if (currentName == null || currentName.isBlank()) {
            return true;
        }
        String normalized = currentName.trim();
        return "Unknown".equalsIgnoreCase(normalized)
                || "null".equalsIgnoreCase(normalized)
                || "-".equals(normalized)
                || normalized.startsWith("发现设备-");
    }

    private String fallbackDiscoveredDeviceName(String discoveredName, String ip) {
        if (isMeaningfulDiscoveredName(discoveredName)) {
            return discoveredName.trim();
        }
        return "发现设备-" + ip;
    }

    private boolean isMeaningfulDiscoveredName(String discoveredName) {
        if (discoveredName == null || discoveredName.isBlank()) {
            return false;
        }
        String normalized = discoveredName.trim();
        return !"Unknown".equalsIgnoreCase(normalized)
                && !"null".equalsIgnoreCase(normalized)
                && !"-".equals(normalized);
    }

    private String inferDiscoveredId(String hardwareId, String ip) {
        return "DIS-" + compactIdToken(identityToken(hardwareId, ip), 11);
    }

    private String generateDiscoveredId(String hardwareId, String ip) {
        String base = "DIS-" + compactIdToken(identityToken(hardwareId, ip), 11);
        if (!deviceRepository.existsById(base)) {
            return base;
        }
        for (int i = 1; i <= 9999; i++) {
            String candidate = base + "-" + i;
            if (!deviceRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("生成发现设备编号失败");
    }

    private String extractHardwareId(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        for (String field : List.of("mac", "macAddress", "serial", "serialNo", "deviceId", "hostname")) {
            JsonNode node = jsonNode.get(field);
            if (node != null && !node.isNull()) {
                String value = node.asText("").trim();
                if (!value.isBlank()) {
                    return value.toUpperCase();
                }
            }
        }
        return null;
    }

    private String identityToken(String hardwareId, String ip) {
        return hardwareId == null || hardwareId.isBlank() ? ip : hardwareId;
    }

    private String compactIdToken(String raw, int maxLen) {
        String normalized = raw == null ? "" : raw.replaceAll("[^0-9A-Za-z]", "");
        if (normalized.isBlank()) {
            normalized = "0000";
        }
        if (normalized.length() > maxLen) {
            normalized = normalized.substring(normalized.length() - maxLen);
        }
        return normalized;
    }
}
