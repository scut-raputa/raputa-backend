package cn.scut.raputa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实时数据推送消息
 * 用于WebSocket推送传感器数据到前端
 * 
 * @author RAPUTA Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeDataMessage {
    
    /**
     * 设备ID
     */
    private String deviceId;
    
    /**
     * 数据类型: imu, gas, audio
     */
    private String dataType;
    
    /**
     * 时间戳(毫秒)
     */
    private Long timestamp;
    
    /**
     * IMU数据 - X轴
     */
    private Integer x;
    
    /**
     * IMU数据 - Y轴
     */
    private Integer y;
    
    /**
     * IMU数据 - Z轴
     */
    private Integer z;
    
    /**
     * GAS数据 - 流量
     */
    private Integer flow;
    
    /**
     * AUDIO数据 - 音频幅值(降采样后)
     */
    private Float amplitude;
    
    /**
     * 创建IMU数据消息
     */
    public static RealtimeDataMessage createImuMessage(String deviceId, Long timestamp, Integer x, Integer y, Integer z) {
        RealtimeDataMessage message = new RealtimeDataMessage();
        message.setDeviceId(deviceId);
        message.setDataType("imu");
        message.setTimestamp(timestamp);
        message.setX(x);
        message.setY(y);
        message.setZ(z);
        return message;
    }
    
    /**
     * 创建GAS数据消息
     */
    public static RealtimeDataMessage createGasMessage(String deviceId, Long timestamp, Integer flow) {
        RealtimeDataMessage message = new RealtimeDataMessage();
        message.setDeviceId(deviceId);
        message.setDataType("gas");
        message.setTimestamp(timestamp);
        message.setFlow(flow);
        return message;
    }
    
    /**
     * 创建AUDIO数据消息
     */
    public static RealtimeDataMessage createAudioMessage(String deviceId, Long timestamp, Float amplitude) {
        RealtimeDataMessage message = new RealtimeDataMessage();
        message.setDeviceId(deviceId);
        message.setDataType("audio");
        message.setTimestamp(timestamp);
        message.setAmplitude(amplitude);
        return message;
    }
}






