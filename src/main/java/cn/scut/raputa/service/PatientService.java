package cn.scut.raputa.service;

import cn.scut.raputa.dto.PatientCreateDTO;
import cn.scut.raputa.vo.PatientVO;
import org.springframework.data.domain.Page;

public interface PatientService {

    Page<PatientVO> page(
            int page, int size,
            String id, String name, String dept, String address,
            String gender, String admit, Boolean checked);

    PatientVO create(PatientCreateDTO dto);

    PatientVO updateDeptAndAddress(String id, String dept, String address);

    void deleteById(String id);
}
