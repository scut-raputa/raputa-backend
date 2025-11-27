package cn.scut.raputa.service;

import cn.scut.raputa.dto.CheckRecordDTO;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public CheckRecordVO create(CheckRecordDTO dto) {
        CheckRecord entity = mapDtoToEntity(dto);
        entity = checkRecordRepository.save(entity);
        return VoMappers.toCheckVO(entity);
    }

    @Override
    public List<CheckRecordVO> createBatch(List<CheckRecordDTO> dtos) {
        List<CheckRecord> list = new ArrayList<>();
        for (CheckRecordDTO dto : dtos) {
            list.add(mapDtoToEntity(dto));
        }
        List<CheckRecord> saved = checkRecordRepository.saveAll(list);
        return saved.stream().map(VoMappers::toCheckVO).toList();
    }

    private CheckRecord mapDtoToEntity(CheckRecordDTO dto) {
        if (dto == null) throw new IllegalArgumentException("payload cannot be null");
        if (isBlank(dto.getPatientId()) || isBlank(dto.getName()) || isBlank(dto.getStaff()))
            throw new IllegalArgumentException("patientId/name/staff are required");
        if (isBlank(dto.getResult()))
            throw new IllegalArgumentException("result is required");

        CheckResult cr = CheckResult.fromLabel(dto.getResult());
        if (cr == null)
            throw new IllegalArgumentException("invalid result: " + dto.getResult());

        CheckRecord e = new CheckRecord();
        e.setPatientId(dto.getPatientId());
        e.setName(dto.getName());
        e.setStaff(dto.getStaff());
        e.setResult(cr);

        if (isBlank(dto.getCheckTime())) {
            e.setCheckTime(LocalDateTime.now(CheckRecord.ZONE_CN));
        } else {
            try {
                // 解析 ISO-8601，本地时间；如果传的是 UTC 带 Z，可先 OffsetDateTime->LocalDateTime
                e.setCheckTime(LocalDateTime.parse(dto.getCheckTime()));
            } catch (DateTimeParseException ex) {
                // 兜底：使用服务器当前时间
                e.setCheckTime(LocalDateTime.now(CheckRecord.ZONE_CN));
            }
        }
        return e;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
