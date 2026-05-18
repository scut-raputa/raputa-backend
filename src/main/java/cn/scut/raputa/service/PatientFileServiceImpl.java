package cn.scut.raputa.service;

import cn.scut.raputa.entity.Patient;
import cn.scut.raputa.entity.PatientFile;
import cn.scut.raputa.entity.key.PatientFileId;
import cn.scut.raputa.repository.PatientFileRepository;
import cn.scut.raputa.repository.PatientRepository;
import cn.scut.raputa.vo.PatientFilesOverviewVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
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
    private final FileStorageService fileStorageService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void record(String patientId, String sessionId, String absolutePath, String fileType, LocalDateTime savedAt) {
        Path normalizedPath = Path.of(absolutePath).toAbsolutePath().normalize();
        Path storageRoot = fileStorageService.getStorageRootPath();
        PatientFileId key = new PatientFileId(patientId, normalizedPath.toString());

        PatientFile entity = patientFileRepository.findById(key).orElseGet(PatientFile::new);
        entity.setId(key);
        if (entity.getFileId() == null || entity.getFileId().isBlank()) {
            entity.setFileId(UUID.randomUUID().toString().replace("-", ""));
        }
        entity.setSessionId(sessionId);
        entity.setStorageRoot(storageRoot.toString());
        entity.setRelativePath(fileStorageService.toRelativePath(normalizedPath));
        entity.setOriginalName(resolveFileName(normalizedPath, absolutePath));
        entity.setFileType(fileType.toLowerCase());
        entity.setSavedAt(savedAt != null ? savedAt : LocalDateTime.now());
        entity.setSizeBytes(readFileSize(normalizedPath));
        entity.setLegacyAbsolutePath(normalizedPath.toString());

        patientFileRepository.save(entity);

        patientRepository.findById(patientId).ifPresent(patient -> {
            if (!patient.isChecked()) {
                patient.setChecked(true);
                patientRepository.save(patient);
            }
        });
    }

    @Override
    public List<PatientFilesOverviewVO> overview(LocalDate date, List<String> filterPatientIds, List<String> fileTypes, String fileNameLike) {

        List<Patient> patients = patientRepository.findAll();

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
                String keyword = "%" + fileNameLike + "%";
                ps.add(cb.or(
                        cb.like(root.get("originalName"), keyword),
                        cb.like(root.get("relativePath"), keyword),
                    cb.like(root.get("legacyAbsolutePath"), keyword),
                    cb.like(root.get("id").get("filePath"), keyword)
                ));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };

        List<PatientFile> files = patientFileRepository.findAll(spec);
        ensureFileIds(files);

        // patientId -> date -> session group -> files
        Map<String, Map<LocalDate, Map<String, List<PatientFile>>>> grouped =
                files.stream().collect(Collectors.groupingBy(
                        PatientFile::getPatientId,
                        Collectors.groupingBy(
                                pf -> pf.getSavedAt().toLocalDate(),
                    Collectors.groupingBy(this::groupingSessionKey)
                        )
                ));

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

            List<LocalDate> dateKeys = new ArrayList<>(byDate.keySet());

            dateKeys.sort(Comparator.reverseOrder());

            List<PatientFilesOverviewVO.DateGroup> dates = new ArrayList<>();
            for (LocalDate dKey : dateKeys) {
                Map<String, List<PatientFile>> timesMap = byDate.getOrDefault(dKey, Collections.emptyMap());

                List<String> timeKeys = new ArrayList<>(timesMap.keySet());
                Collections.sort(timeKeys);

                List<PatientFilesOverviewVO.TimeGroup> times = new ArrayList<>();
                for (String tKey : timeKeys) {
                    List<PatientFile> fileList = timesMap.getOrDefault(tKey, Collections.emptyList());
                    fileList = fileList.stream()
                        .sorted(Comparator.comparing(PatientFile::getFileType)
                            .thenComparing(PatientFile::getSavedAt))
                        .toList();

                    List<PatientFilesOverviewVO.FileItem> filesVo = fileList.stream()
                            .map(pf -> new PatientFilesOverviewVO.FileItem(
                            pf.getFileId(),
                            resolveDisplayName(pf),
                            pf.getFileType()
                            )).toList();

                    PatientFilesOverviewVO.TimeGroup tg = new PatientFilesOverviewVO.TimeGroup();
                    tg.setTime(resolveGroupTime(fileList));
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
                ps.add(root.get("id").get("patientId").in(patientIds));
            }
            if (fileTypes != null && !fileTypes.isEmpty()) {
                ps.add(root.get("fileType").in(fileTypes.stream().map(String::toLowerCase).toList()));
            }
            if (fileNameLike != null && !fileNameLike.isBlank()) {
                String keyword = "%" + fileNameLike + "%";
                ps.add(cb.or(
                        cb.like(root.get("originalName"), keyword),
                        cb.like(root.get("relativePath"), keyword),
                        cb.like(root.get("legacyAbsolutePath"), keyword),
                        cb.like(root.get("id").get("filePath"), keyword)
                ));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
        return patientFileRepository.findAll(spec);
    }

    @Override
    public List<PatientFile> listByIds(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return patientFileRepository.findAllByFileIdIn(fileIds);
    }

    private String groupingSessionKey(PatientFile file) {
        if (file.getSessionId() != null && !file.getSessionId().isBlank()) {
            return file.getSessionId();
        }
        return "legacy-" + file.getSavedAt().withNano(0);
    }

    private void ensureFileIds(List<PatientFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<PatientFile> changed = new ArrayList<>();
        for (PatientFile file : files) {
            if (file.getFileId() == null || file.getFileId().isBlank()) {
                file.setFileId(UUID.randomUUID().toString().replace("-", ""));
                changed.add(file);
            }
        }
        if (!changed.isEmpty()) {
            patientFileRepository.saveAll(changed);
        }
    }

    private String resolveGroupTime(List<PatientFile> files) {
        if (files == null || files.isEmpty()) {
            return "00:00:00";
        }
        LocalDateTime min = files.stream()
                .map(PatientFile::getSavedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(files.get(0).getSavedAt());
        return min == null ? "00:00:00" : min.toLocalTime().format(TIME_FMT);
    }

    private String resolveDisplayName(PatientFile file) {
        if (file.getOriginalName() != null && !file.getOriginalName().isBlank()) {
            return file.getOriginalName();
        }
        if (file.getRelativePath() != null && !file.getRelativePath().isBlank()) {
            Path p = Path.of(file.getRelativePath());
            Path name = p.getFileName();
            if (name != null) {
                return name.toString();
            }
        }
        if (file.getLegacyAbsolutePath() != null && !file.getLegacyAbsolutePath().isBlank()) {
            Path p = Path.of(file.getLegacyAbsolutePath());
            Path name = p.getFileName();
            if (name != null) {
                return name.toString();
            }
            return file.getLegacyAbsolutePath();
        }
        return "unknown";
    }

    private String resolveFileName(Path path, String fallback) {
        try {
            Path name = path.getFileName();
            if (name != null) {
                return name.toString();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private Long readFileSize(Path path) {
        try {
            if (Files.exists(path)) {
                return Files.size(path);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
