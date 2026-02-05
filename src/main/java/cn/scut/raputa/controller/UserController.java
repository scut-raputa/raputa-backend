package cn.scut.raputa.controller;

import cn.scut.raputa.dto.UserLoginDTO;
import cn.scut.raputa.dto.UserRegisterDTO;
import cn.scut.raputa.entity.User;
import cn.scut.raputa.response.ApiResponse;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户注册、登录等操作")
public class UserController {

    private final UserService userService;

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
