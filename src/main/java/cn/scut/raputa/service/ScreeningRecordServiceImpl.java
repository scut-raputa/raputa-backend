package cn.scut.raputa.service;

import cn.scut.raputa.dto.ScreeningArchiveDTO;
import cn.scut.raputa.dto.ScreeningRecordDTO;
import cn.scut.raputa.entity.Appointment;
import cn.scut.raputa.entity.CaptureSession;
import cn.scut.raputa.entity.CheckRecord;
import cn.scut.raputa.entity.Patient;
import cn.scut.raputa.entity.ScreeningRecord;
import cn.scut.raputa.enums.CheckResult;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.AppointmentRepository;
import cn.scut.raputa.repository.CaptureSessionRepository;
import cn.scut.raputa.repository.CheckRecordRepository;
import cn.scut.raputa.repository.PatientRepository;
import cn.scut.raputa.repository.ScreeningRecordRepository;
import cn.scut.raputa.vo.ScreeningRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScreeningRecordServiceImpl implements ScreeningRecordService {

    private final ScreeningRecordRepository screeningRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final CaptureSessionRepository captureSessionRepository;
    private final CsvDataService csvDataService;

    @Override
    public Page<ScreeningRecordVO> page(int page, int size, String appointmentId, String patientId, String name, String status) {
        Specification<ScreeningRecord> spec = Specification.<ScreeningRecord>unrestricted()
                .and(likeIfPresent("appointmentId", appointmentId))
                .and(likeIfPresent("patientId", patientId))
                .and(likeIfPresent("subjectName", name))
                .and(eqIfPresent("status", normalizeBlank(status)));

        return screeningRecordRepository.findAll(
                spec,
                PageRequest.of(
                        Math.max(page - 1, 0),
                        Math.max(size, 1),
                        Sort.by(
                                Sort.Order.desc("checkTime"),
                                Sort.Order.desc("createdAt"))))
                .map(this::toVO);
    }

    @Override
    @Transactional
    public ScreeningRecordVO create(ScreeningRecordDTO dto) {
        if (dto == null) {
            throw new BizException(400, "筛查记录不能为空");
        }

        ScreeningRecord record = findReusableRecord(dto);
        if ("ARCHIVED".equals(record.getStatus())) {
            return toVO(record);
        }

        Appointment appointment = null;
        String appointmentId = trim(dto.getAppointmentId());
        if (appointmentId != null) {
            appointment = appointmentRepository.findById(appointmentId).orElse(null);
        }

        String subjectName = firstNonBlank(dto.getSubjectName(), appointment == null ? null : appointment.getName());
        if (subjectName == null) {
            throw new BizException(400, "筛查对象姓名不能为空");
        }

        String subjectDept = firstNonBlank(dto.getSubjectDept(), appointment == null ? null : appointment.getDept());
        String result = normalizeResult(dto);
        boolean abnormal = isAbnormal(result, safeCount(dto.getDysphagiaSwallows()), safeCount(dto.getAspirationSwallows()));

        record.setSource("APPOINTMENT");
        record.setAppointmentId(appointmentId);
        record.setPatientId(null);
        record.setSubjectName(subjectName);
        record.setSubjectGender(firstNonBlank(dto.getSubjectGender(), appointment == null ? null : appointment.getGender()));
        record.setSubjectAge(dto.getSubjectAge() == null ? null : Math.max(dto.getSubjectAge(), 0));
        record.setSubjectIdCard(firstNonBlank(dto.getSubjectIdCard(), appointment == null ? null : appointment.getIdCard()));
        record.setSubjectPhone(firstNonBlank(dto.getSubjectPhone(), appointment == null ? null : appointment.getPhone()));
        record.setSubjectDept(subjectDept);
        record.setCheckDept(trim(dto.getCheckDept()));
        record.setDeviceId(trim(dto.getDeviceId()));
        record.setMode(firstNonBlank(dto.getMode(), "REALTIME"));
        record.setSessionId(trim(dto.getSessionId()));
        record.setResult(result);
        record.setRiskLevel(firstNonBlank(dto.getRiskLevel(), abnormal ? "中风险" : "低风险"));
        record.setStaff(trim(dto.getStaff()));
        record.setTotalSwallows(safeCount(dto.getTotalSwallows()));
        record.setNormalSwallows(safeCount(dto.getNormalSwallows()));
        record.setDysphagiaSwallows(safeCount(dto.getDysphagiaSwallows()));
        record.setAspirationSwallows(safeCount(dto.getAspirationSwallows()));
        record.setCheckTime(parseDateTime(dto.getCheckTime()));
        record.setStatus(abnormal ? "NEEDS_PATIENT_RECORD" : "COMPLETED");

        if (!abnormal) {
            markAppointmentCompleted(appointment);
        }

        return toVO(screeningRecordRepository.save(record));
    }

    @Override
    @Transactional
    public ScreeningRecordVO archive(String id, ScreeningArchiveDTO dto) {
        ScreeningRecord record = screeningRecordRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "筛查记录不存在"));

        if ("ARCHIVED".equals(record.getStatus())) {
            return toVO(record);
        }

        String patientId = dto == null ? null : trim(dto.getPatientId());
        if (patientId == null) {
            throw new BizException(400, "patientId 不能为空");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BizException(404, "患者不存在"));

        String staff = firstNonBlank(dto == null ? null : dto.getStaff(), record.getStaff());
        if (staff == null) {
            throw new BizException(400, "归档前请填写报告医生");
        }

        relinkCaptureSession(record, patient);
        createPatientCheckRecords(record, patient, staff);
        finalizeCaptureSession(record);

        record.setPatientId(patient.getId());
        record.setSubjectName(patient.getName());
        record.setSubjectDept(patient.getDept());
        record.setStaff(staff);
        record.setStatus("ARCHIVED");
        record.setArchivedAt(LocalDateTime.now(ScreeningRecord.ZONE_CN));

        markAppointmentCompleted(record.getAppointmentId());

        return toVO(screeningRecordRepository.save(record));
    }

    private void markAppointmentCompleted(Appointment appointment) {
        if (appointment == null) {
            return;
        }
        appointment.setStatus("COMPLETED");
        appointmentRepository.save(appointment);
    }

    private void markAppointmentCompleted(String appointmentId) {
        String id = trim(appointmentId);
        if (id == null) {
            return;
        }
        appointmentRepository.findById(id).ifPresent(this::markAppointmentCompleted);
    }

    private ScreeningRecord findReusableRecord(ScreeningRecordDTO dto) {
        String sessionId = trim(dto.getSessionId());
        if (sessionId != null) {
            return screeningRecordRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId)
                    .orElseGet(ScreeningRecord::new);
        }
        return new ScreeningRecord();
    }

    private void relinkCaptureSession(ScreeningRecord record, Patient patient) {
        String sessionId = trim(record.getSessionId());
        if (sessionId == null) {
            return;
        }

        CaptureSession session = captureSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(404, "会话不存在: " + sessionId));
        session.setPatientId(patient.getId());
        session.setPatientNameSnapshot(patient.getName());
        captureSessionRepository.save(session);
    }

    private void finalizeCaptureSession(ScreeningRecord record) {
        String sessionId = trim(record.getSessionId());
        if (sessionId == null) {
            return;
        }
        csvDataService.finalizeSessionFilesBySessionId(sessionId);
    }

    private void createPatientCheckRecords(ScreeningRecord record, Patient patient, String staff) {
        int dysphagia = safeCount(record.getDysphagiaSwallows());
        int aspiration = safeCount(record.getAspirationSwallows());
        String result = normalizeResult(record.getResult());
        if (dysphagia == 0 && "DYSPHAGIA".equals(result)) {
            dysphagia = 1;
        }
        CheckResult normalizedCheckResult = CheckResult.fromLabel(result);
        if (aspiration == 0 && normalizedCheckResult != null && normalizedCheckResult.isAspiration()) {
            aspiration = 1;
        }

        List<CheckRecord> records = new ArrayList<>();
        if (dysphagia > 0) {
            records.add(toCheckRecord(record, patient, staff, CheckResult.DYSPHAGIA));
        }
        if (aspiration > 0) {
            records.add(toCheckRecord(record, patient, staff, CheckResult.ASPIRATION));
        }
        if (records.isEmpty()) {
            records.add(toCheckRecord(record, patient, staff, CheckResult.NORMAL));
        }
        checkRecordRepository.saveAll(records);
    }

    private CheckRecord toCheckRecord(ScreeningRecord record, Patient patient, String staff, CheckResult result) {
        CheckRecord check = new CheckRecord();
        check.setPatientId(patient.getId());
        check.setName(patient.getName());
        check.setStaff(staff);
        check.setPatientDeptSnapshot(patient.getDept());
        check.setResult(result);
        check.setCheckTime(record.getCheckTime() == null
                ? LocalDateTime.now(CheckRecord.ZONE_CN)
                : record.getCheckTime());
        return check;
    }

    private String normalizeResult(ScreeningRecordDTO dto) {
        int aspiration = safeCount(dto.getAspirationSwallows());
        int dysphagia = safeCount(dto.getDysphagiaSwallows());
        if (aspiration > 0) {
            return CheckResult.ASPIRATION.name();
        }
        if (dysphagia > 0) {
            return CheckResult.DYSPHAGIA.name();
        }
        return normalizeResult(dto.getResult());
    }

    private String normalizeResult(String raw) {
        CheckResult result = CheckResult.fromLabel(raw);
        return result == null ? CheckResult.NORMAL.name() : result.name();
    }

    private boolean isAbnormal(String result, int dysphagia, int aspiration) {
        return dysphagia > 0 || aspiration > 0 || !CheckResult.NORMAL.name().equals(result);
    }

    private ScreeningRecordVO toVO(ScreeningRecord r) {
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return ScreeningRecordVO.builder()
                .id(r.getId())
                .source(r.getSource())
                .status(r.getStatus())
                .appointmentId(r.getAppointmentId())
                .patientId(r.getPatientId())
                .subjectName(r.getSubjectName())
                .subjectGender(r.getSubjectGender())
                .subjectAge(r.getSubjectAge())
                .subjectIdCard(r.getSubjectIdCard())
                .subjectPhone(r.getSubjectPhone())
                .subjectDept(r.getSubjectDept())
                .checkDept(r.getCheckDept())
                .deviceId(r.getDeviceId())
                .mode(r.getMode())
                .sessionId(r.getSessionId())
                .result(formatResult(r.getResult()))
                .riskLevel(r.getRiskLevel())
                .staff(r.getStaff())
                .totalSwallows(r.getTotalSwallows())
                .normalSwallows(r.getNormalSwallows())
                .dysphagiaSwallows(r.getDysphagiaSwallows())
                .aspirationSwallows(r.getAspirationSwallows())
                .checkTime(r.getCheckTime() == null ? null : r.getCheckTime().format(tf))
                .archivedAt(r.getArchivedAt() == null ? null : r.getArchivedAt().format(tf))
                .build();
    }

    private String formatResult(String raw) {
        CheckResult result = CheckResult.fromLabel(raw);
        return result == null ? raw : result.getLabel();
    }

    private LocalDateTime parseDateTime(String value) {
        String v = trim(value);
        if (v == null) {
            return LocalDateTime.now(ScreeningRecord.ZONE_CN);
        }
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(v).atZoneSameInstant(ScreeningRecord.ZONE_CN).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                return LocalDateTime.now(ScreeningRecord.ZONE_CN);
            }
        }
    }

    private Specification<ScreeningRecord> likeIfPresent(String field, String q) {
        return (root, query, cb) -> (q == null || q.isBlank()) ? null : cb.like(root.get(field), "%" + q.trim() + "%");
    }

    private Specification<ScreeningRecord> eqIfPresent(String field, String q) {
        return (root, query, cb) -> (q == null || q.isBlank()) ? null : cb.equal(root.get(field), q.trim());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trim(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeBlank(String value) {
        return trim(value);
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}
