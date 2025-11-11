// cn/scut/raputa/repository/PatientFileRepository.java
package cn.scut.raputa.repository;

import cn.scut.raputa.entity.PatientFile;
import cn.scut.raputa.entity.key.PatientFileId;
import org.springframework.data.jpa.repository.*;

public interface PatientFileRepository extends JpaRepository<PatientFile, PatientFileId>, JpaSpecificationExecutor<PatientFile> {
}
