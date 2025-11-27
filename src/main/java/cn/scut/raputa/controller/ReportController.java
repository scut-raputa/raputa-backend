// cn/scut/raputa/controller/ReportController.java
package cn.scut.raputa.controller;

import cn.scut.raputa.entity.PatientFile;
import cn.scut.raputa.repository.PatientFileRepository;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.PatientFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final PatientFileRepository patientFileRepository;
    private final PatientFileService patientFileService;

    // 复用 CSV 根目录，保持所有文件在一棵树下
    @Value("${raputa.data.dir:D:/health_plat_bk/data}")
    private String dataRoot;

    private static final DateTimeFormatter SESSION_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @PostMapping("/upload")
    public ApiResponse<String> uploadReportPdf(
            @RequestParam("patientId") String patientId,
            @RequestParam("patientName") String patientName,
            @RequestPart("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "空文件，无法保存报告");
        }
        if (patientId == null || patientId.isBlank()) {
            return ApiResponse.error(400, "patientId 不能为空");
        }

        try {
            // 1. 解析/推断会话目录
            Path sessionFolder = resolveSessionFolder(patientId, patientName);

            if (!Files.exists(sessionFolder)) {
                Files.createDirectories(sessionFolder);
            }

            // 2. 生成报告文件名，例如 report_20251126_203011.pdf
            String ts = LocalDateTime.now().format(SESSION_TS);
            String filename = "report_" + ts + ".pdf";
            Path dest = sessionFolder.resolve(filename);

            // 3. 保存 PDF 文件
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }

            // 4. 写入 PatientFile
            patientFileService.record(
                    patientId,
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

    /**
     * 根据该患者最近一次的 PatientFile 记录推断会话目录：
     * - 有记录：用最近一条文件的父目录（这样 pdf 跟 csv/wav 在同一会话目录）
     * - 没有记录：按 “患者id_患者姓名_时间戳” 新建一个目录
     */
    private Path resolveSessionFolder(String patientId, String patientName) {
        Optional<PatientFile> latestOpt =
                patientFileRepository.findTop1ByIdPatientIdOrderBySavedAtDesc(patientId);

        if (latestOpt.isPresent()) {
            Path p = Path.of(latestOpt.get().getFilePath()).normalize();
            Path parent = p.getParent();
            if (parent != null) {
                return parent;
            }
        }

        // 没有任何记录，按 CsvDataService 的规则造一个新会话目录
        String ts = LocalDateTime.now().format(SESSION_TS);
        String safeName = sanitize(patientName);
        String folderName = String.format("%s_%s_%s", patientId, safeName, ts);
        return Path.of(dataRoot, folderName);
    }

    // 直接把 CsvDataService 里的 sanitize 复制一份保持一致
    private static String sanitize(String s) {
        if (s == null) return "unknown";
        String t = s.trim();
        if (t.isEmpty()) return "unknown";
        t = t.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_");
        t = t.replaceAll("\\s+", "_");
        if (t.length() > 80) t = t.substring(0, 80);
        return t;
    }
}
