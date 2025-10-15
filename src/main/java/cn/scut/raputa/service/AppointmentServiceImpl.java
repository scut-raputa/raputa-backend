package cn.scut.raputa.service;

import cn.scut.raputa.entity.Appointment;
import cn.scut.raputa.repository.AppointmentRepository;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.AppointmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.*;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;

    @Override
    public Page<AppointmentVO> page(int page, int size, String id, String name, String dept, String date) {
        if (date == null || date.isEmpty()) {
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
            date = today.toString();
        }

        Specification<Appointment> spec = Specification.<Appointment>unrestricted()
                .and(likeIfPresent("id", id))
                .and(likeIfPresent("name", name))
                .and(likeIfPresent("dept", dept))
                .and(dateIfPresent("apptTime", date));

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
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            return cb.between(root.get(field), start, end);
        };
    }
}
