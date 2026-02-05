package cn.scut.raputa.repository;

import cn.scut.raputa.entity.AudioData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AudioDataRepository extends JpaRepository<AudioData, Long> {
    
    /**
     * 根据设备ID查询音频数据
     */
    Page<AudioData> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);
    
    /**
     * 根据设备ID和时间范围查询音频数据
     */
    @Query("SELECT a FROM AudioData a WHERE a.deviceId = :deviceId AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AudioData> findByDeviceIdAndTimestampRange(@Param("deviceId") String deviceId, 
                                                    @Param("startTime") Long startTime, 
                                                    @Param("endTime") Long endTime);
    
    /**
     * 根据设备ID删除音频数据
     */
    void deleteByDeviceId(String deviceId);
    
    /**
     * 统计设备的数据量
     */
    long countByDeviceId(String deviceId);
}






