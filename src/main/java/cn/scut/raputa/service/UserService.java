package cn.scut.raputa.service;

import cn.scut.raputa.entity.User;
import cn.scut.raputa.vo.AuthVO;

public interface UserService {
    User register(String username, String rawPassword, String hospitalName, String departmentName);

    User findByUsername(String username);

    boolean verifyPassword(String raw, String encoded);

    void updateLastLogin(User u, String ip);

    AuthVO login(String username, String rawPassword, String ip);
}
