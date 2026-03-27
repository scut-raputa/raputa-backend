package cn.scut.raputa.service;

import cn.scut.raputa.dto.ModelDTO;
import cn.scut.raputa.entity.Model;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.ModelRepository;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.ModelStatsVO;
import cn.scut.raputa.vo.ModelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final ModelRepository modelRepository;
    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    @Override
    public Page<ModelVO> page(int page, int size, String id, String func, String name, String uploader, String date) {
        Specification<Model> spec = Specification.<Model>unrestricted()
                .and(likeIfPresent("id", id))
                .and(likeIfPresent("func", func))
                .and(likeIfPresent("name", name))
                .and(likeIfPresent("uploader", uploader))
                .and(dateIfPresent("uploadTime", date));

        Page<Model> pg = modelRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1),
                        Sort.by(Sort.Order.desc("uploadTime"),
                                Sort.Order.desc("id"))));
        return pg.map(VoMappers::toModelVO);
    }

    @Override
    public ModelStatsVO stats() {
        long totalCount = modelRepository.count();

        LocalDate today = LocalDate.now(ZONE_CN);
        LocalDate thisMonday = today.with(DayOfWeek.MONDAY);
        LocalDateTime thisWeekStart = thisMonday.atStartOfDay();
        LocalDateTime thisWeekEnd = today.plusDays(1).atStartOfDay();
        long weekNewCount = modelRepository.countByUploadTimeBetween(thisWeekStart, thisWeekEnd);

        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDateTime lastWeekStart = lastMonday.atStartOfDay();
        LocalDateTime lastWeekEnd = thisMonday.atStartOfDay();
        long lastWeekNewCount = modelRepository.countByUploadTimeBetween(lastWeekStart, lastWeekEnd);

        String topUploader = null;
        long topUploaderCount = 0;
        double topUploaderRatio = 0;
        List<Object[]> uploaderStats = modelRepository.findUploaderStats(PageRequest.of(0, 1));
        if (!uploaderStats.isEmpty()) {
            topUploader = (String) uploaderStats.get(0)[0];
            topUploaderCount = (long) uploaderStats.get(0)[1];
            topUploaderRatio = totalCount > 0 ? (double) topUploaderCount / totalCount : 0;
        }

        return ModelStatsVO.builder()
                .totalCount(totalCount)
                .weekNewCount(weekNewCount)
                .lastWeekNewCount(lastWeekNewCount)
                .topUploader(topUploader)
                .topUploaderCount(topUploaderCount)
                .topUploaderRatio(topUploaderRatio)
                .build();
    }

    @Override
    public ModelVO create(ModelDTO dto) {
        Model model = new Model();
        model.setId(generateId());
        applyDto(model, dto);
        return VoMappers.toModelVO(modelRepository.save(model));
    }

    @Override
    public ModelVO update(String id, ModelDTO dto) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "模型不存在"));
        applyDto(model, dto);
        return VoMappers.toModelVO(modelRepository.save(model));
    }

    @Override
    public void delete(String id) {
        if (!modelRepository.existsById(id)) {
            throw new BizException(404, "模型不存在");
        }
        modelRepository.deleteById(id);
    }

    private void applyDto(Model model, ModelDTO dto) {
        model.setFunc(dto.getFunc());
        model.setName(dto.getName());
        model.setUploadTime(parseUploadTime(dto.getUploadTime()));
        model.setUploader(dto.getUploader());
        model.setRemark(dto.getRemark());
        model.setLocation(dto.getLocation() != null ? dto.getLocation() : "");
        model.setAccuracy(dto.getAccuracy());
        model.setSensitivity(dto.getSensitivity());
        model.setSpecificity(dto.getSpecificity());
    }

    private LocalDateTime parseUploadTime(String s) {
        if (s == null || s.isBlank()) return LocalDateTime.now(ZONE_CN);
        try { return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); } catch (Exception ignored) {}
        return LocalDateTime.now(ZONE_CN);
    }

    private String generateId() {
        String prefix = "M" + LocalDate.now(ZONE_CN).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int attempt = 0; attempt < 10; attempt++) {
            String id = prefix + String.format("%06d", (int) (Math.random() * 1_000_000));
            if (!modelRepository.existsById(id)) return id;
        }
        throw new BizException(500, "生成模型编号失败，请重试");
    }

    private Specification<Model> likeIfPresent(String field, String q) {
        return (root, query, cb) -> (q == null || q.isEmpty()) ? null : cb.like(root.get(field), "%" + q + "%");
    }

    private Specification<Model> dateIfPresent(String field, String d) {
        return (root, query, cb) -> {
            if (d == null || d.isEmpty()) return null;
            LocalDate day = LocalDate.parse(d);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            return cb.between(root.get(field), start, end);
        };
    }
}