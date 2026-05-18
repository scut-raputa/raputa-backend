package cn.scut.raputa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
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
@Table(name = "capture_session", indexes = {
        @Index(name = "idx_capture_session_patient", columnList = "patient_id"),
        @Index(name = "idx_capture_session_device", columnList = "device_id"),
        @Index(name = "idx_capture_session_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptureSession {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 16, nullable = false)
    private String mode;

    @Column(length = 24, nullable = false)
    private String status;

    @Column(name = "patient_id", length = 20)
    private String patientId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "session_key", length = 128)
    private String sessionKey;

    @Column(name = "patient_name_snapshot", length = 128)
    private String patientNameSnapshot;

    @Column(name = "session_dir", length = 255)
    private String sessionDir;

    @Column(name = "inference_service_url", length = 255)
    private String inferenceServiceUrl;

    @Lob
    @Column(name = "model_snapshot_json", columnDefinition = "TEXT")
    private String modelSnapshotJson;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

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
