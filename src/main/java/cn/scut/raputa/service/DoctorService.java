package cn.scut.raputa.service;

import cn.scut.raputa.dto.DoctorDTO;
import cn.scut.raputa.vo.DoctorVO;
import org.springframework.data.domain.Page;

public interface DoctorService {
    Page<DoctorVO> page(int page, int size, String id, String name,
                        String department, String phone, String title);

    DoctorVO create(DoctorDTO dto);

    DoctorVO update(String id, DoctorDTO dto);

    void delete(String id);
}