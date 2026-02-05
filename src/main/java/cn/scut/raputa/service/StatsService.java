package cn.scut.raputa.service;

import cn.scut.raputa.dto.StatsDTO;
import cn.scut.raputa.entity.CheckRecord;
import cn.scut.raputa.entity.Patient;
import cn.scut.raputa.enums.CheckResult;
import cn.scut.raputa.repository.CheckRecordRepository;
import cn.scut.raputa.repository.PatientFileRepository;
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
    private final PatientFileRepository patientFileRepository;

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

        // 按日期分组统计
        Map<LocalDate, Long> countByDate = records.stream()
            .collect(Collectors.groupingBy(
                record -> record.getCheckTime().toLocalDate(),
                Collectors.counting()
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
            Integer overt = resultMap.getOrDefault(CheckResult.OVERT_ASPIRATION, 0L).intValue();
            Integer silent = resultMap.getOrDefault(CheckResult.SILENT_ASPIRATION, 0L).intValue();

            result.add(new StatsDTO.DailyCheckResult(category, normal, dysphagia, overt, silent));
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

        // 查询时间范围内有检查记录的患者
        List<CheckRecord> records = checkRecordRepository.findByCheckTimeBetween(startDateTime, endDateTime);
        Set<String> patientIds = records.stream()
            .map(CheckRecord::getPatientId)
            .collect(Collectors.toSet());

        if (patientIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询这些患者的信息
        List<Patient> patients = patientRepository.findAllById(patientIds);

        // 按科室分组统计
        Map<String, Long> countByDept = patients.stream()
            .filter(patient -> patient.getDept() != null && !patient.getDept().isEmpty())
            .collect(Collectors.groupingBy(
                Patient::getDept,
                Collectors.counting()
            ));

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

        // 查询时间范围内的患者文件记录 (CSV文件代表设备使用)
        List<Object[]> deviceUsageData = patientFileRepository.findDeviceUsageStats(
            startDateTime, endDateTime);

        // 按设备ID和日期分组统计使用时长
        Map<String, Map<LocalDate, Double>> usageByDeviceAndDate = new HashMap<>();

        for (Object[] row : deviceUsageData) {
            String sessionKey = (String) row[0];
            LocalDateTime savedAt = (LocalDateTime) row[1];
            Long fileCount = (Long) row[2];

            // 从sessionKey中提取设备ID (假设格式为: deviceId_timestamp)
            String deviceId = extractDeviceIdFromSessionKey(sessionKey);
            LocalDate date = savedAt.toLocalDate();

            // 估算使用时长: 每个文件约代表0.5小时的使用
            double hours = fileCount * 0.5;

            usageByDeviceAndDate
                .computeIfAbsent(deviceId, k -> new HashMap<>())
                .merge(date, hours, Double::sum);
        }

        // 转换为结果列表
        List<StatsDTO.DeviceUsage> result = new ArrayList<>();
        for (Map.Entry<String, Map<LocalDate, Double>> entry : usageByDeviceAndDate.entrySet()) {
            String deviceId = entry.getKey();
            Map<LocalDate, Double> dateUsageMap = entry.getValue();

            // 生成完整的日期序列
            List<Double> usageHours = new ArrayList<>();
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                Double hours = dateUsageMap.getOrDefault(current, 0.0);
                usageHours.add(Math.round(hours * 10.0) / 10.0); // 保留1位小数
                current = current.plusDays(1);
            }

            result.add(new StatsDTO.DeviceUsage(deviceId, usageHours));
        }

        // 按设备ID排序
        result.sort(Comparator.comparing(StatsDTO.DeviceUsage::getDeviceId));

        return result;
    }

    /**
     * 从sessionKey中提取设备ID
     */
    private String extractDeviceIdFromSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isEmpty()) {
            return "UNKNOWN";
        }

        // sessionKey格式可能是: deviceId_timestamp 或 deviceId
        int underscoreIndex = sessionKey.indexOf('_');
        if (underscoreIndex > 0) {
            return sessionKey.substring(0, underscoreIndex);
        }

        return sessionKey;
    }

    /**
     * 格式化日期类别标签
     * 如果日期范围<=7天，使用星期几；否则使用日期
     */
    private String formatDateCategory(LocalDate date, LocalDate startDate, LocalDate endDate) {
        // 始终返回完整日期格式
        //return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // 或者使用 "MM-dd" 格式
        return date.format(DateTimeFormatter.ofPattern("MM-dd"));
    }

}

