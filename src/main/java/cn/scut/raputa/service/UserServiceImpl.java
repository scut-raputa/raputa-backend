package cn.scut.raputa.service;

import cn.scut.raputa.entity.User;
import cn.scut.raputa.enums.UserRole;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.UserRepository;
import cn.scut.raputa.utils.JwtService;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.AuthVO;
import lombok.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public User register(String username, String rawPwd, String hospitalName, String departmentName) {
        String u = username.trim();
        if (userRepository.existsByUsername(u)) {
            throw new BizException(409, "该用户名已被注册");
        }
        User user = new User();
        user.setUsername(u);
        user.setPassword(passwordEncoder.encode(rawPwd));
        user.setHospitalName(hospitalName);
        user.setDepartmentName(departmentName);
        user.setEnabled(true);
        user.setAvatarUrl("/images/default-avatar.png");
        user.setRole(UserRole.DEPARTMENT);
        try {
            return userRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BizException(409, "该用户名已被注册");
        }
    }

    @Override
    @Transactional
    public AuthVO login(String username, String rawPassword, String ip) {
        User u = userRepository.findByUsername(username);
        if (u == null) {
            throw new BizException(404, "该用户不存在，或用户名输入错误");
        }
        if (!Boolean.TRUE.equals(u.getEnabled())) {
            throw new BizException(403, "该用户不可用，请联系系统管理员");
        }
        if (!passwordEncoder.matches(rawPassword, u.getPassword())) {
            throw new BizException(401, "密码错误");
        }

        updateLastLogin(u, ip);

        String token = jwtService.generateToken(u);
        AuthVO auth = new AuthVO();
        auth.setToken(token);
        auth.setUser(VoMappers.toUserVO(u));
        return auth;
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    @Transactional
    public void updateLastLogin(User u, String ip) {
        LocalDateTime now = LocalDateTime.now(User.ZONE_CN);
        int n = userRepository.touchLogin(u.getId(), now, ip);
        u.setLastLoginAt(now);
        u.setLastLoginIp(ip);
        if (n != 1) {
            log.warn("touchLogin affected {} rows for userId={}, fallback to save()", n, u.getId());
            u.setLastLoginAt(now);
            u.setLastLoginIp(ip);
            userRepository.save(u);
        }
    }

}
