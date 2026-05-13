package cn.scut.raputa.service;

import cn.scut.raputa.dto.AppointmentCreateDTO;
import cn.scut.raputa.dto.AppointmentUpdateDTO;
import cn.scut.raputa.entity.Appointment;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.AppointmentRepository;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.AppointmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;

    @Override
    public Page<AppointmentVO> page(int page, int size, String id, String name, String dept, String date, String status) {
        if (date == null || date.isEmpty()) {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            date = today.toString();
        }

        Specification<Appointment> spec = Specification.<Appointment>unrestricted()
                .and(likeIfPresent("id", id))
                .and(likeIfPresent("name", name))
                .and(likeIfPresent("dept", dept))
                .and(dateIfPresent("apptTime", date))
                .and(statusIfPresent(status));

        Page<Appointment> pg = appointmentRepository.findAll(
                spec,
                PageRequest.of(
                        Math.max(page - 1, 0),
                        Math.max(size, 1),
                        Sort.by(
                                Sort.Order.desc("apptTime"),
                                Sort.Order.desc("id"))));
        return pg.map(VoMappers::toAppointmentVO);
    }

    private Specification<Appointment> likeIfPresent(String field, String q) {
        return (root, query, cb) -> (q == null || q.isEmpty()) ? null : cb.like(root.get(field), "%" + q + "%");
    }

    private Specification<Appointment> dateIfPresent(String field, String d) {
        return (root, query, cb) -> {
            if (d == null || d.isEmpty())
                return null;
            LocalDate day = LocalDate.parse(d);
            // 修复：直接使用 LocalDate 进行相等比较
            return cb.equal(root.get(field), day);
        };
    }

    private Specification<Appointment> statusIfPresent(String status) {
        return (root, query, cb) -> {
            String normalized = status == null ? "" : status.trim().toUpperCase();
            if ("ALL".equals(normalized)) {
                return null;
            }
            if (normalized.isEmpty() || "PENDING".equals(normalized)) {
                return cb.or(cb.isNull(root.get("status")), cb.equal(root.get("status"), "PENDING"));
            }
            return cb.equal(root.get("status"), normalized);
        };
    }


    @Override
    public AppointmentVO create(AppointmentCreateDTO dto) {
        validateApptTime(dto.getTime());

        // 修复：使用 UUID 确保唯一性，避免并发问题
        String outpatientId = generateUniqueId();

        Appointment a = new Appointment();
        a.setId(outpatientId);
        a.setName(dto.getName() == null ? null : dto.getName().trim());
        a.setGender(dto.getGender() == null ? null : dto.getGender().trim());
        a.setIdCard(dto.getIdCard() == null ? null : dto.getIdCard().trim().toUpperCase());
        a.setPhone(dto.getPhone() == null ? null : dto.getPhone().trim());
        a.setDept(dto.getDept() == null ? null : dto.getDept().trim());
        a.setApptTime(dto.getTime());
        a.setStatus("PENDING");

        Appointment saved = appointmentRepository.save(a);
        return VoMappers.toAppointmentVO(saved);

    }

    // 添加新方法：生成唯一 ID
    private String generateUniqueId() {
        LocalDate today = LocalDate.now();
        String datePart = today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        // 使用 UUID 的一部分确保唯一性
        String uniquePart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "O" + datePart + uniquePart;
    }


    @Override
    public AppointmentVO update(String id, AppointmentUpdateDTO dto) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "预约不存在"));
        if (dto.getPhone() != null) a.setPhone(dto.getPhone().trim());
        if (dto.getDept() != null) a.setDept(dto.getDept().trim());
        if (dto.getTime() != null) {
            validateApptTime(dto.getTime());
            a.setApptTime(dto.getTime());
        }

        Appointment saved = appointmentRepository.save(a);
        return VoMappers.toAppointmentVO(saved);
    }

    @Override
    public void deleteById(String id) {
        if (!appointmentRepository.existsById(id)) {
            throw new BizException(404, "预约不存在");
        }
        try {
            appointmentRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(409, "该预约存在关联记录，无法删除");
        }
    }

    private void validateApptTime(LocalDate apptTime) {
        if (apptTime == null) {
            throw new BizException(400, "预约时间不能为空");
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (apptTime.isBefore(today)) {
            throw new BizException(400, "预约时间必须为今天或之后");
        }
    }
}
