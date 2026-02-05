package cn.scut.raputa.repository;

import cn.scut.raputa.entity.CheckRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface CheckRecordRepository
        extends JpaRepository<CheckRecord, Long>, JpaSpecificationExecutor<CheckRecord> {

    /**
     * 查询指定时间范围内的检查记录
     */
    List<CheckRecord> findByCheckTimeBetween(LocalDateTime startTime, LocalDateTime endTime);
}
