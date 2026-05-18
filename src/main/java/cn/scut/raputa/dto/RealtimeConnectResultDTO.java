package cn.scut.raputa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeConnectResultDTO {
    private boolean success;
    private boolean occupied;
    private String deviceId;
    private String sessionId;
    private DeviceOccupationDTO occupation;
    private String reason;

    public static RealtimeConnectResultDTO success(String deviceId, String sessionId) {
        RealtimeConnectResultDTO dto = new RealtimeConnectResultDTO();
        dto.setSuccess(true);
        dto.setOccupied(false);
        dto.setDeviceId(deviceId);
        dto.setSessionId(sessionId);
        return dto;
    }

    public static RealtimeConnectResultDTO occupied(
            String deviceId,
            DeviceOccupationDTO occupation,
            String reason) {
        RealtimeConnectResultDTO dto = new RealtimeConnectResultDTO();
        dto.setSuccess(false);
        dto.setOccupied(true);
        dto.setDeviceId(deviceId);
        dto.setOccupation(occupation);
        dto.setReason(reason);
        return dto;
    }

    public static RealtimeConnectResultDTO failed(String deviceId, String reason) {
        RealtimeConnectResultDTO dto = new RealtimeConnectResultDTO();
        dto.setSuccess(false);
        dto.setOccupied(false);
        dto.setDeviceId(deviceId);
        dto.setReason(reason);
        return dto;
    }
}
