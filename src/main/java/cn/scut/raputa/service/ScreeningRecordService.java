package cn.scut.raputa.service;

import cn.scut.raputa.dto.ScreeningArchiveDTO;
import cn.scut.raputa.dto.ScreeningRecordDTO;
import cn.scut.raputa.vo.ScreeningRecordVO;
import org.springframework.data.domain.Page;

public interface ScreeningRecordService {
    Page<ScreeningRecordVO> page(int page, int size, String appointmentId, String patientId, String name, String status);

    ScreeningRecordVO create(ScreeningRecordDTO dto);

    ScreeningRecordVO archive(String id, ScreeningArchiveDTO dto);
}
