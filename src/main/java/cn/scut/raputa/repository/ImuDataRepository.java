package cn.scut.raputa.repository;

import cn.scut.raputa.entity.ImuData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ImuDataRepository extends JpaRepository<ImuData, Long> {
    
    /**
     * 根据设备ID查询IMU数据
     */
    Page<ImuData> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);
    
    /**
     * 根据设备ID和时间范围查询IMU数据
     */
    @Query("SELECT i FROM ImuData i WHERE i.deviceId = :deviceId AND i.timestamp BETWEEN :startTime AND :endTime ORDER BY i.timestamp DESC")
    List<ImuData> findByDeviceIdAndTimestampRange(@Param("deviceId") String deviceId, 
                                                  @Param("startTime") Long startTime, 
                                                  @Param("endTime") Long endTime);
    
    /**
     * 根据设备ID删除IMU数据
     */
    void deleteByDeviceId(String deviceId);
    
    /**
     * 统计设备的数据量
     */
    long countByDeviceId(String deviceId);
}






