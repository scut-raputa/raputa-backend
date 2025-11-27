package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "patient", indexes = {
        @Index(name = "idx_patient_name", columnList = "name"),
        @Index(name = "idx_patient_dept", columnList = "dept"),
        @Index(name = "idx_patient_admit", columnList = "admit")
})
public class Patient {

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "outpatient_id", nullable = false, length = 32, unique = true)
    private String outpatientId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 2)
    private String gender;

    @Column(nullable = false)
    private LocalDate birth;

    @Column(nullable = false)
    private LocalDate admit;

    @Column(length = 128)
    private String dept;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private boolean checked;

    private LocalDateTime createdAt;
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
