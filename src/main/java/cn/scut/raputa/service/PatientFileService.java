// cn/scut/raputa/service/PatientFileService.java
package cn.scut.raputa.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import cn.scut.raputa.entity.PatientFile;
import cn.scut.raputa.vo.PatientFilesOverviewVO;

public interface PatientFileService {
    // 写入一条记录
    void record(String patientId, String absolutePath, String fileType, LocalDateTime savedAt);

    // 概览（把所有患者都返回，哪怕没有记录）
    List<PatientFilesOverviewVO> overview(
        LocalDate date,                 // 可空；如果有，只取当天
        List<String> filterPatientIds,  // 可空
        List<String> fileTypes,         // 可空
        String fileNameLike             // 可空（模糊匹配）
    );

    List<PatientFile> listFiles(
        LocalDate date, List<String> patientIds,
        List<String> fileTypes, String fileNameLike
    );
}
