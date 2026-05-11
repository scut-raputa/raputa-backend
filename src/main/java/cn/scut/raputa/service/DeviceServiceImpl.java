package cn.scut.raputa.service;

import cn.scut.raputa.dto.DeviceDTO;
import cn.scut.raputa.entity.Device;
import cn.scut.raputa.entity.DeviceSessionLock;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.DeviceRepository;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.DeviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceLockService deviceLockService;
    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DTMF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<DeviceVO> page(int page, int size, String id, String name,
                               String responsible, String status, String storageLocation) {
        Specification<Device> spec = Specification.<Device>unrestricted()
                .and(likeIfPresent("id", id))
                .and(likeIfPresent("name", name))
                .and(likeIfPresent("responsible", responsible))
                .and(eqIfPresent("status", status))
                .and(eqIfPresent("storageLocation", storageLocation));

        Page<Device> pg = deviceRepository.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1),
                        Sort.by(Sort.Order.asc("id"))));
        List<DeviceVO> mapped = enrichOccupancy(pg.getContent().stream().map(VoMappers::toDeviceVO).toList());
        return new PageImpl<>(mapped, pg.getPageable(), pg.getTotalElements());
    }

    @Override
    public List<String> distinctLocations() {
        return deviceRepository.findDistinctStorageLocations();
    }

    @Override
    public DeviceVO create(DeviceDTO dto) {
        Device device = new Device();
        device.setId(generateId());
        applyDto(device, dto);
        return enrichSingle(deviceRepository.save(device));
    }

    @Override
    public DeviceVO update(String id, DeviceDTO dto) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "设备不存在"));
        applyDto(device, dto);
        return enrichSingle(deviceRepository.save(device));
    }

    @Override
    public void delete(String id) {
        if (!deviceRepository.existsById(id))
            throw new BizException(404, "设备不存在");
        deviceRepository.deleteById(id);
    }

    @Override
    public DeviceVO toggleStatus(String id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "设备不存在"));
        if ("在线".equals(device.getStatus())) {
            device.setStatus("离线");
        } else {
            device.setStatus("在线");
            device.setLastConnectedTime(LocalDateTime.now(ZONE_CN));
            device.setLastSeenAt(LocalDateTime.now(ZONE_CN));
        }
        return enrichSingle(deviceRepository.save(device));
    }

    @Override
    public List<DeviceVO> registry(Boolean onlineOnly) {
        List<Device> devices = deviceRepository.findByEnabledTrueOrderByUpdatedAtDesc();
        if (Boolean.TRUE.equals(onlineOnly)) {
            devices = devices.stream().filter(d -> "在线".equals(d.getStatus())).toList();
        }
        return enrichOccupancy(devices.stream().map(VoMappers::toDeviceVO).toList());
    }

    @Override
    public boolean forceRelease(String id) {
        return deviceLockService.release(id, null, "admin-force-release", true);
    }

    @Override
    public DeviceVO upsertManualDevice(String ip, String deviceName) {
        String normalizedIp = normalizeIp(ip);
        Device device = deviceRepository.findFirstByIp(normalizedIp).orElseGet(Device::new);
        if (device.getId() == null || device.getId().isBlank()) {
            device.setId(generateManualId(normalizedIp));
        }
        if (deviceName != null && !deviceName.isBlank()) {
            device.setName(deviceName.trim());
        } else if (device.getName() == null || device.getName().isBlank()) {
            device.setName("手工设备-" + normalizedIp);
        }
        device.setIp(normalizedIp);
        device.setStatus("在线");
        device.setEnabled(true);
        device.setAccessMode("MANUAL");
        device.setControlPort(device.getControlPort() == null ? 6667 : device.getControlPort());
        device.setRtspPath(device.getRtspPath() == null || device.getRtspPath().isBlank() ? "/stream/audio" : device.getRtspPath());
        device.setLastSeenAt(LocalDateTime.now(ZONE_CN));
        device.setLastConnectedTime(LocalDateTime.now(ZONE_CN));
        return enrichSingle(deviceRepository.save(device));
    }

    private void applyDto(Device device, DeviceDTO dto) {
        device.setName(dto.getName());
        device.setIp(normalizeIp(dto.getIp()));
        device.setStatus(dto.getStatus() != null ? dto.getStatus() : "离线");
        if (dto.getAccessMode() != null && !dto.getAccessMode().isBlank()) {
            device.setAccessMode(normalizeAccessMode(dto.getAccessMode()));
        } else if (device.getAccessMode() == null || device.getAccessMode().isBlank()) {
            device.setAccessMode("DISCOVERY");
        }
        if (dto.getControlPort() != null && dto.getControlPort() > 0) {
            device.setControlPort(dto.getControlPort());
        } else if (device.getControlPort() == null || device.getControlPort() <= 0) {
            device.setControlPort(6667);
        }
        if (dto.getRtspPath() != null && !dto.getRtspPath().isBlank()) {
            device.setRtspPath(dto.getRtspPath().trim());
        } else if (device.getRtspPath() == null || device.getRtspPath().isBlank()) {
            device.setRtspPath("/stream/audio");
        }
        if (dto.getEnabled() != null) {
            device.setEnabled(dto.getEnabled());
        } else if (device.getEnabled() == null) {
            device.setEnabled(Boolean.TRUE);
        }
        device.setDescription(dto.getDescription());
        device.setStorageLocation(dto.getStorageLocation());
        device.setResponsible(dto.getResponsible());
        if (dto.getLastConnectedTime() != null) {
            device.setLastConnectedTime(parseDateTime(dto.getLastConnectedTime()));
        }
        if ("在线".equals(device.getStatus())) {
            device.setLastSeenAt(LocalDateTime.now(ZONE_CN));
        }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); } catch (Exception ignored) {}
        return null;
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) return "0.0.0.0";
        return ip.trim();
    }

    private String normalizeAccessMode(String accessMode) {
        if (accessMode == null || accessMode.isBlank()) {
            return "DISCOVERY";
        }
        String mode = accessMode.trim().toUpperCase();
        if (!"DISCOVERY".equals(mode) && !"STATIC".equals(mode) && !"MANUAL".equals(mode)) {
            return "DISCOVERY";
        }
        return mode;
    }

    private String generateId() {
        for (int i = 1; i <= 9999; i++) {
            String id = "DEV-" + String.format("%03d", i);
            if (!deviceRepository.existsById(id)) return id;
        }
        throw new BizException(500, "生成设备编号失败");
    }

    private String generateManualId(String ip) {
        String base = "MAN-" + compactIdToken(ip, 10);
        if (!deviceRepository.existsById(base)) {
            return base;
        }
        for (int i = 1; i <= 9999; i++) {
            String candidate = base + "-" + i;
            if (!deviceRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new BizException(500, "生成手工设备编号失败");
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

    private Specification<Device> likeIfPresent(String field, String q) {
        return (root, query, cb) ->
                (q == null || q.isEmpty()) ? null : cb.like(root.get(field), "%" + q + "%");
    }

    private Specification<Device> eqIfPresent(String field, String q) {
        return (root, query, cb) ->
                (q == null || q.isEmpty()) ? null : cb.equal(root.get(field), q);
    }

    private DeviceVO enrichSingle(Device device) {
        List<DeviceVO> list = enrichOccupancy(List.of(VoMappers.toDeviceVO(device)));
        return list.isEmpty() ? VoMappers.toDeviceVO(device) : list.get(0);
    }

    private List<DeviceVO> enrichOccupancy(List<DeviceVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return List.of();
        }
        Map<String, DeviceSessionLock> lockMap = deviceLockService.getActiveLocks(
                vos.stream().map(DeviceVO::getId).collect(Collectors.toSet()));

        for (DeviceVO vo : vos) {
            DeviceSessionLock lock = lockMap.get(vo.getId());
            if (lock != null) {
                vo.setOccupied(true);
                vo.setOccupiedSessionId(lock.getSessionId());
                vo.setOccupiedPatientId(lock.getPatientId());
                vo.setOccupiedPatientName(lock.getPatientNameSnapshot());
                vo.setLockExpiresAt(lock.getExpiresAt() == null ? null : lock.getExpiresAt().format(DTMF));
            } else {
                vo.setOccupied(false);
                vo.setOccupiedSessionId(null);
                vo.setOccupiedPatientId(null);
                vo.setOccupiedPatientName(null);
                vo.setLockExpiresAt(null);
            }
        }
        return vos;
    }
}