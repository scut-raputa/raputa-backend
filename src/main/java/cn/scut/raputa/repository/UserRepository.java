package cn.scut.raputa.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import cn.scut.raputa.entity.User;
import cn.scut.raputa.enums.UserRole;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    User findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);

    long countByRoleAndEnabledTrue(UserRole role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update User u set u.lastLoginAt = :t, u.lastLoginIp = :ip, u.sessionActive = true, u.lastSeenAt = :t where u.id = :id")
    int touchLogin(@Param("id") Long id,
            @Param("t") LocalDateTime time,
            @Param("ip") String ip);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update User u
            set u.sessionActive = true, u.lastSeenAt = :now
            where u.id = :id
              and (u.sessionActive = false or u.lastSeenAt is null or u.lastSeenAt < :staleBefore)
            """)
    int touchSeenIfStale(@Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update User u set u.sessionActive = false, u.lastSeenAt = :now where u.username = :username")
    int markLoggedOut(@Param("username") String username,
            @Param("now") LocalDateTime now);
}
