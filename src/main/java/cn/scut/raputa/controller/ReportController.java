package cn.scut.raputa.controller;

import cn.scut.raputa.entity.CaptureSession;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.CaptureSessionRepository;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.FileStorageService;
import cn.scut.raputa.service.PatientFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final CaptureSessionRepository captureSessionRepository;
    private final PatientFileService patientFileService;
    private final FileStorageService fileStorageService;

    private static final DateTimeFormatter SESSION_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @PostMapping("/upload")
    public ApiResponse<String> uploadReportPdf(
            @RequestParam("patientId") String patientId,
            @RequestParam("patientName") String patientName,
            @RequestParam("sessionId") String sessionId,
            @RequestPart("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "空文件，无法保存报告");
        }
        if (patientId == null || patientId.isBlank()) {
            return ApiResponse.error(400, "patientId 不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return ApiResponse.error(400, "sessionId 不能为空");
        }

        try {
            CaptureSession session = captureSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BizException(404, "会话不存在: " + sessionId));

            if (session.getPatientId() != null && !session.getPatientId().isBlank()
                    && !session.getPatientId().equals(patientId)) {
                return ApiResponse.error(400, "patientId 与 sessionId 不匹配");
            }

            Path sessionFolder = fileStorageService.resolveSessionDir(session.getSessionDir());

            if (!Files.exists(sessionFolder)) {
                Files.createDirectories(sessionFolder);
            }

            String ts = LocalDateTime.now().format(SESSION_TS);
            String filename = "report_" + ts + ".pdf";
            Path dest = sessionFolder.resolve(filename);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }

            patientFileService.record(
                    patientId,
                    sessionId,
                    dest.toAbsolutePath().toString(),
                    "pdf",
                    LocalDateTime.now()
            );

            log.info("保存并登记报告 PDF：patientId={}, path={}", patientId, dest);
            return ApiResponse.ok(dest.toString());
        } catch (Exception e) {
            log.error("上传报告 PDF 失败", e);
            return ApiResponse.error(500, "上传报告失败：" + e.getMessage());
        }
    }
}
