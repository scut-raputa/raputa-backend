package cn.scut.raputa.repository;

import cn.scut.raputa.entity.PatientFile;
import cn.scut.raputa.entity.key.PatientFileId;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.*;

public interface PatientFileRepository extends JpaRepository<PatientFile, PatientFileId>, JpaSpecificationExecutor<PatientFile> {
    Optional<PatientFile> findTop1ByIdPatientIdOrderBySavedAtDesc(String patientId);

    Optional<PatientFile> findByFileId(String fileId);

    List<PatientFile> findAllByFileIdIn(Collection<String> fileIds);

        @Query("SELECT pf.sessionId, pf.savedAt, COUNT(pf) " +
           "FROM PatientFile pf " +
           "WHERE pf.savedAt BETWEEN :startTime AND :endTime " +
           "AND pf.fileType = 'csv' " +
            "GROUP BY pf.sessionId, pf.savedAt")
    List<Object[]> findDeviceUsageStats(LocalDateTime startTime, LocalDateTime endTime);
}
