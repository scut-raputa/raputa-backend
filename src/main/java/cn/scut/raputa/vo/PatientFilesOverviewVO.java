package cn.scut.raputa.vo;

import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class PatientFilesOverviewVO {
    private String id;
    private String name;
    private List<DateGroup> dates;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    public static class DateGroup {
        private String date;              // yyyy-MM-dd
        private List<TimeGroup> slots;
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
        private String id;
        private String name;
        private String type;              // csv/wav/pdf
    }
}
