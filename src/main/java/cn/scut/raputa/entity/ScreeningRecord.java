package cn.scut.raputa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "screening_record", indexes = {
        @Index(name = "idx_screening_appointment", columnList = "appointment_id"),
        @Index(name = "idx_screening_patient", columnList = "patient_id"),
        @Index(name = "idx_screening_session", columnList = "session_id"),
        @Index(name = "idx_screening_status", columnList = "status"),
        @Index(name = "idx_screening_time", columnList = "check_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningRecord {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 16, nullable = false)
    private String source;

    @Column(length = 24, nullable = false)
    private String status;

    @Column(name = "appointment_id", length = 20)
    private String appointmentId;

    @Column(name = "patient_id", length = 20)
    private String patientId;

    @Column(name = "subject_name", nullable = false, length = 64)
    private String subjectName;

    @Column(name = "subject_gender", length = 2)
    private String subjectGender;

    @Column(name = "subject_age")
    private Integer subjectAge;

    @Column(name = "subject_id_card", length = 18)
    private String subjectIdCard;

    @Column(name = "subject_phone", length = 32)
    private String subjectPhone;

    @Column(name = "subject_dept", length = 128)
    private String subjectDept;

    @Column(name = "check_dept", length = 128)
    private String checkDept;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(length = 16)
    private String mode;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(length = 32, nullable = false)
    private String result;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(length = 64)
    private String staff;

    @Column(name = "total_swallows")
    private Integer totalSwallows;

    @Column(name = "normal_swallows")
    private Integer normalSwallows;

    @Column(name = "dysphagia_swallows")
    private Integer dysphagiaSwallows;

    @Column(name = "aspiration_swallows")
    private Integer aspirationSwallows;

    @Column(name = "check_time", nullable = false)
    private LocalDateTime checkTime;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZONE_CN);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (source == null || source.isBlank()) {
            source = "APPOINTMENT";
        }
        if (status == null || status.isBlank()) {
            status = "COMPLETED";
        }
        if (checkTime == null) {
            checkTime = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now(ZONE_CN);
    }
}
