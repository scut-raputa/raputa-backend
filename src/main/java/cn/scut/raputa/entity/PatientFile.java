package cn.scut.raputa.entity;

import cn.scut.raputa.entity.key.PatientFileId;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_file",
       indexes = {
         @Index(name="idx_pf_session", columnList = "session_id"),
         @Index(name="idx_pf_patient", columnList = "patient_id"),
         @Index(name="idx_pf_saved_at", columnList = "saved_at"),
         @Index(name="idx_pf_type", columnList = "file_type")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class PatientFile {

  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(
      name = "patientId",
      column = @Column(name = "patient_id", length = 20, nullable = false)
    ),
    @AttributeOverride(
      name = "filePath",
      column = @Column(name = "file_path", length = 680, nullable = false)
    )
  })
  private PatientFileId id;

  @Column(name = "file_id", length = 64, unique = true)
  private String fileId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "storage_root", length = 255)
    private String storageRoot;

    @Column(name = "relative_path", length = 255)
    private String relativePath;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name="file_type", length = 16, nullable = false)
    private String fileType; // csv/wav/pdf…

    @Column(name="saved_at", nullable = false)
    private LocalDateTime savedAt;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "legacy_absolute_path", length = 680)
    private String legacyAbsolutePath;

    @Transient
    public String getPatientId() {
      return id != null ? id.getPatientId() : null;
    }

    @Transient
    public String getFilePath() {
      return id != null ? id.getFilePath() : null;
    }
}
