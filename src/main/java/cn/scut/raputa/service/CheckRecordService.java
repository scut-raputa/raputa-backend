package cn.scut.raputa.service;

import cn.scut.raputa.dto.CheckRecordDTO;
import cn.scut.raputa.vo.CheckRecordVO;

import java.util.List;

import org.springframework.data.domain.Page;

public interface CheckRecordService {
    Page<CheckRecordVO> page(int page, int size, String id, String name, String staff, String result, String date);

    CheckRecordVO create(CheckRecordDTO dto);

    List<CheckRecordVO> createBatch(List<CheckRecordDTO> dtos);
}
