package cn.scut.raputa.controller;

import cn.scut.raputa.dto.UserLoginDTO;
import cn.scut.raputa.dto.UserRegisterDTO;
import cn.scut.raputa.entity.User;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.security.JwtAuthenticationFilter;
import cn.scut.raputa.service.UserService;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.AuthVO;
import cn.scut.raputa.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户注册、登录等操作")
public class UserController {

    private final UserService userService;
    @Value("${security.jwt.exp-minutes:720}")
    private long jwtExpMinutes;

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新的用户账户")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "注册成功",
                    content = @Content(schema = @Schema(implementation = UserVO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "用户名已存在")
    })
    public ApiResponse<UserVO> register(
            @Parameter(description = "用户注册信息", required = true)
            @RequestBody @Valid UserRegisterDTO dto) {
        User u = userService.register(dto.getUsername(), dto.getPassword(),
                dto.getHospitalName(), dto.getDepartmentName());
        return ApiResponse.ok(VoMappers.toUserVO(u));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录获取访问令牌")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功",
                    content = @Content(schema = @Schema(implementation = AuthVO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "用户名或密码错误")
    })
    public ApiResponse<AuthVO> login(
            @Parameter(description = "用户登录信息", required = true)
            @RequestBody @Valid UserLoginDTO dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        String ip = extractClientIp(request);
        AuthVO auth = userService.login(dto.getUsername(), dto.getPassword(), ip);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(auth.getToken()).toString());
        auth.setToken(null);
        return ApiResponse.ok(auth);
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户", description = "根据服务端会话 Cookie 返回当前用户信息")
    public ApiResponse<UserVO> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BizException(401, "未登录或登录已失效");
        }
        User user = userService.findByUsername(authentication.getName());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(401, "未登录或登录已失效");
        }
        return ApiResponse.ok(VoMappers.toUserVO(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "清理服务端会话 Cookie")
    public ApiResponse<Void> logout(Authentication authentication, HttpServletResponse response) {
        if (authentication != null && authentication.isAuthenticated()) {
            userService.markLoggedOut(authentication.getName());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString());
        return ApiResponse.ok(null);
    }

    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(JwtAuthenticationFilter.SESSION_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(jwtExpMinutes))
                .build();
    }

    private ResponseCookie expiredSessionCookie() {
        return ResponseCookie.from(JwtAuthenticationFilter.SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
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
