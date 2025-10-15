package cn.scut.raputa.service;

import cn.scut.raputa.entity.CheckRecord;
import cn.scut.raputa.enums.CheckResult;
import cn.scut.raputa.repository.CheckRecordRepository;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.CheckRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.*;

@Service
@RequiredArgsConstructor
public class CheckRecordServiceImpl implements CheckRecordService {

    private final CheckRecordRepository checkRecordRepository;

    @Override
    public Page<CheckRecordVO> page(int page, int size, String id, String name, String staff, String result,
            String date) {
        Specification<CheckRecord> spec = Specification.<CheckRecord>unrestricted()
                .and(likeIfPresent("patientId", id))
                .and(likeIfPresent("name", name))
                .and(likeIfPresent("staff", staff))
                .and(resultIfPresent(result))
                .and(dateOrLastTwoWeeks("checkTime", date));

        Page<CheckRecord> pg = checkRecordRepository.findAll(
                spec,
                PageRequest.of(
                        Math.max(page - 1, 0),
                        Math.max(size, 1),
                        Sort.by(
                                Sort.Order.desc("checkTime"),
                                Sort.Order.desc("rid"))));
        return pg.map(VoMappers::toCheckVO);
    }

    private Specification<CheckRecord> likeIfPresent(String field, String q) {
        return (root, query, cb) -> (q == null || q.isEmpty()) ? null : cb.like(root.get(field), "%" + q + "%");
    }

    private Specification<CheckRecord> resultIfPresent(String r) {
        return (root, query, cb) -> {
            if (r == null || r.isEmpty())
                return null;
            CheckResult cr = CheckResult.fromLabel(r);
            return (cr == null) ? null : cb.equal(root.get("result"), cr);
        };
    }

    private Specification<CheckRecord> dateOrLastTwoWeeks(String field, String d) {
        return (root, query, cb) -> {
            ZoneId zone = ZoneId.of("Asia/Shanghai");
            if (d != null && !d.isEmpty()) {
                LocalDate day = LocalDate.parse(d);
                LocalDateTime start = day.atStartOfDay();
                LocalDateTime end = start.plusDays(1);
                return cb.between(root.get(field), start, end);
            } else {
                LocalDate today = LocalDate.now(zone);
                LocalDateTime start = today.minusDays(13).atStartOfDay();
                LocalDateTime end = today.plusDays(1).atStartOfDay();
                return cb.between(root.get(field), start, end);
            }
        };
    }
}
