package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "device", indexes = {
        @Index(name = "idx_device_name", columnList = "name"),
        @Index(name = "idx_device_ip", columnList = "ip"),
        @Index(name = "idx_device_status", columnList = "status")
})
public class Device {

    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String ip;

    @Column(length = 8)
    private Integer port;

    @Column(nullable = false, length = 32)
    private String status = "OFFLINE";

    @Column(length = 128)
    private String location;

    @Column(length = 255)
    private String description;

    @Column(name = "device_type", length = 64)
    private String deviceType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.lastUsedAt = this.createdAt;
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
