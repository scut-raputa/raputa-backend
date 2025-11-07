package cn.scut.raputa.repository;

import cn.scut.raputa.entity.GasData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GasDataRepository extends JpaRepository<GasData, Long> {
    
    /**
     * 根据设备ID查询气体数据
     */
    Page<GasData> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);
    
    /**
     * 根据设备ID和时间范围查询气体数据
     */
    @Query("SELECT g FROM GasData g WHERE g.deviceId = :deviceId AND g.timestamp BETWEEN :startTime AND :endTime ORDER BY g.timestamp DESC")
    List<GasData> findByDeviceIdAndTimestampRange(@Param("deviceId") String deviceId, 
                                                  @Param("startTime") Long startTime, 
                                                  @Param("endTime") Long endTime);
    
    /**
     * 根据设备ID删除气体数据
     */
    void deleteByDeviceId(String deviceId);
    
    /**
     * 统计设备的数据量
     */
    long countByDeviceId(String deviceId);
}






