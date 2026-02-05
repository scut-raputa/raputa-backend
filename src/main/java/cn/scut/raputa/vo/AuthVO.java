package cn.scut.raputa.vo;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthVO {
    private String token;
    private UserVO user;
}
