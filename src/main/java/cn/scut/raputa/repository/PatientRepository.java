package cn.scut.raputa.repository;

import cn.scut.raputa.entity.Patient;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatientRepository extends JpaRepository<Patient, String>, JpaSpecificationExecutor<Patient> {
    long countByAdmit(LocalDate admit);

    boolean existsByOutpatientId(String outpatientId);
}
