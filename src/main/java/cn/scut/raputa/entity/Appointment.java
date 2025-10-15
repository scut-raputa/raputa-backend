package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "appointment", indexes = {
        @Index(name = "idx_appointment_name", columnList = "name"),
        @Index(name = "idx_appointment_dept", columnList = "dept"),
        @Index(name = "idx_appointment_appt_time", columnList = "appt_time")
})
public class Appointment {

    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 128)
    private String dept;

    @Column(name = "appt_time", nullable = false)
    private LocalDateTime apptTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
