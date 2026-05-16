package cn.scut.raputa.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cn.scut.raputa.entity.User;
import cn.scut.raputa.enums.UserRole;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    User findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);

    long countByRoleAndEnabledTrue(UserRole role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.lastLoginAt = :t, u.lastLoginIp = :ip where u.id = :id")
    int touchLogin(@Param("id") Long id,
            @Param("t") LocalDateTime time,
            @Param("ip") String ip);
}
