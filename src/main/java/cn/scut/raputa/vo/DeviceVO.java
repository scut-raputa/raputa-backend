package cn.scut.raputa.vo;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVO {
    private String id;
    private String name;
    private String ip;
    private String lastConnectedTime; // formatted "yyyy-MM-dd HH:mm:ss", nullable
    private String status;
    private String description;
    private String storageLocation;
    private String responsible;
}