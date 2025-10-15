package cn.scut.raputa.controller;

import cn.scut.raputa.dto.UserLoginDTO;
import cn.scut.raputa.dto.UserRegisterDTO;
import cn.scut.raputa.entity.User;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.UserService;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.AuthVO;
import cn.scut.raputa.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@RequestBody @Valid UserRegisterDTO dto) {
        User u = userService.register(dto.getUsername(), dto.getPassword(),
                dto.getHospitalName(), dto.getDepartmentName());
        return ApiResponse.ok(VoMappers.toUserVO(u));
    }

    @PostMapping("/login")
    public ApiResponse<AuthVO> login(@RequestBody @Valid UserLoginDTO dto,
            HttpServletRequest request) {
        String ip = extractClientIp(request);
        AuthVO auth = userService.login(dto.getUsername(), dto.getPassword(), ip);
        return ApiResponse.ok(auth);
    }

    private static String extractClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return (comma > 0) ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank())
            return ip.trim();
        return req.getRemoteAddr();
    }
}
