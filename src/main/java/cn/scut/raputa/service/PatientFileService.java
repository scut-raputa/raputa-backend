package cn.scut.raputa.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import cn.scut.raputa.entity.PatientFile;
import cn.scut.raputa.vo.PatientFilesOverviewVO;

public interface PatientFileService {

    void record(String patientId, String sessionId, String absolutePath, String fileType, LocalDateTime savedAt);

    default void record(String patientId, String absolutePath, String fileType, LocalDateTime savedAt) {
        record(patientId, null, absolutePath, fileType, savedAt);
    }

    List<PatientFilesOverviewVO> overview(
        LocalDate date,
        List<String> filterPatientIds,
        List<String> fileTypes,
        String fileNameLike
    );

    List<PatientFile> listFiles(
        LocalDate date, List<String> patientIds,
        List<String> fileTypes, String fileNameLike
    );

    List<PatientFile> listByIds(List<String> fileIds);
}
