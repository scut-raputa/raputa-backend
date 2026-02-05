package cn.scut.raputa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统计数据传输对象
 */
public class StatsDTO {

    /**
     * 每日检测患者数量数据项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPatientCount {
        private String category;  // 日期标签 (如: 周日, 2024-01-01)
        private Integer value;    // 患者数量
    }

    /**
     * 每日患者检测结果情况数据项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCheckResult {
        private String category;      // 日期标签
        private Integer normal;       // 正常患者数
        private Integer dysphagia;    // 吞咽障碍患者数
        private Integer overt;        // 显性误吸患者数
        private Integer silent;       // 隐性误吸患者数
    }

    /**
     * 科室患者占比数据项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeptPatientCount {
        private String name;   // 科室名称
        private Integer value; // 患者数量
    }

    /**
     * 设备使用时长数据项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceUsage {
        private String deviceId;           // 设备ID
        private List<Double> usageHours;   // 每日使用时长数组 (单位: 小时)
    }

    /**
     * 统计数据响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsResponse {
        private List<DailyPatientCount> dailyPatientCount;    // 每日检测患者数量
        private List<DailyCheckResult> dailyCheckResult;      // 每日患者检测结果情况
        private List<DeptPatientCount> deptPatientCount;      // 各科室患者占比
        private List<DeviceUsage> deviceUsage;                // 设备使用时长
    }

    /**
     * 统计查询参数
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsQuery {
        private String startDate;  // 开始日期 (格式: yyyy-MM-dd)
        private String endDate;    // 结束日期 (格式: yyyy-MM-dd)
        private Integer days;      // 最近N天 (默认7天)
    }
}
