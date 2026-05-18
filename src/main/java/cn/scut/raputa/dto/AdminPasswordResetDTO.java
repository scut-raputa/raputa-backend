package cn.scut.raputa.dto;

import lombok.Data;

@Data
public class AdminPasswordResetDTO {
    private String adminPassword;
    private String newPassword;
}
