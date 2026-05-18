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
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private static final int[] ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECKSUM = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    @Override
    public Page<PatientVO> page(int page, int size,
                                String id, String name, String dept,
                                String gender, String admit, Boolean checked,
                                LocalDate onsetDate, String pastHistory, String bedNumber, String course) {

        Specification<Patient> spec = Specification.<Patient>unrestricted()
                .and(likeIfPresent("id", id))
                .and(likeIfPresent("name", name))
                .and(likeIfPresent("dept", dept))
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
        String gender = dto.getGender() == null ? null : dto.getGender().trim();
        if (!"男".equals(gender) && !"女".equals(gender)) {
            throw new BizException(400, "性别仅支持：男 / 女");
        }

        String idCard = normalizeIdCard(dto.getIdCard());
        if (!isValidMainlandIdCard(idCard)) {
            throw new BizException(400, "请输入有效的中国大陆居民身份证号码");
        }
        if (!gender.equals(inferGenderFromIdCard(idCard))) {
            throw new BizException(400, "性别与身份证信息不一致");
        }

        LocalDate today = LocalDate.now();
        String datePart = today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        long seq = patientRepository.countByAdmit(today) + 1;

        String genId = "P" + datePart + String.format("%05d", seq);
        String outpatientId = "O" + datePart + String.format("%05d", seq);

        Patient p = new Patient();
        p.setId(genId);
        p.setOutpatientId(outpatientId);
        p.setName(dto.getName().trim());
        p.setGender(gender);
        p.setDept(dto.getDept().trim());
        p.setChecked(false);
        p.setAdmit(today);
        p.setCourse(dto.getCourse().trim());
        p.setOnsetDate(dto.getOnsetDate());
        p.setIdCard(idCard);
        p.setPastHistory(dto.getPastHistory().trim());
        p.setBedNumber(dto.getBedNumber().trim());

        Patient saved = patientRepository.save(p);

        return cn.scut.raputa.utils.VoMappers.toPatientVO(saved);
    }

    @Override
    @Transactional
    public PatientVO update(String id,PatientCreateDTO dto) {
        Patient p = patientRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "患者不存在"));

        String gender = dto.getGender() == null ? null : dto.getGender().trim();
        if (!"男".equals(gender) && !"女".equals(gender)) {
            throw new BizException(400, "性别仅支持：男 / 女");
        }

        String idCard = normalizeIdCard(dto.getIdCard());
        if (!isValidMainlandIdCard(idCard)) {
            throw new BizException(400, "请输入有效的中国大陆居民身份证号码");
        }
        if (!gender.equals(inferGenderFromIdCard(idCard))) {
            throw new BizException(400, "性别与身份证信息不一致");
        }

        String name = dto.getName() == null ? null : dto.getName().trim();
        if (!Objects.equals(name, p.getName())
                || !Objects.equals(gender, p.getGender())
                || !Objects.equals(idCard, normalizeIdCard(p.getIdCard()))) {
            throw new BizException(400, "姓名、性别和身份证号码已建档，不能在此处修改");
        }

        p.setDept(dto.getDept().trim());
        p.setOnsetDate(dto.getOnsetDate());
        p.setPastHistory(dto.getPastHistory() == null ? null : dto.getPastHistory().trim());
        p.setBedNumber(dto.getBedNumber() == null ? null : dto.getBedNumber().trim());
        p.setCourse(dto.getCourse() == null ? null : dto.getCourse().trim());

        Patient updated = patientRepository.save(p);
        return VoMappers.toPatientVO(updated);
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
        LocalDate today = LocalDate.now();
        String datePart = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long countToday = patientRepository.countByAdmit(today) + 1;

        if (p.getId() == null || p.getId().isBlank()) {
            String id = "P" + datePart + String.format("%05d", countToday);
            p.setId(id);
        }

        if (p.getOutpatientId() == null || p.getOutpatientId().isBlank()) {
            String outpatientId = "M" + datePart + String.format("%05d", countToday);
            p.setOutpatientId(outpatientId);
        }

        return patientRepository.save(p);
    }

    private String normalizeIdCard(String idCard) {
        return idCard == null ? null : idCard.trim().toUpperCase();
    }

    private String inferGenderFromIdCard(String idCard) {
        int seqCode = idCard.charAt(16) - '0';
        return seqCode % 2 == 1 ? "男" : "女";
    }

    private boolean isValidMainlandIdCard(String idCard) {
        if (idCard == null || !idCard.matches("^\\d{6}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$")) {
            return false;
        }

        String upper = idCard.toUpperCase();
        String birthday = upper.substring(6, 14);
        try {
            LocalDate.parse(birthday, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException ex) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (upper.charAt(i) - '0') * ID_CARD_WEIGHTS[i];
        }
        char expected = ID_CARD_CHECKSUM[sum % 11];
        return upper.charAt(17) == expected;
    }

}
