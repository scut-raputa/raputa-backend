package cn.scut.raputa.repository;

import cn.scut.raputa.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppointmentRepository
        extends JpaRepository<Appointment, String>, JpaSpecificationExecutor<Appointment> {
}
