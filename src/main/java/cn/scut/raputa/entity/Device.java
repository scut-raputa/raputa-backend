package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@Setter
@Table(name = "device", indexes = {
        @Index(name = "idx_device_name", columnList = "name"),
        @Index(name = "idx_device_status", columnList = "status"),
        @Index(name = "idx_device_hardware_id", columnList = "hardware_id"),
        @Index(name = "idx_device_enabled", columnList = "enabled"),
        @Index(name = "idx_device_access_mode", columnList = "access_mode"),
        @Index(name = "idx_device_storage_location", columnList = "storage_location")
})
public class Device {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String ip;

    @Column(name = "hardware_id", length = 128)
    private String hardwareId;

    @Column(name = "last_connected_time")
    private LocalDateTime lastConnectedTime;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "access_mode", length = 16)
    private String accessMode; // DISCOVERY / STATIC / MANUAL

    @Column(name = "control_port")
    private Integer controlPort;

    @Column(name = "rtsp_path", length = 128)
    private String rtspPath;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 500)
    private String description;

    @Column(name = "storage_location", length = 128)
    private String storageLocation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZONE_CN);
        if (enabled == null) enabled = true;
        if (accessMode == null || accessMode.isBlank()) accessMode = "DISCOVERY";
        if (controlPort == null || controlPort <= 0) controlPort = 6667;
        if (rtspPath == null || rtspPath.isBlank()) rtspPath = "/stream/audio";
        if (createdAt == null) createdAt = now;
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now(ZONE_CN);
    }
}
