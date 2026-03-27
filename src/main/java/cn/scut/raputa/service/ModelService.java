package cn.scut.raputa.service;

import cn.scut.raputa.dto.ModelDTO;
import cn.scut.raputa.vo.ModelStatsVO;
import cn.scut.raputa.vo.ModelVO;
import org.springframework.data.domain.Page;

public interface ModelService {
    Page<ModelVO> page(int page, int size, String id, String func, String name, String uploader, String date);

    ModelStatsVO stats();

    ModelVO create(ModelDTO dto);

    ModelVO update(String id, ModelDTO dto);

    void delete(String id);
}