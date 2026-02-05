package cn.scut.raputa.service;

import cn.scut.raputa.dto.*;
import cn.scut.raputa.entity.AudioData;
import cn.scut.raputa.entity.GasData;
import cn.scut.raputa.entity.ImuData;
import cn.scut.raputa.repository.AudioDataRepository;
import cn.scut.raputa.repository.GasDataRepository;
import cn.scut.raputa.repository.ImuDataRepository;
import cn.scut.raputa.utils.SocketTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 数据传输服务
 * 
 * @author RAPUTA Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataTransferService {

    private final ImuDataRepository imuDataRepository;
    private final GasDataRepository gasDataRepository;
    private final AudioDataRepository audioDataRepository;

    /**
     * 发送数据传输控制命令
     */
    public CompletableFuture<Boolean> sendControlCommand(DataTransferControlDTO request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Socket socket = new Socket(request.getDeviceIp(), 6667);
                socket.setSoTimeout(15000);
                
                // 构建控制命令
                String command = request.getEnable() ? "true" : "false";
                byte[] commandData = SocketTools.packSFream(command);
                
                socket.getOutputStream().write(commandData);
                socket.getOutputStream().flush();
                
                log.info("发送控制命令到设备 {}: {}", request.getDeviceIp(), command);
                
                socket.close();
                return true;
                
            } catch (IOException e) {
                log.error("发送控制命令失败", e);
                return false;
            }
        });
    }

    /**
     * 保存IMU数据
     */
    @Transactional
    public ImuData saveImuData(ImuDataDTO request, String deviceId) {
        try {
            ImuData imuData = new ImuData();
            imuData.setDeviceId(deviceId);
            imuData.setTimestamp(request.getTimestamp());
            imuData.setTimestampus(request.getTimestampus());
            imuData.setX(request.getAcc().getX());
            imuData.setY(request.getAcc().getY());
            imuData.setZ(request.getAcc().getZ());
            log.info("imu数据: x={}, y={}, z={}", request.getAcc().getX(), request.getAcc().getY(), request.getAcc().getZ());
            
            ImuData saved = imuDataRepository.save(imuData);
            log.debug("保存IMU数据: deviceId={}, timestamp={}", deviceId, request.getTimestamp());
            return saved;
            
        } catch (Exception e) {
            log.error("保存IMU数据失败", e);
            throw new RuntimeException("保存IMU数据失败: " + e.getMessage());
        }
    }

    /**
     * 批量保存IMU数据
     */
    @Transactional
    public List<ImuData> saveImuDataBatch(List<ImuDataDTO> requests, String deviceId) {
        try {
            List<ImuData> imuDataList = requests.stream()
                .map(request -> {
                    ImuData imuData = new ImuData();
                    imuData.setDeviceId(deviceId);
                    imuData.setTimestamp(request.getTimestamp());
                    imuData.setTimestampus(request.getTimestampus());
                    imuData.setX(request.getAcc().getX());
                    imuData.setY(request.getAcc().getY());
                    imuData.setZ(request.getAcc().getZ());
                    return imuData;
                })
                .toList();
            
            List<ImuData> saved = imuDataRepository.saveAll(imuDataList);
            log.info("批量保存IMU数据: deviceId={}, count={}", deviceId, saved.size());
            return saved;
            
        } catch (Exception e) {
            log.error("批量保存IMU数据失败", e);
            throw new RuntimeException("批量保存IMU数据失败: " + e.getMessage());
        }
    }

//    /**
//     * 保存气体数据
//     */
//    @Transactional
//    public GasData saveGasData(GasDataDTO request, String deviceId) {
//        try {
//            GasData gasData = new GasData();
//            gasData.setDeviceId(deviceId);
//            gasData.setTimestamp(request.getTimestamp());
//            gasData.setTimestampus(request.getTimestampus());
//            gasData.setFlow(request.getFlow());
//
//            GasData saved = gasDataRepository.save(gasData);
//            log.debug("保存气体数据: deviceId={}, timestamp={}", deviceId, request.getTimestamp());
//            return saved;
//
//        } catch (Exception e) {
//            log.error("保存气体数据失败", e);
//            throw new RuntimeException("保存气体数据失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 批量保存气体数据
//     */
//    @Transactional
//    public List<GasData> saveGasDataBatch(List<GasDataDTO> requests, String deviceId) {
//        try {
//            List<GasData> gasDataList = requests.stream()
//                .map(request -> {
//                    GasData gasData = new GasData();
//                    gasData.setDeviceId(deviceId);
//                    gasData.setTimestamp(request.getTimestamp());
//                    gasData.setTimestampus(request.getTimestampus());
//                    gasData.setFlow(request.getFlow());
//                    return gasData;
//                })
//                .toList();
//
//            List<GasData> saved = gasDataRepository.saveAll(gasDataList);
//            log.info("批量保存气体数据: deviceId={}, count={}", deviceId, saved.size());
//            return saved;
//
//        } catch (Exception e) {
//            log.error("批量保存气体数据失败", e);
//            throw new RuntimeException("批量保存气体数据失败: " + e.getMessage());
//        }
//    }

    /**
     * 保存音频数据
     */
    @Transactional
    public AudioData saveAudioData(AudioDataDTO request) {
        try {
            AudioData audioData = new AudioData();
            audioData.setDeviceId(request.getDeviceIp());
            audioData.setTimestamp(request.getTimestamp());
            audioData.setSampleRate(request.getSampleRate());
            audioData.setChannels(request.getChannels());
            audioData.setAudioData(request.getAudioData());
            
            AudioData saved = audioDataRepository.save(audioData);
            log.debug("保存音频数据: deviceId={}, timestamp={}", request.getDeviceIp(), request.getTimestamp());
            return saved;
            
        } catch (Exception e) {
            log.error("保存音频数据失败", e);
            throw new RuntimeException("保存音频数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备IMU数据
     */
    public List<ImuData> getImuDataByDevice(String deviceId, Long startTime, Long endTime) {
        if (startTime != null && endTime != null) {
            return imuDataRepository.findByDeviceIdAndTimestampRange(deviceId, startTime, endTime);
        } else {
            return imuDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId, 
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
        }
    }

    /**
     * 获取设备气体数据
     */
    public List<GasData> getGasDataByDevice(String deviceId, Long startTime, Long endTime) {
        if (startTime != null && endTime != null) {
            return gasDataRepository.findByDeviceIdAndTimestampRange(deviceId, startTime, endTime);
        } else {
            return gasDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId, 
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
        }
    }

    /**
     * 获取设备音频数据
     */
    public List<AudioData> getAudioDataByDevice(String deviceId, Long startTime, Long endTime) {
        if (startTime != null && endTime != null) {
            return audioDataRepository.findByDeviceIdAndTimestampRange(deviceId, startTime, endTime);
        } else {
            return audioDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId, 
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
        }
    }

    /**
     * 删除设备数据
     */
    @Transactional
    public void deleteDeviceData(String deviceId, String dataType) {
        try {
            switch (dataType.toUpperCase()) {
                case "IMU":
                    imuDataRepository.deleteByDeviceId(deviceId);
                    log.info("删除设备IMU数据: {}", deviceId);
                    break;
                case "GAS":
                    gasDataRepository.deleteByDeviceId(deviceId);
                    log.info("删除设备气体数据: {}", deviceId);
                    break;
                case "AUDIO":
                    audioDataRepository.deleteByDeviceId(deviceId);
                    log.info("删除设备音频数据: {}", deviceId);
                    break;
                case "ALL":
                    imuDataRepository.deleteByDeviceId(deviceId);
                    gasDataRepository.deleteByDeviceId(deviceId);
                    audioDataRepository.deleteByDeviceId(deviceId);
                    log.info("删除设备所有数据: {}", deviceId);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的数据类型: " + dataType);
            }
        } catch (Exception e) {
            log.error("删除设备数据失败", e);
            throw new RuntimeException("删除设备数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备数据统计
     */
    public DeviceDataStatsDTO getDeviceDataStats(String deviceId) {
        long imuCount = imuDataRepository.countByDeviceId(deviceId);
        long gasCount = gasDataRepository.countByDeviceId(deviceId);
        long audioCount = audioDataRepository.countByDeviceId(deviceId);
        
        return new DeviceDataStatsDTO(deviceId, imuCount, gasCount, audioCount);
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






