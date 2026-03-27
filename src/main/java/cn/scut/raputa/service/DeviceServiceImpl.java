package cn.scut.raputa.service;

import cn.scut.raputa.dto.DeviceDTO;
import cn.scut.raputa.entity.Device;
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

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

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
        return pg.map(VoMappers::toDeviceVO);
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
        return VoMappers.toDeviceVO(deviceRepository.save(device));
    }

    @Override
    public DeviceVO update(String id, DeviceDTO dto) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "设备不存在"));
        applyDto(device, dto);
        return VoMappers.toDeviceVO(deviceRepository.save(device));
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
        }
        return VoMappers.toDeviceVO(deviceRepository.save(device));
    }

    private void applyDto(Device device, DeviceDTO dto) {
        device.setName(dto.getName());
        device.setStatus(dto.getStatus() != null ? dto.getStatus() : "离线");
        device.setDescription(dto.getDescription());
        device.setStorageLocation(dto.getStorageLocation());
        device.setResponsible(dto.getResponsible());
        device.setLastConnectedTime(parseDateTime(dto.getLastConnectedTime()));
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); } catch (Exception ignored) {}
        return null;
    }

    private String generateId() {
        for (int i = 1; i <= 9999; i++) {
            String id = "DEV-" + String.format("%03d", i);
            if (!deviceRepository.existsById(id)) return id;
        }
        throw new BizException(500, "生成设备编号失败");
    }

    private Specification<Device> likeIfPresent(String field, String q) {
        return (root, query, cb) ->
                (q == null || q.isEmpty()) ? null : cb.like(root.get(field), "%" + q + "%");
    }

    private Specification<Device> eqIfPresent(String field, String q) {
        return (root, query, cb) ->
                (q == null || q.isEmpty()) ? null : cb.equal(root.get(field), q);
    }
}