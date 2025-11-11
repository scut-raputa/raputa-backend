// cn/scut/raputa/vo/PatientFilesOverviewVO.java
package cn.scut.raputa.vo;

import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class PatientFilesOverviewVO {
    private String id;
    private String name;
    private List<DateGroup> dates; // 每天一个组，组内再按“时间段（秒）”细分

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    public static class DateGroup {
        private String date;              // yyyy-MM-dd
        private List<TimeGroup> slots;    // 同一天里的多个时间段
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    public static class TimeGroup {
        private String time;              // HH:mm:ss
        private List<FileItem> files;     // imu.csv / gas.csv / audio.wav …
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    public static class FileItem {
        private String name;              // 文件名（不含路径）
        private String type;              // csv/wav/pdf
        private String path;              // 绝对路径（下载用）
    }
}
