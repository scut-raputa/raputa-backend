package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "audio_data", indexes = {
        @Index(name = "idx_audio_device_id", columnList = "device_id"),
        @Index(name = "idx_audio_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audio_created_at", columnList = "created_at")
})
public class AudioData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 20)
    private String deviceId;

    @Column(nullable = false)
    private Long timestamp;

    @Column(name = "sample_rate", nullable = false)
    private Integer sampleRate;

    @Column(nullable = false)
    private Integer channels;

    @Column(name = "audio_data", columnDefinition = "LONGTEXT")
    private String audioData;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}






