package cn.scut.raputa.service;

import cn.scut.raputa.entity.Model;
import cn.scut.raputa.repository.ModelRepository;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.ModelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.*;

@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final ModelRepository modelRepository;

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

    private Specification<Model> likeIfPresent(String field, String q) {
        return (root, query, cb) -> (q == null || q.isEmpty()) ? null : cb.like(root.get(field), "%" + q + "%");
    }

    private Specification<Model> dateIfPresent(String field, String d) {
        return (root, query, cb) -> {
            if (d == null || d.isEmpty())
                return null;
            LocalDate day = LocalDate.parse(d);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            return cb.between(root.get(field), start, end);
        };
    }
}
