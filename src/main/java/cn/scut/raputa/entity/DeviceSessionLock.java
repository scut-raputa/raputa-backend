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

@Entity
@Table(name = "device_session_lock", indexes = {
        @Index(name = "idx_device_lock_expires", columnList = "expires_at"),
        @Index(name = "idx_device_lock_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSessionLock {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "session_id", length = 64, nullable = false)
    private String sessionId;

    @Column(name = "patient_id", length = 20)
    private String patientId;

    @Column(name = "patient_name_snapshot", length = 128)
    private String patientNameSnapshot;

    @Column(name = "holder", length = 64)
    private String holder;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "heartbeat_at")
    private LocalDateTime heartbeatAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZONE_CN);
        if (startedAt == null) {
            startedAt = now;
        }
        if (heartbeatAt == null) {
            heartbeatAt = now;
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
