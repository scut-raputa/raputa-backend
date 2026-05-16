package cn.scut.raputa.dto;

import lombok.Data;

@Data
public class DeviceDTO {
    private String name;
    private String ip;
    private String hardwareId;       // system-maintained stable identity: MAC / serial / device ID
    private String lastConnectedTime; // ISO "yyyy-MM-ddTHH:mm:ss" or "yyyy-MM-dd HH:mm:ss", nullable
    private String status;            // "在线" or "离线"
    private String accessMode;        // DISCOVERY / STATIC / MANUAL
    private Integer controlPort;
    private String rtspPath;
    private Boolean enabled;
    private String description;
    private String storageLocation;
    private String responsible;
}
