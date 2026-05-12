package cn.scut.raputa.repository;

import cn.scut.raputa.entity.Device;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, String>, JpaSpecificationExecutor<Device> {

    @Query("SELECT DISTINCT d.storageLocation FROM Device d " +
           "WHERE d.storageLocation IS NOT NULL AND d.storageLocation <> '' " +
           "ORDER BY d.storageLocation")
    List<String> findDistinctStorageLocations();

    List<Device> findByEnabledTrueOrderByUpdatedAtDesc();

    Optional<Device> findFirstByEnabledTrueAndStatusOrderByUpdatedAtDesc(String status);

    Optional<Device> findFirstByIp(String ip);

    Optional<Device> findFirstByHardwareId(String hardwareId);
}
