package cn.scut.raputa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceOccupationDTO {
    private String deviceId;
    private boolean occupied;
    private String patientId;
    private String patientName;
    private LocalDateTime startedAt;
    private String reason;
}
