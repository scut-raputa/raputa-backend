package cn.scut.raputa.service;

import cn.scut.raputa.vo.RuntimeModelVO;
import cn.scut.raputa.vo.RuntimeSummaryVO;

import java.util.List;

public interface ModelService {
    List<RuntimeModelVO> runtimeList(String name, String taskType, Boolean loaded, Boolean available);

    RuntimeSummaryVO runtimeSummary();
}
