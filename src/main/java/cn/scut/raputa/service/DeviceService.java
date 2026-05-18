package cn.scut.raputa.service;

import cn.scut.raputa.dto.DeviceDTO;
import cn.scut.raputa.vo.DeviceVO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface DeviceService {
    Page<DeviceVO> page(int page, int size, String id, String name,
                        String status, String storageLocation);

    List<String> distinctLocations();

    DeviceVO update(String id, DeviceDTO dto);

    List<DeviceVO> registry(Boolean onlineOnly);

    boolean forceRelease(String id, String requesterLabel);

    DeviceVO upsertManualDevice(String ip, String deviceName);
}
