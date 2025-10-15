package cn.scut.raputa.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CheckRecordVO {
    private String id;
    private String name;
    private String staff;
    private String result;
    private String date;
}
