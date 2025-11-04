package cn.scut.raputa.service;

import cn.scut.raputa.entity.AudioData;
import cn.scut.raputa.entity.GasData;
import cn.scut.raputa.entity.ImuData;
import cn.scut.raputa.repository.AudioDataRepository;
import cn.scut.raputa.repository.GasDataRepository;
import cn.scut.raputa.repository.ImuDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据查询服务
 * 
 * @author RAPUTA Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataQueryService {

    private final ImuDataRepository imuDataRepository;
    private final GasDataRepository gasDataRepository;
    private final AudioDataRepository audioDataRepository;

    /**
     * 获取设备IMU数据
     */
    public List<ImuData> getImuDataByDevice(String deviceId, Long startTime, Long endTime, int limit) {
        try {
            if (startTime != null && endTime != null) {
                return imuDataRepository.findByDeviceIdAndTimestampRange(deviceId, startTime, endTime)
                        .stream()
                        .limit(limit)
                        .toList();
            } else {
                return imuDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId, PageRequest.of(0, limit)).getContent();
            }
        } catch (Exception e) {
            log.error("查询IMU数据失败: deviceId={}", deviceId, e);
            return List.of();
        }
    }

    /**
     * 获取设备气体数据
     */
    public List<GasData> getGasDataByDevice(String deviceId, Long startTime, Long endTime, int limit) {
        try {
            if (startTime != null && endTime != null) {
                return gasDataRepository.findByDeviceIdAndTimestampRange(deviceId, startTime, endTime)
                        .stream()
                        .limit(limit)
                        .toList();
            } else {
                return gasDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId, PageRequest.of(0, limit)).getContent();
            }
        } catch (Exception e) {
            log.error("查询气体数据失败: deviceId={}", deviceId, e);
            return List.of();
        }
    }

    /**
     * 获取设备音频数据
     */
    public List<AudioData> getAudioDataByDevice(String deviceId, Long startTime, Long endTime, int limit) {
        try {
            if (startTime != null && endTime != null) {
                return audioDataRepository.findByDeviceIdAndTimestampRange(deviceId, startTime, endTime)
                        .stream()
                        .limit(limit)
                        .toList();
            } else {
                return audioDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId, PageRequest.of(0, limit)).getContent();
            }
        } catch (Exception e) {
            log.error("查询音频数据失败: deviceId={}", deviceId, e);
            return List.of();
        }
    }

    /**
     * 获取设备数据统计
     */
    public DeviceDataStatsDTO getDeviceDataStats(String deviceId) {
        try {
            long imuCount = imuDataRepository.countByDeviceId(deviceId);
            long gasCount = gasDataRepository.countByDeviceId(deviceId);
            long audioCount = audioDataRepository.countByDeviceId(deviceId);
            
            return new DeviceDataStatsDTO(deviceId, imuCount, gasCount, audioCount);
        } catch (Exception e) {
            log.error("获取设备数据统计失败: deviceId={}", deviceId, e);
            return new DeviceDataStatsDTO(deviceId, 0, 0, 0);
        }
    }

    /**
     * 设备数据统计DTO
     */
    public record DeviceDataStatsDTO(
        String deviceId,
        long imuCount,
        long gasCount,
        long audioCount
    ) {}
}
