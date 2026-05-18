package cn.scut.raputa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeDataMessage {

    private String deviceId;

    private String dataType;

    private Long timestamp;

    private Integer x;

    private Integer y;

    private Integer z;

    private Integer flow;

    private Float amplitude;

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

    public static RealtimeDataMessage createGasMessage(String deviceId, Long timestamp, Integer flow) {
        RealtimeDataMessage message = new RealtimeDataMessage();
        message.setDeviceId(deviceId);
        message.setDataType("gas");
        message.setTimestamp(timestamp);
        message.setFlow(flow);
        return message;
    }

    public static RealtimeDataMessage createAudioMessage(String deviceId, Long timestamp, Float amplitude) {
        RealtimeDataMessage message = new RealtimeDataMessage();
        message.setDeviceId(deviceId);
        message.setDataType("audio");
        message.setTimestamp(timestamp);
        message.setAmplitude(amplitude);
        return message;
    }
}

