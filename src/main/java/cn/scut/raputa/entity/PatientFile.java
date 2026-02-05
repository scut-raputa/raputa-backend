package cn.scut.raputa.entity;

import cn.scut.raputa.entity.key.PatientFileId;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_file",
       indexes = {
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
            // 原来是 length = 1024，改成 ≤ 680（utf8mb4 下合规）
            column = @Column(name = "file_path", length = 680, nullable = false)
        )
    })
    private PatientFileId id;

    @Column(name="file_type", length = 16, nullable = false)
    private String fileType; // csv/wav/pdf…

    @Column(name="saved_at", nullable = false)
    private LocalDateTime savedAt;

    // 会话目录名：Pxxxx_姓名_yyyyMMdd_HHmmss
    @Column(name="session_key", length = 128, nullable = false)
    private String sessionKey;

    // —— 便捷访问器（不参与持久化映射）——
    @Transient
    public String getPatientId() {
        return id != null ? id.getPatientId() : null;
    }

    @Transient
    public String getFilePath() {
        return id != null ? id.getFilePath() : null;
    }
}
