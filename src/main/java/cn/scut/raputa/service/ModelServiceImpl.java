package cn.scut.raputa.service;

import cn.scut.raputa.vo.RuntimeModelVO;
import cn.scut.raputa.vo.RuntimeSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final InferenceRuntimeService inferenceRuntimeService;

    @Override
    public List<RuntimeModelVO> runtimeList(String name, String taskType, Boolean loaded, Boolean available) {
        Stream<RuntimeModelVO> stream = inferenceRuntimeService.getRuntimeModels().stream();

        if (name != null && !name.isBlank()) {
            String keyword = name.toLowerCase(Locale.ROOT);
            stream = stream.filter(item -> item.getName() != null && item.getName().toLowerCase(Locale.ROOT).contains(keyword));
        }
        if (taskType != null && !taskType.isBlank()) {
            String keyword = taskType.toLowerCase(Locale.ROOT);
            stream = stream.filter(item -> item.getTaskType() != null && item.getTaskType().toLowerCase(Locale.ROOT).contains(keyword));
        }
        if (loaded != null) {
            stream = stream.filter(item -> item.isLoaded() == loaded);
        }
        if (available != null) {
            stream = stream.filter(item -> (item.isLoaded() && item.isServiceLive() && item.isServiceReady()) == available);
        }

        return stream.toList();
    }

    @Override
    public RuntimeSummaryVO runtimeSummary() {
        return inferenceRuntimeService.getRuntimeSummary();
    }
}
