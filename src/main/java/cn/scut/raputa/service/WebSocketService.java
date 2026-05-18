package cn.scut.raputa.service;

import cn.scut.raputa.dto.RealtimeDataMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushImuData(String deviceId, Long timestamp, Integer x, Integer y, Integer z) {
        try {
            RealtimeDataMessage message = RealtimeDataMessage.createImuMessage(deviceId, timestamp, x, y, z);

            messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/imu", message);
        } catch (Exception e) {
            log.error("推送IMU数据失败: deviceId={}", deviceId, e);
        }
    }

    public void pushGasData(String deviceId, Long timestamp, Integer flow) {
        try {
            RealtimeDataMessage message = RealtimeDataMessage.createGasMessage(deviceId, timestamp, flow);

            messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/gas", message);
        } catch (Exception e) {
            log.error("推送GAS数据失败: deviceId={}", deviceId, e);
        }
    }

    public void pushAudioData(String deviceId, Long timestamp, Float amplitude) {
        try {
            RealtimeDataMessage message = RealtimeDataMessage.createAudioMessage(deviceId, timestamp, amplitude);
            String destination = "/topic/device/" + deviceId + "/audio";
            messagingTemplate.convertAndSend(destination, message);
        } catch (Exception e) {
            log.error("推送AUDIO数据失败: deviceId={}", deviceId, e);
        }
    }

    public void pushPredictionResult(String deviceId, Object result) {
        try {
            String destination = "/topic/device/" + deviceId + "/prediction";
            messagingTemplate.convertAndSend(destination, result);
            log.info("推送预测结果到设备: {}", deviceId);
        } catch (Exception e) {
            log.error("推送预测结果失败: deviceId={}", deviceId, e);
        }
    }

    public void pushDeviceControl(String deviceId, Object command) {
        try {
            String destination = "/topic/device/" + deviceId + "/control";
            messagingTemplate.convertAndSend(destination, command);
            log.info("推送设备控制指令: deviceId={}, destination={}", deviceId, destination);
        } catch (Exception e) {
            log.error("推送设备控制指令失败: deviceId={}", deviceId, e);
        }
    }

    public void pushImuDataBatch(String deviceId, java.util.List<RealtimeDataMessage> dataList) {
        try {
            messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/imu/batch", dataList);
        } catch (Exception e) {
            log.error("推送批量IMU数据失败: deviceId={}", deviceId, e);
        }
    }

    public void pushGasDataBatch(String deviceId, java.util.List<RealtimeDataMessage> dataList) {
        try {
            messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/gas/batch", dataList);
        } catch (Exception e) {
            log.error("推送批量GAS数据失败: deviceId={}", deviceId, e);
        }
    }
}

