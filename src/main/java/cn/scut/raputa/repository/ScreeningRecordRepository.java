package cn.scut.raputa.repository;

import cn.scut.raputa.entity.ScreeningRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ScreeningRecordRepository
        extends JpaRepository<ScreeningRecord, String>, JpaSpecificationExecutor<ScreeningRecord> {

    Optional<ScreeningRecord> findTopBySessionIdOrderByCreatedAtDesc(String sessionId);
}
