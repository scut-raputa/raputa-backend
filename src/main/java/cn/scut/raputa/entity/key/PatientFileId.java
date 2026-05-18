package cn.scut.raputa.entity.key;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class PatientFileId implements Serializable {
    private String patientId;
    private String filePath;
}
