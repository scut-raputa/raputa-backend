package cn.scut.raputa.service;

import cn.scut.raputa.dto.CsvMappingRequestDTO;
import cn.scut.raputa.vo.TempFileUploadVO;
import org.springframework.web.multipart.MultipartFile;

public interface TempFileService {
    TempFileUploadVO upload(MultipartFile file);

    void setMapping(String tempId, CsvMappingRequestDTO req);

    void delete(String tempId);
}
