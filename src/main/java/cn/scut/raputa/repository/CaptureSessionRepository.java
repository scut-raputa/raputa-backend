package cn.scut.raputa.repository;

import cn.scut.raputa.entity.CaptureSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CaptureSessionRepository extends JpaRepository<CaptureSession, String> {

    Optional<CaptureSession> findTop1ByDeviceIdAndStatusInOrderByCreatedAtDesc(String deviceId, Collection<String> status);

    List<CaptureSession> findByStartedAtBeforeAndDeviceIdIsNotNull(LocalDateTime endTime);
}
