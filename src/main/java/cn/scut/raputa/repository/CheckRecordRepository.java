package cn.scut.raputa.repository;

import cn.scut.raputa.entity.CheckRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CheckRecordRepository
        extends JpaRepository<CheckRecord, Long>, JpaSpecificationExecutor<CheckRecord> {
}
