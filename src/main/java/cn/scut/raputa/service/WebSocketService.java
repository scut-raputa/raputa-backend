package cn.scut.raputa.service;

import cn.scut.raputa.dto.RealtimeDataMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket推送服务
 * 负责向前端推送实时传感器数据
 * 
 * @author RAPUTA Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 推送IMU数据到指定设备的订阅者
     * 
     * @param deviceId 设备ID
     * @param timestamp 时间戳
     * @param x X轴数据
     * @param y Y轴数据
     * @param z Z轴数据
     */
    public void pushImuData(String deviceId, Long timestamp, Integer x, Integer y, Integer z) {
        try {
            RealtimeDataMessage message = RealtimeDataMessage.createImuMessage(deviceId, timestamp, x, y, z);
            // 推送到 /topic/device/{deviceId}/imu
            messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/imu", message);
        } catch (Exception e) {
            log.error("推送IMU数据失败: deviceId={}", deviceId, e);
        }
    }
    
    /**
     * 推送GAS数据到指定设备的订阅者
     * 
     * @param deviceId 设备ID
     * @param timestamp 时间戳
     * @param flow 流量数据
     */
    public void pushGasData(String deviceId, Long timestamp, Integer flow) {
        try {
            RealtimeDataMessage message = RealtimeDataMessage.createGasMessage(deviceId, timestamp, flow);
            // 推送到 /topic/device/{deviceId}/gas
            messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/gas", message);
        } catch (Exception e) {
            log.error("推送GAS数据失败: deviceId={}", deviceId, e);
        }
    }
    
    /**
     * 推送批量IMU数据(用于降低推送频率,避免前端卡顿)
     * 
     * @param deviceId 设备ID
     * @param dataList IMU数据列表
     */
    public void pushImuDataBatch(String deviceId, java.util.List<RealtimeDataMessage> dataList) {
        try {
            messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/imu/batch", dataList);
        } catch (Exception e) {
            log.error("推送批量IMU数据失败: deviceId={}", deviceId, e);
        }
    }
    
    /**
     * 推送批量GAS数据
     * 
     * @param deviceId 设备ID
     * @param dataList GAS数据列表
     */
    public void pushGasDataBatch(String deviceId, java.util.List<RealtimeDataMessage> dataList) {
        try {
            messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/gas/batch", dataList);
        } catch (Exception e) {
            log.error("推送批量GAS数据失败: deviceId={}", deviceId, e);
        }
    }
}



