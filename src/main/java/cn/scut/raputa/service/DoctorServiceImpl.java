package cn.scut.raputa.service;

import cn.scut.raputa.dto.DoctorDTO;
import cn.scut.raputa.entity.Doctor;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.DoctorRepository;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.DoctorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;

    @Override
    public Page<DoctorVO> page(int page, int size, String id, String name,
                               String department, String phone, String title) {
        Specification<Doctor> spec = Specification.<Doctor>unrestricted()
                .and(likeIfPresent("id", id))
                .and(likeIfPresent("name", name))
                .and(likeIfPresent("department", department))
                .and(likeIfPresent("phone", phone))
                .and(eqIfPresent("title", title));

        Page<Doctor> pg = doctorRepository.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1),
                        Sort.by(Sort.Order.asc("id"))));
        return pg.map(VoMappers::toDoctorVO);
    }

    @Override
    public DoctorVO create(DoctorDTO dto) {
        Doctor doctor = new Doctor();
        doctor.setId(generateId());
        applyDto(doctor, dto);
        return VoMappers.toDoctorVO(doctorRepository.save(doctor));
    }

    @Override
    public DoctorVO update(String id, DoctorDTO dto) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "医生不存在"));
        applyDto(doctor, dto);
        return VoMappers.toDoctorVO(doctorRepository.save(doctor));
    }

    @Override
    public void delete(String id) {
        if (!doctorRepository.existsById(id))
            throw new BizException(404, "医生不存在");
        doctorRepository.deleteById(id);
    }

    private void applyDto(Doctor doctor, DoctorDTO dto) {
        doctor.setName(dto.getName());
        doctor.setDepartment(dto.getDepartment());
        doctor.setTitle(dto.getTitle());
        doctor.setPhone(dto.getPhone());
    }

    private String generateId() {
        for (int i = 1; i <= 9999; i++) {
            String id = "D" + String.format("%03d", i);
            if (!doctorRepository.existsById(id)) return id;
        }
        throw new BizException(500, "生成工号失败");
    }

    private Specification<Doctor> likeIfPresent(String field, String q) {
        return (root, query, cb) ->
                (q == null || q.isEmpty()) ? null : cb.like(root.get(field), "%" + q + "%");
    }

    private Specification<Doctor> eqIfPresent(String field, String q) {
        return (root, query, cb) ->
                (q == null || q.isEmpty()) ? null : cb.equal(root.get(field), q);
    }
}