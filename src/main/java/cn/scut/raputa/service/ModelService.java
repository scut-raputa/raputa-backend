package cn.scut.raputa.service;

import cn.scut.raputa.dto.ModelDTO;
import cn.scut.raputa.vo.RuntimeModelVO;
import cn.scut.raputa.vo.RuntimeSummaryVO;
import cn.scut.raputa.vo.ModelStatsVO;
import cn.scut.raputa.vo.ModelVO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ModelService {
    Page<ModelVO> page(int page, int size, String id, String func, String name, String uploader, String date);

    List<RuntimeModelVO> runtimeList(String name, String taskType, Boolean loaded, Boolean available);

    RuntimeSummaryVO runtimeSummary();

    ModelStatsVO stats();

    ModelVO create(ModelDTO dto);

    ModelVO update(String id, ModelDTO dto);

    void delete(String id);
}