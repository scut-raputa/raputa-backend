package cn.scut.raputa.service;

import cn.scut.raputa.entity.DeviceSessionLock;
import cn.scut.raputa.repository.DeviceSessionLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceLockService {

    @Value("${raputa.device-lock.ttl-seconds:45}")
    private long ttlSeconds;

    private final DeviceSessionLockRepository deviceSessionLockRepository;

    @Transactional
    public LockAcquireResult tryAcquire(
            String deviceId,
            String sessionId,
            String patientId,
            String patientName,
            String holder) {

        LocalDateTime now = now();
        DeviceSessionLock current = deviceSessionLockRepository.findById(deviceId).orElse(null);

        if (current == null || isExpired(current, now)) {
            DeviceSessionLock lock = newLock(deviceId, sessionId, patientId, patientName, holder, now);
            deviceSessionLockRepository.save(lock);
            return new LockAcquireResult(true, lock);
        }

        if (sessionId != null && sessionId.equals(current.getSessionId())) {
            current.setHeartbeatAt(now);
            current.setExpiresAt(now.plusSeconds(normalizedTtl()));
            current.setHolder(holder);
            deviceSessionLockRepository.save(current);
            return new LockAcquireResult(true, current);
        }

        return new LockAcquireResult(false, current);
    }

    @Transactional
    public boolean release(String deviceId, String sessionId, String reason, boolean force) {
        DeviceSessionLock current = deviceSessionLockRepository.findById(deviceId).orElse(null);
        if (current == null) {
            return true;
        }

        boolean canRelease = force
                || sessionId == null
                || sessionId.isBlank()
                || sessionId.equals(current.getSessionId());

        if (!canRelease) {
            return false;
        }

        deviceSessionLockRepository.deleteById(deviceId);
        log.info("释放设备锁: deviceId={}, sessionId={}, reason={}", deviceId, current.getSessionId(), reason);
        return true;
    }

    @Transactional
    public void heartbeat(String deviceId, String sessionId) {
        DeviceSessionLock current = deviceSessionLockRepository.findById(deviceId).orElse(null);
        if (current == null) {
            return;
        }
        if (sessionId != null && !sessionId.isBlank() && !sessionId.equals(current.getSessionId())) {
            return;
        }

        LocalDateTime now = now();
        current.setHeartbeatAt(now);
        current.setExpiresAt(now.plusSeconds(normalizedTtl()));
        deviceSessionLockRepository.save(current);
    }

    @Transactional(readOnly = true)
    public Map<String, DeviceSessionLock> getActiveLocks(Set<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LocalDateTime now = now();
        return deviceSessionLockRepository
                .findAllByDeviceIdInAndExpiresAtAfter(deviceIds, now)
                .stream()
                .collect(Collectors.toMap(DeviceSessionLock::getDeviceId, Function.identity(), (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public DeviceSessionLock getActiveLock(String deviceId) {
        DeviceSessionLock lock = deviceSessionLockRepository.findById(deviceId).orElse(null);
        if (lock == null) {
            return null;
        }
        return isExpired(lock, now()) ? null : lock;
    }

    @Transactional
    public int cleanupExpiredLocks() {
        LocalDateTime now = now();
        Collection<DeviceSessionLock> expired = deviceSessionLockRepository.findByExpiresAtBefore(now);
        if (expired.isEmpty()) {
            return 0;
        }
        int size = expired.size();
        deviceSessionLockRepository.deleteAll(expired);
        return size;
    }

    private DeviceSessionLock newLock(
            String deviceId,
            String sessionId,
            String patientId,
            String patientName,
            String holder,
            LocalDateTime now) {
        return DeviceSessionLock.builder()
                .deviceId(deviceId)
                .sessionId(sessionId)
                .patientId(patientId)
                .patientNameSnapshot(patientName)
                .holder(holder)
                .startedAt(now)
                .heartbeatAt(now)
                .expiresAt(now.plusSeconds(normalizedTtl()))
                .build();
    }

    private boolean isExpired(DeviceSessionLock lock, LocalDateTime now) {
        return lock.getExpiresAt() == null || lock.getExpiresAt().isBefore(now);
    }

    private long normalizedTtl() {
        return Math.max(ttlSeconds, 10);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(DeviceSessionLock.ZONE_CN);
    }

    public record LockAcquireResult(boolean acquired, DeviceSessionLock lock) {
    }
}
