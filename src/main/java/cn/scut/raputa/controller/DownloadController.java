// cn/scut/raputa/controller/DownloadController.java
package cn.scut.raputa.controller;

import cn.scut.raputa.entity.PatientFile;
import cn.scut.raputa.service.PatientFileService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/download")
@RequiredArgsConstructor
@Slf4j
public class DownloadController {

    private final PatientFileService patientFileService;

    /** 1) 单个文件下载（前端传绝对路径 path） */
    @GetMapping("/file")
    public void downloadSingle(@RequestParam("path") String path, HttpServletResponse resp) throws IOException {
        Path p = Paths.get(path).normalize();
        if (!Files.exists(p) || Files.isDirectory(p)) {
            resp.setStatus(404);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"message\":\"file not found\"}");
            return;
        }
        String filename = p.getFileName().toString();
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + urlEncode(filename));
        resp.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        try (InputStream in = Files.newInputStream(p)) {
            StreamUtils.copy(in, resp.getOutputStream());
        }
    }

    /** 2) 批量下载（指定某个患者，结合筛选条件） */
    @PostMapping("/patient-zip")
    public void downloadPatientZip(@RequestBody PatientZipReq req, HttpServletResponse resp) throws IOException {
        List<String> patientIds = Collections.singletonList(req.getPatientId());
        List<PatientFile> files = patientFileService.listFiles(req.getDate(), patientIds, req.getTypes(), req.getFilenameLike());
        String zipName = String.format("patient_%s_%s.zip",
                req.getPatientId(),
                Optional.ofNullable(req.getDate()).map(LocalDate::toString).orElse("all"));

        streamZip(files, zipName, resp);
    }

    /** 3) 批量下载（全局，支持多患者 + 条件） */
    @PostMapping("/all-zip")
    public void downloadAllZip(@RequestBody AllZipReq req, HttpServletResponse resp) throws IOException {
        List<PatientFile> files = patientFileService.listFiles(req.getDate(), req.getPatientIds(), req.getTypes(), req.getFilenameLike());
        String zipName = String.format("export_%s_%dfiles.zip",
                Optional.ofNullable(req.getDate()).map(LocalDate::toString).orElse("all"),
                files.size());
        streamZip(files, zipName, resp);
    }

    // ---------- 内部工具 ----------

    private void streamZip(List<PatientFile> files, String zipName, HttpServletResponse resp) throws IOException {
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + urlEncode(zipName));
        resp.setContentType("application/zip");

        // ZIP 内的目录结构：patientId/yyyy-MM-dd/HH-mm-ss/文件名
        DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH-mm-ss");

        try (ZipOutputStream zos = new ZipOutputStream(resp.getOutputStream())) {
            int added = 0;
            for (PatientFile pf : files) {
                Path real = Paths.get(pf.getFilePath()).normalize();
                if (!Files.exists(real) || Files.isDirectory(real)) continue;

                String date = pf.getSavedAt().toLocalDate().format(DATE);
                String time = pf.getSavedAt().toLocalTime().format(TIME);
                String baseName = real.getFileName().toString(); // imu.csv / gas.csv / audio.wav

                String entryName = pf.getPatientId() + "/" + date + "/" + time + "/" + baseName;

                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(real, zos);
                zos.closeEntry();
                added++;
            }
            log.info("ZIP 打包完成，共 {} 个文件", added);
        }
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    // ---------- 请求体 ----------

    @Data
    public static class PatientZipReq {
        private String patientId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;             // 可空
        private List<String> types;         // 可空
        private String filenameLike;        // 可空
    }

    @Data
    public static class AllZipReq {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;             // 可空
        private List<String> patientIds;    // 可空
        private List<String> types;         // 可空
        private String filenameLike;        // 可空
    }
}
