package cn.scut.raputa.service;

import cn.scut.raputa.dto.StatsDTO;
import cn.scut.raputa.entity.CheckRecord;
import cn.scut.raputa.entity.CaptureSession;
import cn.scut.raputa.entity.Patient;
import cn.scut.raputa.enums.CheckResult;
import cn.scut.raputa.repository.CaptureSessionRepository;
import cn.scut.raputa.repository.CheckRecordRepository;
import cn.scut.raputa.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final CheckRecordRepository checkRecordRepository;
    private final PatientRepository patientRepository;
    private final CaptureSessionRepository captureSessionRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEKDAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    /**
     * 获取统计数据
     */
    public StatsDTO.StatsResponse getStats(StatsDTO.StatsQuery query) {
        // 确定日期范围
        LocalDate endDate = query.getEndDate() != null
            ? LocalDate.parse(query.getEndDate(), DATE_FORMATTER)
            : LocalDate.now();

        int days = query.getDays() != null ? query.getDays() : 7;
        LocalDate startDate = query.getStartDate() != null
            ? LocalDate.parse(query.getStartDate(), DATE_FORMATTER)
            : endDate.minusDays(days - 1);

        log.info("获取统计数据: startDate={}, endDate={}", startDate, endDate);

        // 获取各项统计数据
        List<StatsDTO.DailyPatientCount> dailyPatientCount = getDailyPatientCount(startDate, endDate);
        List<StatsDTO.DailyCheckResult> dailyCheckResult = getDailyCheckResult(startDate, endDate);
        List<StatsDTO.DeptPatientCount> deptPatientCount = getDeptPatientCount(startDate, endDate);
        List<StatsDTO.DeviceUsage> deviceUsage = getDeviceUsage(startDate, endDate);

        return new StatsDTO.StatsResponse(
            dailyPatientCount,
            dailyCheckResult,
            deptPatientCount,
            deviceUsage
        );
    }

    /**
     * 获取每日检测患者数量
     */
    private List<StatsDTO.DailyPatientCount> getDailyPatientCount(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        // 查询时间范围内的所有检查记录
        List<CheckRecord> records = checkRecordRepository.findByCheckTimeBetween(startDateTime, endDateTime);

        // 按日期统计不同患者数，避免同一次检测产生多条结果记录后重复计数
        Map<LocalDate, Long> countByDate = records.stream()
            .collect(Collectors.groupingBy(
                record -> record.getCheckTime().toLocalDate(),
                Collectors.mapping(CheckRecord::getPatientId, Collectors.collectingAndThen(Collectors.toSet(), set -> (long) set.size()))
            ));

        // 生成完整的日期序列
        List<StatsDTO.DailyPatientCount> result = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            String category = formatDateCategory(current, startDate, endDate);
            Integer count = countByDate.getOrDefault(current, 0L).intValue();
            result.add(new StatsDTO.DailyPatientCount(category, count));
            current = current.plusDays(1);
        }

        return result;
    }

    /**
     * 获取每日患者检测结果情况
     */
    private List<StatsDTO.DailyCheckResult> getDailyCheckResult(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        // 查询时间范围内的所有检查记录
        List<CheckRecord> records = checkRecordRepository.findByCheckTimeBetween(startDateTime, endDateTime);

        // 按日期和结果分组统计
        Map<LocalDate, Map<CheckResult, Long>> countByDateAndResult = records.stream()
            .collect(Collectors.groupingBy(
                record -> record.getCheckTime().toLocalDate(),
                Collectors.groupingBy(
                    CheckRecord::getResult,
                    Collectors.counting()
                )
            ));

        // 生成完整的日期序列
        List<StatsDTO.DailyCheckResult> result = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            String category = formatDateCategory(current, startDate, endDate);
            Map<CheckResult, Long> resultMap = countByDateAndResult.getOrDefault(current, new HashMap<>());

            Integer normal = resultMap.getOrDefault(CheckResult.NORMAL, 0L).intValue();
            Integer dysphagia = resultMap.getOrDefault(CheckResult.DYSPHAGIA, 0L).intValue();
            Integer aspiration = Math.toIntExact(
                    resultMap.getOrDefault(CheckResult.OVERT_ASPIRATION, 0L)
                            + resultMap.getOrDefault(CheckResult.SILENT_ASPIRATION, 0L)
                            + resultMap.getOrDefault(CheckResult.ASPIRATION, 0L));

            result.add(new StatsDTO.DailyCheckResult(category, normal, dysphagia, aspiration));
            current = current.plusDays(1);
        }

        return result;
    }

    /**
     * 获取各科室患者占比
     */
    private List<StatsDTO.DeptPatientCount> getDeptPatientCount(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<CheckRecord> records = checkRecordRepository.findByCheckTimeBetween(startDateTime, endDateTime);
        Set<String> patientIds = records.stream()
                .map(CheckRecord::getPatientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (patientIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, String> currentDeptByPatient = patientRepository.findAllById(patientIds).stream()
                .filter(patient -> patient.getDept() != null && !patient.getDept().isBlank())
                .collect(Collectors.toMap(Patient::getId, Patient::getDept, (a, b) -> a));

        Map<String, String> deptByPatient = new HashMap<>();
        for (CheckRecord record : records) {
            String patientId = record.getPatientId();
            if (patientId == null || deptByPatient.containsKey(patientId)) {
                continue;
            }
            String snapshot = record.getPatientDeptSnapshot();
            String dept = snapshot != null && !snapshot.isBlank()
                    ? snapshot
                    : currentDeptByPatient.get(patientId);
            if (dept != null && !dept.isBlank()) {
                deptByPatient.put(patientId, dept);
            }
        }

        Map<String, Long> countByDept = deptByPatient.values().stream()
                .collect(Collectors.groupingBy(dept -> dept, Collectors.counting()));

        // 转换为结果列表并按数量降序排序
        return countByDept.entrySet().stream()
            .map(entry -> new StatsDTO.DeptPatientCount(entry.getKey(), entry.getValue().intValue()))
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * 获取设备使用时长
     */
    private List<StatsDTO.DeviceUsage> getDeviceUsage(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        Map<String, Map<LocalDate, Double>> usageByDeviceAndDate = new HashMap<>();

        for (CaptureSession session : captureSessionRepository.findByStartedAtBeforeAndDeviceIdIsNotNull(endDateTime)) {
            String deviceId = session.getDeviceId();
            LocalDateTime sessionStart = session.getStartedAt();
            LocalDateTime sessionEnd = sessionEndTime(session);
            if (deviceId == null || deviceId.isBlank() || sessionStart == null || sessionEnd == null) {
                continue;
            }
            if (sessionEnd.isBefore(sessionStart)) {
                sessionEnd = sessionStart;
            }

            LocalDateTime clippedStart = max(sessionStart, startDateTime);
            LocalDateTime clippedEnd = min(sessionEnd, endDateTime);
            if (!clippedStart.isBefore(clippedEnd)) {
                continue;
            }

            LocalDateTime cursor = clippedStart;
            while (cursor.isBefore(clippedEnd)) {
                LocalDate date = cursor.toLocalDate();
                LocalDateTime nextDay = date.plusDays(1).atStartOfDay();
                LocalDateTime segmentEnd = min(nextDay, clippedEnd);
                double minutes = java.time.Duration.between(cursor, segmentEnd).toMillis() / 60_000.0;
                usageByDeviceAndDate
                        .computeIfAbsent(deviceId, k -> new HashMap<>())
                        .merge(date, minutes, Double::sum);
                cursor = segmentEnd;
            }
        }

        // 转换为结果列表
        List<StatsDTO.DeviceUsage> result = new ArrayList<>();
        for (Map.Entry<String, Map<LocalDate, Double>> entry : usageByDeviceAndDate.entrySet()) {
            String deviceId = entry.getKey();
            Map<LocalDate, Double> dateUsageMap = entry.getValue();

            // 生成完整的日期序列
            List<Double> usageMinutes = new ArrayList<>();
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                Double minutes = dateUsageMap.getOrDefault(current, 0.0);
                usageMinutes.add(Math.round(minutes * 10.0) / 10.0); // 保留1位小数
                current = current.plusDays(1);
            }

            result.add(new StatsDTO.DeviceUsage(deviceId, usageMinutes));
        }

        // 按设备ID排序
        result.sort(Comparator.comparing(StatsDTO.DeviceUsage::getDeviceId));

        return result;
    }

    private LocalDateTime sessionEndTime(CaptureSession session) {
        if (session.getStoppedAt() != null) {
            return session.getStoppedAt();
        }
        if (session.getFinalizedAt() != null) {
            return session.getFinalizedAt();
        }
        if (session.getUpdatedAt() != null) {
            return session.getUpdatedAt();
        }
        return session.getStartedAt();
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    /**
     * 格式化日期类别标签
     * 如果日期范围<=7天，使用星期几；否则使用日期
     */
    private String formatDateCategory(LocalDate date, LocalDate startDate, LocalDate endDate) {
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        if (daysBetween <= 7) {
            // 使用星期几
            int dayOfWeek = date.getDayOfWeek().getValue() % 7; // 转换为0-6 (周日为0)
            return WEEKDAYS[dayOfWeek];
        } else {
            // 使用日期
            return date.format(DateTimeFormatter.ofPattern("MM-dd"));
        }
    }
}
