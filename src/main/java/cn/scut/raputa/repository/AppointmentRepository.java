package cn.scut.raputa.repository;

import cn.scut.raputa.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface AppointmentRepository extends JpaRepository<Appointment, String>, JpaSpecificationExecutor<Appointment> {
    long countByApptTime(LocalDate apptTime);
}
