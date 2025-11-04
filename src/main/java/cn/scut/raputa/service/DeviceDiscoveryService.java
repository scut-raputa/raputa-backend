package cn.scut.raputa.service;

import cn.scut.raputa.dto.DeviceDiscoveryDTO;
import cn.scut.raputa.dto.DeviceDiscoveryResponseDTO;
import cn.scut.raputa.utils.SocketTools;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
public class DeviceDiscoveryService {
    
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
                new DeviceDiscoveryResponseDTO(null, null, "DISCOVERING", 
                    System.currentTimeMillis(), "设备发现已在进行中")
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
//                        JsonNode macNode = jsonNode.get("mac");
                        
                        if (ipNode != null && !ipNode.isNull()) {
                            String deviceIp = ipNode.asText();
                            
                            if (SocketTools.isValidIpAddress(deviceIp)) {
                                log.info("发现设备，IP地址: {}", deviceIp);
                                
                                // 安全获取设备名称
                                String deviceName = "Unknown";
                                JsonNode nameNode = jsonNode.get("name");
                                if (nameNode != null && !nameNode.isNull()) {
                                    deviceName = nameNode.asText();
                                }
                                
                                return new DeviceDiscoveryResponseDTO(
                                    deviceIp,
                                    deviceName,
                                    "ONLINE",
                                    System.currentTimeMillis(),
                                    dataString
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
            
            return new DeviceDiscoveryResponseDTO(
                null,
                null,
                "NOT_FOUND",
                System.currentTimeMillis(),
                "未发现设备，请检查网络连接"
            );
                
        } catch (SocketException e) {
            log.error("创建UDP Socket失败", e);
            return new DeviceDiscoveryResponseDTO(
                null,
                null,
                "ERROR",
                System.currentTimeMillis(),
                "网络错误: " + e.getMessage()
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
}
