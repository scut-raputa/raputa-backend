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
        @Index(name = "idx_device_responsible", columnList = "responsible"),
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

    @Column(name = "last_connected_time")
    private LocalDateTime lastConnectedTime;

    /** "在线" or "离线" */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 500)
    private String description;

    @Column(name = "storage_location", length = 128)
    private String storageLocation;

    @Column(length = 64)
    private String responsible;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZONE_CN);
        if (createdAt == null) createdAt = now;
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now(ZONE_CN);
    }
}