package cn.scut.raputa.dto;

import cn.scut.raputa.enums.UserRole;
import lombok.Data;

@Data
public class AdminUserUpdateDTO {
    private String hospitalName;
    private String departmentName;
    private UserRole role;
}
