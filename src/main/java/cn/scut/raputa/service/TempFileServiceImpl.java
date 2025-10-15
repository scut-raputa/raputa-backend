package cn.scut.raputa.service;

import cn.scut.raputa.dto.CsvMappingRequestDTO;
import cn.scut.raputa.entity.TempFile;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.TempFileRepository;
import cn.scut.raputa.utils.Ids;
import cn.scut.raputa.vo.TempFileUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TempFileServiceImpl implements TempFileService {

    private final TempFileRepository repo;

    @Value("${raputa.storage.tmp-dir:${java.io.tmpdir}/raputa/tmp}")
    private String tmpRoot;

    @Override
    public TempFileUploadVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "文件不能为空");
        }

        String originalName = sanitize(file.getOriginalFilename());
        String ext = getExtensionLower(originalName);
        String contentType = file.getContentType();

        // 允许：后缀 csv 或 content-type 包含 csv
        boolean looksLikeCsv = "csv".equalsIgnoreCase(ext)
                || (contentType != null && contentType.toLowerCase().contains("csv"));
        if (!looksLikeCsv) {
            throw new BizException(400, "仅支持 CSV 文件");
        }

        String id = Ids.randomId(20);
        String storedName = id + "." + (ext.isEmpty() ? "csv" : ext);

        Path dir = Paths.get(tmpRoot, id);
        Path filePath = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            if (file.getSize() > 50 * 1024 * 1024L) {
                throw new BizException(413, "文件过大（>50MB）");
            }
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Throwable root = e;
            while (root.getCause() != null)
                root = root.getCause();
            throw new BizException(500, "文件保存失败: " + root.getClass().getSimpleName() + ": " + root.getMessage());
        }

        TempFile t = new TempFile();
        t.setId(id);
        t.setOriginalName(originalName);
        t.setStoredName(storedName);
        t.setLocation(filePath.toAbsolutePath().toString());
        t.setContentType(contentType);
        t.setSize(file.getSize());
        t.setExpireAt(LocalDateTime.now(TempFile.ZONE_CN).plusHours(6));
        try {
            repo.save(t);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null)
                root = root.getCause();
            throw new BizException(500, "数据库写入失败: " + root.getClass().getSimpleName() + ": " + root.getMessage());
        }

        return new TempFileUploadVO(id);
    }

    @Override
    public void setMapping(String tempId, CsvMappingRequestDTO req) {
        TempFile t = repo.findById(tempId)
                .orElseThrow(() -> new BizException(404, "临时文件不存在"));

        t.setSampleRate(req.getSampleRate());
        if (req.getImuAxisMap() != null) {
            t.setImuX(emptyToNull(req.getImuAxisMap().getX()));
            t.setImuY(emptyToNull(req.getImuAxisMap().getY()));
            t.setImuZ(emptyToNull(req.getImuAxisMap().getZ()));
        }
        t.setGasCol(emptyToNull(req.getGasCol()));
        t.setAudioCol(emptyToNull(req.getAudioCol()));
        t.setMappingSet(true);
        repo.save(t);
    }

    @Override
    public void delete(String tempId) {
        TempFile t = repo.findById(tempId)
                .orElseThrow(() -> new BizException(404, "临时文件不存在"));

        Path file = Paths.get(t.getLocation());
        Path dir = file.getParent();
        try {
            Files.deleteIfExists(file);
            if (dir != null && Files.isDirectory(dir) && isDirEmpty(dir)) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            throw new BizException(500, "删除临时文件失败");
        }
        repo.deleteById(tempId);
    }

    // ---------- helpers ----------

    private static String sanitize(String name) {
        if (name == null)
            return "unknown.csv";
        return Paths.get(name).getFileName().toString(); // 只保留文件名，防路径穿越
    }

    private static String getExtensionLower(String filename) {
        if (filename == null)
            return "";
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1)
            return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static boolean isDirEmpty(Path dir) throws IOException {
        try (var ds = Files.newDirectoryStream(dir)) {
            return !ds.iterator().hasNext();
        }
    }
}
