package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "gas_data", indexes = {
        @Index(name = "idx_gas_device_id", columnList = "device_id"),
        @Index(name = "idx_gas_timestamp", columnList = "timestamp"),
        @Index(name = "idx_gas_created_at", columnList = "created_at")
})
public class GasData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 20)
    private String deviceId;

    @Column(nullable = false)
    private Long timestamp;

    @Column(nullable = false)
    private Long timestampus;

    @Column
    private Integer flow;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}






