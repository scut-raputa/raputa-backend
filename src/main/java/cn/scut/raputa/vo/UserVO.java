package cn.scut.raputa.vo;

import java.time.LocalDateTime;

import cn.scut.raputa.enums.UserRole;

import lombok.*;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private Long id;
    private String username;
    private String hospitalName;
    private String departmentName;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private String avatarUrl;
    private UserRole role;
}
