package cn.scut.raputa.controller;

import cn.scut.raputa.dto.CsvMappingRequestDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.TempFileService;
import cn.scut.raputa.vo.TempFileUploadVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files/temp")
@RequiredArgsConstructor
public class TempFileController {

    private final TempFileService tempFileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TempFileUploadVO> upload(@RequestParam("file") MultipartFile file) {
        TempFileUploadVO vo = tempFileService.upload(file);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/{tempId}/mapping")
    public ApiResponse<Void> setMapping(@PathVariable String tempId, @Valid @RequestBody CsvMappingRequestDTO req) {
        tempFileService.setMapping(tempId, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{tempId}")
    public ApiResponse<Void> delete(@PathVariable String tempId) {
        tempFileService.delete(tempId);
        return ApiResponse.ok();
    }
}
