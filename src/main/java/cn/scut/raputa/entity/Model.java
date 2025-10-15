package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "model", indexes = {
        @Index(name = "idx_model_func", columnList = "func"),
        @Index(name = "idx_model_name", columnList = "name"),
        @Index(name = "idx_model_uploader", columnList = "uploader"),
        @Index(name = "idx_model_upload_time", columnList = "upload_time")
})
public class Model {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "func", nullable = false, length = 64)
    private String func;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "upload_time", nullable = false)
    private LocalDateTime uploadTime;

    @Column(nullable = false, length = 64)
    private String uploader;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(precision = 6, scale = 4)
    private BigDecimal accuracy;

    @Column(precision = 6, scale = 4)
    private BigDecimal sensitivity;

    @Column(precision = 6, scale = 4)
    private BigDecimal specificity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZONE_CN);
        if (createdAt == null)
            createdAt = now;
        updatedAt = createdAt;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now(ZONE_CN);
    }
}
