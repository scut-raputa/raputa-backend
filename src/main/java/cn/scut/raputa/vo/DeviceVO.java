package cn.scut.raputa.vo;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVO {
    private String id;
    private String name;
    private String ip;
    private String lastConnectedTime; // formatted "yyyy-MM-dd HH:mm:ss", nullable
    private String lastSeenAt;
    private String status;
    private String accessMode;
    private Integer controlPort;
    private String rtspPath;
    private Boolean enabled;
    private String description;
    private String storageLocation;
    private String responsible;
    private Boolean occupied;
    private String occupiedSessionId;
    private String occupiedPatientId;
    private String occupiedPatientName;
    private String lockExpiresAt;
}