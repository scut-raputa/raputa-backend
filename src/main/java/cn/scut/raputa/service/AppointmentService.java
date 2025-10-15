package cn.scut.raputa.service;

import cn.scut.raputa.vo.AppointmentVO;
import org.springframework.data.domain.Page;

public interface AppointmentService {
    Page<AppointmentVO> page(int page, int size, String id, String name, String dept, String date);
}
