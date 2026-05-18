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
        @Index(name = "idx_patient_admit", columnList = "admit"),
        @Index(name = "idx_patient_id_card", columnList = "id_card"),
        @Index(name = "idx_patient_outpatient_id", columnList = "outpatient_id"),
        @Index(name = "idx_patient_bed", columnList = "bed_number")
})
public class Patient {

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "outpatient_id", nullable = false, length = 32, unique = true)
    private String outpatientId;

    @Column(name = "id_card", length = 18, unique = true)
    private String idCard;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 2)
    private String gender;

    @Column(nullable = false)
    private LocalDate admit;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(length = 128)
    private String dept;

    @Column(length = 255)
    private String address;

    @Column(name = "past_history", length = 2048)
    private String pastHistory;

    @Column(name = "bed_number", length = 32)
    private String bedNumber;

    @Column(length = 2048)
    private String course;

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
