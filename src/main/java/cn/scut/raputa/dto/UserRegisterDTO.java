package cn.scut.raputa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDTO {
    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @NotBlank
    @Size(max = 128)
    private String hospitalName;

    @NotBlank
    @Size(max = 128)
    private String departmentName;
}
