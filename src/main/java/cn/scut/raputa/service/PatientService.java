package cn.scut.raputa.service;

import cn.scut.raputa.dto.PatientCreateDTO;
import cn.scut.raputa.vo.PatientVO;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface PatientService {

    Page<PatientVO> page(
            int page, int size,
            String id, String name, String dept,
            String gender, String admit, Boolean checked,
            LocalDate onsetDate, String pastHistory, String bedNumber, String course);

    PatientVO create(PatientCreateDTO dto);

    void deleteById(String id);

    PatientVO update(String id, PatientCreateDTO dto);
}
