package cn.scut.raputa.repository;

import cn.scut.raputa.entity.DeviceSessionLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface DeviceSessionLockRepository extends JpaRepository<DeviceSessionLock, String> {

    List<DeviceSessionLock> findAllByDeviceIdInAndExpiresAtAfter(Collection<String> deviceIds, LocalDateTime now);

    boolean existsByHolderAndExpiresAtAfter(String holder, LocalDateTime now);

    List<DeviceSessionLock> findByExpiresAtBefore(LocalDateTime now);
}
