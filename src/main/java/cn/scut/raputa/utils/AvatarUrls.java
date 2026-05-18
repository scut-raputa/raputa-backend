package cn.scut.raputa.utils;

import cn.scut.raputa.enums.UserRole;

public final class AvatarUrls {
    public static final String ADMIN = "/images/avatar-admin.png";
    public static final String DEPARTMENT = "/images/avatar-department.png";

    private AvatarUrls() {
    }

    public static String forRole(UserRole role) {
        return role == UserRole.ADMIN ? ADMIN : DEPARTMENT;
    }

    public static String normalize(String avatarUrl, UserRole role) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return forRole(role);
        }
        return avatarUrl.trim();
    }
}
