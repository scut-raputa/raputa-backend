package cn.scut.raputa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

import cn.scut.raputa.enums.UserRole;

@Entity
@Getter
@Setter
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username"))
public class User {

    public static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String username;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(name = "hospital_name", nullable = false, length = 128)
    private String hospitalName;

    @Column(name = "department_name", nullable = false, length = 128)
    private String departmentName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public User() {
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZONE_CN);
        }
    }

}
