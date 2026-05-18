package cn.scut.raputa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class StatsDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPatientCount {
        private String category;
        private Integer value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCheckResult {
        private String category;
        private Integer normal;
        private Integer dysphagia;
        private Integer aspiration;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeptPatientCount {
        private String name;
        private Integer value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceUsage {
        private String deviceId;
        private List<Double> usageMinutes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsResponse {
        private List<DailyPatientCount> dailyPatientCount;
        private List<DailyCheckResult> dailyCheckResult;
        private List<DeptPatientCount> deptPatientCount;
        private List<DeviceUsage> deviceUsage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsQuery {
        private String startDate;
        private String endDate;
        private Integer days;
    }
}
