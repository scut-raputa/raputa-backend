package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@Setter
@Table(name = "doctor", indexes = {
        @Index(name = "idx_doctor_name", columnList = "name"),
        @Index(name = "idx_doctor_department", columnList = "department"),
        @Index(name = "idx_doctor_title", columnList = "title"),
        @Index(name = "idx_doctor_phone", columnList = "phone")
})
public class Doctor {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    /** 工号，如 D001 */
    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 64)
    private String department;

    /** 主任医师 / 副主任医师 / 主治医师 / 住院医师 */
    @Column(length = 32)
    private String title;

    @Column(length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZONE_CN);
        if (createdAt == null) createdAt = now;
        updatedAt = createdAt;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now(ZONE_CN);
    }
}
