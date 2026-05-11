package cn.scut.raputa.task;

import cn.scut.raputa.service.DeviceLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceLockCleaner {

    private final DeviceLockService deviceLockService;

    @Scheduled(fixedDelayString = "${raputa.device-lock.cleaner-interval-seconds:30}000")
    public void cleanExpiredLocks() {
        int removed = deviceLockService.cleanupExpiredLocks();
        if (removed > 0) {
            log.info("清理过期设备锁完成: removed={}", removed);
        }
    }
}
