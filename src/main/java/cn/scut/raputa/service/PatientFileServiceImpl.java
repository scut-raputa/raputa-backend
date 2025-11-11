// cn/scut/raputa/service/impl/PatientFileServiceImpl.java
package cn.scut.raputa.service;

import cn.scut.raputa.entity.Patient;
import cn.scut.raputa.entity.PatientFile;
import cn.scut.raputa.entity.key.PatientFileId;
import cn.scut.raputa.repository.PatientFileRepository;
import cn.scut.raputa.repository.PatientRepository;
import cn.scut.raputa.service.PatientFileService;
import cn.scut.raputa.vo.PatientFilesOverviewVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientFileServiceImpl implements PatientFileService {

    private final PatientRepository patientRepository;
    private final PatientFileRepository patientFileRepository;

    private static final DateTimeFormatter SESSION_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public void record(String patientId, String absolutePath, String fileType, LocalDateTime savedAt) {
        // 解析会话目录名作为 sessionKey（…/P0001_张三_20251109_094129/imu.csv）
        String sessionKey = extractSessionKey(absolutePath);
        PatientFile entity = new PatientFile();

        PatientFileId id = new PatientFileId(patientId, absolutePath);
        entity.setId(id);
        entity.setFileType(fileType.toLowerCase());
        entity.setSavedAt(savedAt != null ? savedAt : LocalDateTime.now());
        entity.setSessionKey(sessionKey);

        patientFileRepository.save(entity);
    }

    private String extractSessionKey(String absolutePath) {
        try {
            Path p = Path.of(absolutePath).normalize();
            String folder = p.getParent().getFileName().toString(); // 例：P0001_张三_20251109_094129
            return folder;
        } catch (Exception e) {
            return "unknown_session";
        }
    }

    @Override
    public List<PatientFilesOverviewVO> overview(LocalDate date, List<String> filterPatientIds, List<String> fileTypes, String fileNameLike) {
        // 1) 所有患者（用于“无记录也返回”）
        List<Patient> patients = patientRepository.findAll();

        // 建立 patientId -> name
        Map<String, String> idName = patients.stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getName));

        // 2) 构建筛选
        Specification<PatientFile> spec = (root, q, cb) -> {
            var ps = new java.util.ArrayList<Predicate>();

            if (date != null) {
                LocalDateTime start = date.atStartOfDay();
                LocalDateTime end = start.plusDays(1);
                ps.add(cb.between(root.get("savedAt"), start, end));
            }
            if (filterPatientIds != null && !filterPatientIds.isEmpty()) {
                ps.add(root.get("id").get("patientId").in(filterPatientIds));
            }
            if (fileTypes != null && !fileTypes.isEmpty()) {
                ps.add(root.get("fileType").in(fileTypes.stream().map(String::toLowerCase).toList()));
            }
            if (fileNameLike != null && !fileNameLike.isBlank()) {
                ps.add(cb.like(root.get("id").get("filePath"), "%" + fileNameLike + "%"));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };


        // 3) 拉取匹配的文件
        List<PatientFile> files = patientFileRepository.findAll(spec);

        // 4) patientId -> (date -> (timeHHmmss -> files))
        Map<String, Map<LocalDate, Map<String, List<PatientFile>>>> grouped =
                files.stream().collect(Collectors.groupingBy(
                        PatientFile::getPatientId,
                        Collectors.groupingBy(
                                pf -> pf.getSavedAt().toLocalDate(),
                                Collectors.groupingBy(pf -> sessionTimeFromSessionKey(pf.getSessionKey()) // "09:41:29"
                                )
                        )
                ));

        // 5) 组装 VO（所有患者都要返回）
        List<PatientFilesOverviewVO> out = new ArrayList<>();
        for (Patient p : patients) {
            if (filterPatientIds != null && !filterPatientIds.isEmpty()
                    && !filterPatientIds.contains(p.getId())) {
                continue;
            }

            Map<LocalDate, Map<String, List<PatientFile>>> byDate =
                    grouped.getOrDefault(p.getId(), Collections.emptyMap());

            PatientFilesOverviewVO vo = new PatientFilesOverviewVO();
            vo.setId(p.getId());
            vo.setName(p.getName());

            // === 用 keySet + 排序，避免比较器上的通配符陷阱 ===
            List<LocalDate> dateKeys = new ArrayList<>(byDate.keySet());
            // 日期倒序（最近在前）
            dateKeys.sort(Comparator.reverseOrder());

            List<PatientFilesOverviewVO.DateGroup> dates = new ArrayList<>();
            for (LocalDate dKey : dateKeys) {
                Map<String, List<PatientFile>> timesMap = byDate.getOrDefault(dKey, Collections.emptyMap());

                // 时间（HH:mm:ss）升序
                List<String> timeKeys = new ArrayList<>(timesMap.keySet());
                Collections.sort(timeKeys);

                List<PatientFilesOverviewVO.TimeGroup> times = new ArrayList<>();
                for (String tKey : timeKeys) {
                    List<PatientFile> fileList = timesMap.getOrDefault(tKey, Collections.emptyList());
                    fileList.sort(Comparator.comparing(PatientFile::getFileType));

                    List<PatientFilesOverviewVO.FileItem> filesVo = fileList.stream()
                            .map(pf -> new PatientFilesOverviewVO.FileItem(
                                    java.nio.file.Path.of(pf.getFilePath()).getFileName().toString(),
                                    pf.getFileType(),
                                    pf.getFilePath()
                            )).toList();

                    PatientFilesOverviewVO.TimeGroup tg = new PatientFilesOverviewVO.TimeGroup();
                    tg.setTime(tKey);
                    tg.setFiles(filesVo);
                    times.add(tg);
                }

                PatientFilesOverviewVO.DateGroup dg = new PatientFilesOverviewVO.DateGroup();
                dg.setDate(dKey.toString()); // yyyy-MM-dd
                dg.setSlots(times);
                dates.add(dg);
            }

            vo.setDates(dates);
            out.add(vo);
        }

        // 最终整体排序：patientId 倒序（也可以按 admit 或 name，看你需求）
        out.sort(Comparator.comparing(PatientFilesOverviewVO::getId).reversed());
        return out;
    }

    @Override
    public List<PatientFile> listFiles(LocalDate date, List<String> patientIds,
                                    List<String> fileTypes, String fileNameLike) {
        Specification<PatientFile> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            if (date != null) {
                LocalDateTime start = date.atStartOfDay();
                LocalDateTime end = start.plusDays(1);
                ps.add(cb.between(root.get("savedAt"), start, end));
            }
            if (patientIds != null && !patientIds.isEmpty()) {
                // ★ 关键：访问 EmbeddedId 要用 id.patientId
                ps.add(root.get("id").get("patientId").in(patientIds));
            }
            if (fileTypes != null && !fileTypes.isEmpty()) {
                ps.add(root.get("fileType").in(fileTypes.stream().map(String::toLowerCase).toList()));
            }
            if (fileNameLike != null && !fileNameLike.isBlank()) {
                // ★ 关键：访问 EmbeddedId 要用 id.filePath
                ps.add(cb.like(root.get("id").get("filePath"), "%" + fileNameLike + "%"));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
        return patientFileRepository.findAll(spec);
    }

    // 从 sessionKey（如 P0001_张三_20251109_094129）抽 HH:mm:ss
    private String sessionTimeFromSessionKey(String sessionKey) {
        if (sessionKey == null) return "00:00:00";
        String[] parts = sessionKey.split("_");
        if (parts.length < 3) return "00:00:00";
        String ts = parts[parts.length - 1]; // 094129 或 20251109_094129
        String hhmmss;
        if (ts.length() == 6) {
            hhmmss = ts;
        } else if (ts.length() == "yyyyMMdd_HHmmss".length() && ts.contains("_")) {
            hhmmss = ts.substring(ts.indexOf('_') + 1);
        } else {
            // 兜底：尝试从末尾 6 位取
            hhmmss = ts.substring(Math.max(0, ts.length() - 6));
        }
        return hhmmss.substring(0,2) + ":" + hhmmss.substring(2,4) + ":" + hhmmss.substring(4,6);
    }
}
