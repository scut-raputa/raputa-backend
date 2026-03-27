package cn.scut.raputa.repository;

import cn.scut.raputa.entity.Device;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, String>, JpaSpecificationExecutor<Device> {

    @Query("SELECT DISTINCT d.storageLocation FROM Device d " +
           "WHERE d.storageLocation IS NOT NULL AND d.storageLocation <> '' " +
           "ORDER BY d.storageLocation")
    List<String> findDistinctStorageLocations();
}