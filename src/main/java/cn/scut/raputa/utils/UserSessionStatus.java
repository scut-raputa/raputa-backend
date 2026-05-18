package cn.scut.raputa.utils;

import cn.scut.raputa.entity.User;

import java.time.LocalDateTime;

public final class UserSessionStatus {

    public static final long ONLINE_WINDOW_MINUTES = 720;
    public static final long TOUCH_INTERVAL_SECONDS = 60;

    private UserSessionStatus() {
    }

    public static boolean isOnline(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getSessionActive()) || user.getLastSeenAt() == null) {
            return false;
        }
        return user.getLastSeenAt().isAfter(LocalDateTime.now(User.ZONE_CN).minusMinutes(ONLINE_WINDOW_MINUTES));
    }
}
