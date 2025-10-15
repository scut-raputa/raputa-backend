package cn.scut.raputa.service;

import cn.scut.raputa.dto.PatientCreateDTO;
import cn.scut.raputa.entity.Patient;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.PatientRepository;
import cn.scut.raputa.utils.VoMappers;
import cn.scut.raputa.vo.PatientVO;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;

    @Override
    public Page<PatientVO> page(int page, int size,
            String id, String name, String dept, String address,
            String gender, String admit, Boolean checked) {

        Specification<Patient> spec = Specification.<Patient>unrestricted()
                .and(likeIfPresent("id", id))
                .and(likeIfPresent("name", name))
                .and(likeIfPresent("dept", dept))
                .and(likeIfPresent("address", address))
                .and(eqIfPresent("gender", gender))
                .and(eqIfPresent("admit", parseDateOrNull(admit)))
                .and(boolIfPresent("checked", checked));

        Page<Patient> pg = patientRepository.findAll(
                spec,
                PageRequest.of(
                        Math.max(page - 1, 0),
                        Math.max(size, 1),
                        Sort.by(
                                Sort.Order.desc("admit"),
                                Sort.Order.desc("id"))));

        return pg.map(VoMappers::toPatientVO);
    }

    @Override
    @Transactional
    public PatientVO create(PatientCreateDTO dto) {
        if (!"男".equals(dto.getGender()) && !"女".equals(dto.getGender())) {
            throw new BizException(400, "性别仅支持：男 / 女");
        }

        LocalDate today = LocalDate.now();
        String datePart = today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        long seq = patientRepository.countByAdmit(today) + 1;
        String genId = "P" + datePart + String.format("%05d", seq);

        Patient p = new Patient();
        p.setId(genId);
        p.setName(dto.getName().trim());
        p.setGender(dto.getGender());
        p.setBirth(dto.getBirth());
        p.setDept(dto.getDept().trim());
        p.setAddress(dto.getAddress().trim());
        p.setChecked(Boolean.TRUE.equals(dto.getChecked()));
        p.setAdmit(today);

        Patient saved = patientRepository.save(p);

        return cn.scut.raputa.utils.VoMappers.toPatientVO(saved);
    }

    @Override
    @Transactional
    public PatientVO updateDeptAndAddress(String id, String dept, String address) {
        Patient p = patientRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "患者不存在"));
        p.setDept(dept.trim());
        p.setAddress(address.trim());
        p = patientRepository.save(p);
        return VoMappers.toPatientVO(p);
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        if (!patientRepository.existsById(id)) {
            throw new BizException(404, "患者不存在");
        }
        try {
            patientRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(409, "该患者存在关联记录，无法删除");
        }
    }

    private Specification<Patient> likeIfPresent(String field, String q) {
        return (root, query, cb) -> (q == null || q.isEmpty()) ? null : cb.like(root.get(field), "%" + q + "%");
    }

    private <T> Specification<Patient> eqIfPresent(String field, T v) {
        return (root, query, cb) -> (v == null) ? null : cb.equal(root.get(field), v);
    }

    private Specification<Patient> boolIfPresent(String field, Boolean v) {
        return (root, query, cb) -> (v == null) ? null : (v ? cb.isTrue(root.get(field)) : cb.isFalse(root.get(field)));
    }

    private LocalDate parseDateOrNull(String d) {
        return (d == null || d.isEmpty()) ? null : LocalDate.parse(d);
    }

    public Patient save(Patient p) {
        if (p.getId() == null || p.getId().isBlank()) {
            String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            long countToday = patientRepository.countByAdmit(LocalDate.now());
            String id = "P" + datePart + String.format("%05d", countToday + 1);
            p.setId(id);
        }
        return patientRepository.save(p);
    }
}
