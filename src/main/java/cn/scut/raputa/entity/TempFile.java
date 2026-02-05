package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Getter
@Setter
@Table(name = "temp_file", indexes = {
        @Index(name = "idx_temp_created_at", columnList = "created_at"),
        @Index(name = "idx_temp_expire_at", columnList = "expire_at")
})
public class TempFile {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(name = "content_type", length = 64)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column(name = "sample_rate")
    private Integer sampleRate;

    @Column(name = "imu_x", length = 64)
    private String imuX;

    @Column(name = "imu_y", length = 64)
    private String imuY;

    @Column(name = "imu_z", length = 64)
    private String imuZ;

    @Column(name = "gas_col", length = 64)
    private String gasCol;

    @Column(name = "audio_col", length = 64)
    private String audioCol;

    @Column(name = "mapping_set", nullable = false)
    private Boolean mappingSet = false;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZONE_CN);
        if (createdAt == null)
            createdAt = now;
        if (expireAt == null)
            expireAt = createdAt.plusHours(6);
        updatedAt = createdAt;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now(ZONE_CN);
    }
}
