package cn.scut.raputa.controller;

import cn.scut.raputa.dto.AdminPasswordResetDTO;
import cn.scut.raputa.dto.AdminUserCreateDTO;
import cn.scut.raputa.dto.AdminUserUpdateDTO;
import cn.scut.raputa.entity.User;
import cn.scut.raputa.enums.UserRole;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.UserRepository;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.DeviceLockService;
import cn.scut.raputa.utils.AvatarUrls;
import cn.scut.raputa.utils.UserSessionStatus;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceLockService deviceLockService;

    @GetMapping
    public ApiResponse<?> list(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String hospitalName,
            @RequestParam(required = false) String departmentName,
            @RequestParam(required = false) UserRole role) {
        requireAdmin(authentication);
        Specification<User> spec = Specification.<User>unrestricted()
                .and(likeIfPresent("username", username))
                .and(likeIfPresent("hospitalName", hospitalName))
                .and(likeIfPresent("departmentName", departmentName))
                .and(eqIfPresent("role", role));
        Page<User> pg = userRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1),
                        Sort.by(Sort.Order.asc("role"), Sort.Order.asc("username"))));
        List<UserVO> items = pg.getContent().stream().map(VoMappers::toUserVO).toList();
        return ApiResponse.ok(new PageWrap<>(items, pg.getTotalElements()));
    }

    @PostMapping
    @Transactional
    public ApiResponse<UserVO> create(Authentication authentication, @RequestBody AdminUserCreateDTO dto) {
        requireAdmin(authentication);
        String username = requireText(dto.getUsername(), "请输入用户名");
        if (userRepository.existsByUsername(username)) {
            throw new BizException(409, "该用户名已存在");
        }
        String password = requirePassword(dto.getPassword());
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setHospitalName(requireText(dto.getHospitalName(), "请输入医院名称"));
        user.setDepartmentName(requireText(dto.getDepartmentName(), "请输入科室名称"));
        user.setRole(dto.getRole() == null ? UserRole.DEPARTMENT : dto.getRole());
        user.setEnabled(Boolean.TRUE);
        user.setAvatarUrl(AvatarUrls.forRole(user.getRole()));
        return ApiResponse.ok(VoMappers.toUserVO(userRepository.save(user)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<UserVO> update(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody AdminUserUpdateDTO dto) {
        User admin = requireAdmin(authentication);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "账户不存在"));
        ensureAccountNotUsingDevice(user);
        assertCanChangeAdminState(admin, user, dto.getRole());
        user.setHospitalName(requireText(dto.getHospitalName(), "请输入医院名称"));
        user.setDepartmentName(requireText(dto.getDepartmentName(), "请输入科室名称"));
        user.setRole(dto.getRole() == null ? UserRole.DEPARTMENT : dto.getRole());
        user.setAvatarUrl(AvatarUrls.normalize(user.getAvatarUrl(), user.getRole()));
        return ApiResponse.ok(VoMappers.toUserVO(userRepository.save(user)));
    }

    @PostMapping("/{id}/reset-password")
    @Transactional
    public ApiResponse<UserVO> resetPassword(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody AdminPasswordResetDTO dto) {
        User admin = requireAdmin(authentication);
        if (dto == null || dto.getAdminPassword() == null
                || !passwordEncoder.matches(dto.getAdminPassword(), admin.getPassword())) {
            throw new BizException(403, "管理员密码校验失败");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "账户不存在"));
        ensureAccountNotUsingDevice(user);
        user.setPassword(passwordEncoder.encode(requirePassword(dto.getNewPassword())));
        return ApiResponse.ok(VoMappers.toUserVO(userRepository.save(user)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long id) {
        User admin = requireAdmin(authentication);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "账户不存在"));
        if (admin.getId().equals(user.getId())) {
            throw new BizException(400, "不能删除当前登录账户");
        }
        if (user.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BizException(400, "系统至少需要保留一个管理员账户");
        }
        ensureAccountNotLoggedIn(user);
        ensureAccountNotUsingDevice(user);
        userRepository.delete(user);
        return ApiResponse.ok(null);
    }

    private User requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BizException(401, "未登录或登录已失效");
        }
        User user = userRepository.findByUsername(authentication.getName());
        if (user == null || user.getRole() != UserRole.ADMIN || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(403, "无权限访问系统管理");
        }
        return user;
    }

    private void assertCanChangeAdminState(User admin, User target, UserRole nextRole) {
        UserRole role = nextRole == null ? UserRole.DEPARTMENT : nextRole;
        if (admin.getId().equals(target.getId()) && role != UserRole.ADMIN) {
            throw new BizException(400, "不能移除当前登录管理员的管理员权限");
        }
        if (target.getRole() == UserRole.ADMIN
                && role != UserRole.ADMIN
                && userRepository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new BizException(400, "系统至少需要保留一个启用状态的管理员账户");
        }
    }

    private void ensureAccountNotUsingDevice(User user) {
        if (user != null && deviceLockService.hasActiveLockForHolder(user.getUsername())) {
            throw new BizException(409, "该账户当前正在使用设备，请等待检测、报告下载和归档完成后再修改账户信息");
        }
    }

    private void ensureAccountNotLoggedIn(User user) {
        if (UserSessionStatus.isOnline(user)) {
            throw new BizException(409, "该账户当前处于登录状态，不能删除");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, message);
        }
        return value.trim();
    }

    private String requirePassword(String value) {
        if (value == null || value.length() < 6) {
            throw new BizException(400, "密码至少需要 6 位");
        }
        return value;
    }

    private Specification<User> likeIfPresent(String field, String q) {
        return (root, query, cb) ->
                (q == null || q.isBlank()) ? null : cb.like(root.get(field), "%" + q.trim() + "%");
    }

    private <T> Specification<User> eqIfPresent(String field, T value) {
        return (root, query, cb) -> value == null ? null : cb.equal(root.get(field), value);
    }

    public record PageWrap<T>(List<T> items, long total) {
    }
}
