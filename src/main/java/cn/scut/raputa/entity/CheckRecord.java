package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

import cn.scut.raputa.enums.CheckResult;

@Entity
@Getter
@Setter
@Table(name = "check_record", indexes = {
        @Index(name = "idx_check_patient_id", columnList = "patient_id"),
        @Index(name = "idx_check_name", columnList = "name"),
        @Index(name = "idx_check_staff", columnList = "staff"),
        @Index(name = "idx_check_result", columnList = "result"),
        @Index(name = "idx_check_time", columnList = "check_time")
})
public class CheckRecord {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rid;

    @Column(name = "patient_id", nullable = false, length = 20)
    private String patientId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 64)
    private String staff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckResult result;

    @Column(name = "check_time", nullable = false)
    private LocalDateTime checkTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZONE_CN);
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now(ZONE_CN);
    }
}
