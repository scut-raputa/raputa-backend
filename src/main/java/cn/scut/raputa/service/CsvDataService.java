package cn.scut.raputa.service;

import cn.scut.raputa.entity.CaptureSession;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.CaptureSessionRepository;
import com.opencsv.CSVWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSV数据写入服务 - 参考原始项目的ImuGasCSV.java
 * 负责将IMU和GAS数据写入CSV文件
 * 
 * @author RAPUTA Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CsvDataService {

    private final PatientFileService patientFileService;
    private final CaptureSessionRepository captureSessionRepository;
    private final FileStorageService fileStorageService;
    
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    // 存储每个设备的文件路径和CSVWriter
    private final ConcurrentHashMap<String, CSVWriter> imuWriters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CSVWriter> gasWriters = new ConcurrentHashMap<>();
    
    // 存储每个设备的会话文件夹路径
    private final ConcurrentHashMap<String, String> sessionFolders = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> deviceSessionIds = new ConcurrentHashMap<>();
    
    private final ConcurrentHashMap<String, String> sessionPatientIds   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionPatientNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionDeviceNames  = new ConcurrentHashMap<>();

    //取出CSV文件路径目录
    public String getCsvDirectory() {
        return fileStorageService.getStorageRootPath().toString();
    }

    public String getSessionIdByDeviceId(String deviceId) {
        return deviceSessionIds.get(deviceId);
    }

    // >>> 新增：会话元信息只读访问（供 RealtimeDataService.stop 使用）
    public String getSessionPatientId(String deviceId) {
        return sessionPatientIds.get(deviceId);
    }
    public String getSessionPatientName(String deviceId) {
        return sessionPatientNames.get(deviceId);
    }
    public String getSessionDeviceName(String deviceId) {
        return sessionDeviceNames.get(deviceId);
    }
    
    /**
     * 获取设备的会话文件夹路径
     */
    public String getSessionFolder(String deviceId) {
        return sessionFolders.get(deviceId);
    }

    public void setSessionMeta(
            String deviceId,
            String patientId,
            String patientName,
            String deviceName,
            String sessionId,
            String sessionDir) {
        sessionPatientIds.put(deviceId, patientId == null ? "" : patientId.trim());
        sessionPatientNames.put(deviceId, patientName == null ? "" : patientName.trim());
        sessionDeviceNames.put(deviceId, deviceName == null ? "" : deviceName.trim());
        if (sessionId != null && !sessionId.isBlank()) {
            deviceSessionIds.put(deviceId, sessionId);
        }
        
        try {
            Path folderPath;
            if (sessionDir != null && !sessionDir.isBlank()) {
                folderPath = fileStorageService.resolveSessionDir(sessionDir);
            } else {
                String folderName = generateSessionFolderName(deviceId);
                folderPath = fileStorageService.getStorageRootPath().resolve(folderName);
            }
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }
            sessionFolders.put(deviceId, folderPath.toString());
            log.info("创建会话文件夹: {}", folderPath);
        } catch (IOException e) {
            log.error("创建会话文件夹失败", e);
        }
        
        log.info("登记会话元信息 deviceId={}, patientId={}, patientName={}, deviceName={}",
                deviceId, patientId, patientName, deviceName);
    }
    
    /**
     * 生成会话文件夹名称: 患者id_患者姓名_时间戳
     */
    private String generateSessionFolderName(String deviceId) {
        String timestamp = LocalDateTime.now().format(FILE_NAME_FORMATTER);
        String pid = sanitize(sessionPatientIds.getOrDefault(deviceId, "unknown"));
        String pname = sanitize(sessionPatientNames.getOrDefault(deviceId, "unknown"));
        return String.format("%s_%s_%s", pid, pname, timestamp);
    }


    /**
     * 写入IMU数据到CSV文件
     *
     * @param deviceId 设备ID
     * @param dataList IMU数据列表 [timestamp, x, y, z]
     */
    public void writeImuData(String deviceId, List<String[]> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        try {
            // 检查是否已有writer，如果没有则创建新的文件
            CSVWriter writer = imuWriters.computeIfAbsent(deviceId, id -> {
                try {
                    Path filePath = getSessionFilePath(deviceId, "imu.csv");
                    CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toFile(), true));
                    if (Files.size(filePath) == 0) {
                        // 只写表头，不登记 PatientFile，先当“临时文件”
                        csvWriter.writeNext(new String[]{"time", "X", "Y", "Z"});
                        csvWriter.flush();
                    }
                    log.info("创建新的IMU CSV文件: {}", filePath);
                    return csvWriter;
                } catch (IOException e) {
                    log.error("创建IMU CSV写入器失败: {}", deviceId, e);
                    return null;
                }
            });

            if (writer != null) {
                // 写入数据
                for (String[] data : dataList) {
                    writer.writeNext(data);
                }
                writer.flush();

                log.debug("成功写入 {} 条IMU数据", dataList.size());
            }

        } catch (IOException e) {
            log.error("写入IMU数据到CSV文件失败", e);
        }
    }
    
    /**
     * 写入GAS数据到CSV文件
     *
     * @param deviceId 设备ID
     * @param dataList GAS数据列表 [timestamp, flow]
     */
    public void writeGasData(String deviceId, List<String[]> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        try {
            // 检查是否已有writer，如果没有则创建新的文件
            CSVWriter writer = gasWriters.computeIfAbsent(deviceId, id -> {
                try {
                    Path filePath = getSessionFilePath(deviceId, "gas.csv");
                    CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toFile(), true));
                    if (Files.size(filePath) == 0) {
                        // 只写表头，不登记 PatientFile，先当“临时文件”
                        csvWriter.writeNext(new String[]{"time", "value"});
                        csvWriter.flush();
                    }
                    log.info("创建新的GAS CSV文件: {}", filePath);
                    return csvWriter;
                } catch (IOException e) {
                    log.error("创建GAS CSV写入器失败: {}", deviceId, e);
                    return null;
                }
            });

            if (writer != null) {
                // 写入数据
                for (String[] data : dataList) {
                    writer.writeNext(data);
                }
                writer.flush();

                log.debug("成功写入 {} 条GAS数据", dataList.size());
            }

        } catch (IOException e) {
            log.error("写入GAS数据到CSV文件失败", e);
        }
    }
    
    /**
     * 获取会话文件路径（在会话文件夹内）
     */
    private Path getSessionFilePath(String deviceId, String fileName) throws IOException {
        String sessionFolder = sessionFolders.get(deviceId);
        if (sessionFolder == null) {
            // 如果没有会话文件夹，创建一个默认的
            String folderName = generateSessionFolderName(deviceId);
            Path folderPath = fileStorageService.getStorageRootPath().resolve(folderName);
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }
            sessionFolders.put(deviceId, folderPath.toString());
            sessionFolder = folderPath.toString();
        }
        return Paths.get(sessionFolder, fileName);
    }

    private static String sanitize(String s) {
        if (s == null) return "unknown";
        String t = s.trim();
        if (t.isEmpty()) return "unknown";
        // 去除常见非法字符：\/:*?"<>| 与控制符；空白序列规整为单下划线
        t = t.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_");
        t = t.replaceAll("\\s+", "_");
        // 可选：长度截断，避免极长文件名
        if (t.length() > 80) t = t.substring(0, 80);
        return t;
    }
    
    /**
     * 关闭指定设备的CSV写入器
     *
     * 这里只负责关 writer 和打日志，不再登记任何 PatientFile，
     * 也不清理 sessionFolders / sessionPatientIds，留给“最终确认”来做。
     */
    public void closeWriter(String deviceId) {
        try {
            CSVWriter imuWriter = imuWriters.remove(deviceId);
            if (imuWriter != null) {
                imuWriter.close();
                log.info("关闭设备 {} 的IMU CSV写入器", deviceId);
            }

            CSVWriter gasWriter = gasWriters.remove(deviceId);
            if (gasWriter != null) {
                gasWriter.close();
                log.info("关闭设备 {} 的GAS CSV写入器", deviceId);
            }

            String sessionFolder = sessionFolders.get(deviceId);
            if (sessionFolder != null) {
                log.info("设备 {} 本次会话文件临时保存在: {}", deviceId, sessionFolder);
            }

            // ❗ 不在这里登记 audio.wav，不在这里清理 session 元信息
            // 由 finalizeSessionFiles 来做“临时转正式”

        } catch (IOException e) {
            log.error("关闭CSV写入器失败: {}", deviceId, e);
        }
    }

    /**
     * 在医生确认报告并下载后调用：
     * 将本次会话的 imu.csv / gas.csv / audio.wav 统一登记为正式患者文件，
     * 然后清理会话元信息。
     */
    public void finalizeSessionFiles(String deviceId) {
        String sessionId = deviceSessionIds.get(deviceId);
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(400, "当前设备不存在可 finalize 的会话");
        }
        finalizeSessionFilesBySessionId(sessionId);
        cleanupDeviceSessionMapping(deviceId);
    }

    public void finalizeSessionFilesBySessionId(String sessionId) {
        CaptureSession session = captureSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(404, "会话不存在: " + sessionId));

        if (session.getFinalizedAt() != null) {
            log.info("会话已 finalize，跳过重复执行: sessionId={}", sessionId);
            return;
        }

        if (session.getSessionDir() == null || session.getSessionDir().isBlank()) {
            throw new BizException(400, "会话目录缺失，无法 finalize");
        }
        if (session.getPatientId() == null || session.getPatientId().isBlank()) {
            throw new BizException(400, "会话缺少 patientId，无法 finalize");
        }

        Path folder = fileStorageService.resolveSessionDir(session.getSessionDir());
        registerFileIfExists(session.getPatientId(), sessionId, folder.resolve("imu.csv"), "csv");
        registerFileIfExists(session.getPatientId(), sessionId, folder.resolve("gas.csv"), "csv");
        registerFileIfExists(session.getPatientId(), sessionId, folder.resolve("audio.wav"), "wav");

        session.setStatus("FINALIZED");
        session.setFinalizedAt(LocalDateTime.now(CaptureSession.ZONE_CN));
        captureSessionRepository.save(session);

        if (session.getDeviceId() != null && !session.getDeviceId().isBlank()) {
            cleanupDeviceSessionMapping(session.getDeviceId());
        }
        log.info("会话 finalize 完成: sessionId={}, folder={}", sessionId, folder);
    }

    /**
     * 辅助：文件存在才登记到 PatientFile
     */
    private void registerFileIfExists(String patientId, String sessionId, Path path, String ext) {
        try {
            if (Files.exists(path)) {
                patientFileService.record(
                        patientId,
                        sessionId,
                        path.toAbsolutePath().toString(),
                        ext,
                        LocalDateTime.now());
                log.info("登记患者文件: patientId={}, file={}, ext={}", patientId, path, ext);
            } else {
                log.warn("finalize 时文件不存在: {}", path);
            }
        } catch (Exception e) {
            log.error("登记文件失败: {}", path, e);
        }
    }

    private void cleanupDeviceSessionMapping(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        deviceSessionIds.remove(deviceId);
        sessionFolders.remove(deviceId);
        sessionPatientIds.remove(deviceId);
        sessionPatientNames.remove(deviceId);
        sessionDeviceNames.remove(deviceId);
    }

    
    /**
     * 获取CSV文件列表
     */
    public List<String> getCsvFileList() {
        try {
            Path directory = fileStorageService.getStorageRootPath();
            if (!Files.exists(directory)) {
                return new ArrayList<>();
            }
            
            return Files.walk(directory)
                    .filter(path -> path.toString().endsWith(".csv"))
                    .map(path -> fileStorageService.toRelativePath(path))
                    .sorted()
                    .toList();
                    
        } catch (IOException e) {
            log.error("获取CSV文件列表失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 删除CSV文件
     */
    public boolean deleteCsvFile(String fileName) {
        try {
            Path filePath = fileStorageService.getStorageRootPath().resolve(fileName).normalize();
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("删除CSV文件失败: {}", fileName, e);
            return false;
        }
    }
    
    /**
     * 导出最近N秒的数据段 (IMU/GAS)
     * 
     * @param deviceId 设备ID
     * @param dataType 数据类型 ("imu" 或 "gas")
     * @param seconds 时长(秒)
     * @return 临时CSV文件
     */
    public File exportDataSegment(String deviceId, String dataType, int seconds) {
        try {
            String sessionFolder = sessionFolders.get(deviceId);
            if (sessionFolder == null) {
                log.warn("设备 {} 的会话文件夹不存在", deviceId);
                return null;
            }
            
            String fileName = dataType + ".csv";
            Path sourcePath = Paths.get(sessionFolder, fileName);
            if (!Files.exists(sourcePath)) {
                log.warn("文件不存在: {}", sourcePath);
                return null;
            }
            
            // 读取所有行
            List<String> allLines = Files.readAllLines(sourcePath);
            if (allLines.size() <= 1) {
                log.warn("文件数据不足: {}", fileName);
                return null;
            }
            
            // 获取最后一条数据的时间戳作为基准
            String lastLine = allLines.get(allLines.size() - 1);
            String[] lastParts = lastLine.split(",");
            if (lastParts.length == 0) {
                log.warn("最后一行数据格式错误: {}", fileName);
                return null;
            }
            
            // 解析时间戳
            Long endTimeObj = parseTimestamp(lastParts[0], lastLine, allLines, fileName);
            if (endTimeObj == null) {
                return null;
            }
            final long endTime = endTimeObj;
            
            // 计算时间范围 - 从最后一条数据往前N秒
            long startTime = endTime - (seconds * 1000L);
            
            log.debug("导出数据段: {} - 时间范围: {} 到 {}", fileName, startTime, endTime);
            
            // 过滤最近N秒的数据
            List<String> header = List.of(allLines.get(0));
            List<String> dataLines = allLines.subList(1, allLines.size()).stream()
                .filter(line -> {
                    String[] parts = line.split(",");
                    if (parts.length > 0) {
                        try {
                            // 去除引号和空格
                            String timestampStr = parts[0].trim().replace("\"", "");
                            long timestamp = Long.parseLong(timestampStr);
                            return timestamp >= startTime && timestamp <= endTime;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                    return false;
                })
                .toList();
            
            if (dataLines.isEmpty()) {
                log.warn("最近{}秒没有数据: {} (时间范围: {} - {})", seconds, fileName, startTime, endTime);
                return null;
            }
            
            // 创建临时文件（在会话文件夹内）
            String tempFileName = String.format("%s_segment_%d.csv", dataType, System.currentTimeMillis());
            Path tempPath = Paths.get(sessionFolder, tempFileName);
            
            // 写入数据
            List<String> outputLines = new ArrayList<>();
            outputLines.addAll(header);
            outputLines.addAll(dataLines);
            Files.write(tempPath, outputLines);
            
            log.info("导出数据段成功: {}, 数据行数: {}, 时间范围: {}s", tempFileName, dataLines.size(), seconds);
            return tempPath.toFile();
            
        } catch (Exception e) {
            log.error("导出数据段失败", e);
            return null;
        }
    }
    
    /**
     * 导出最近N秒的音频数据段
     * 注意：音频文件是WAV格式，需要特殊处理
     * 为避免文件锁冲突，创建临时副本而不是直接使用原文件
     * 
     * @param deviceId 设备ID
     * @param seconds 时长(秒)
     * @return 音频WAV文件（临时副本）
     */
    public File exportAudioSegment(String deviceId, int seconds) {
        try {
            String sessionFolder = sessionFolders.get(deviceId);
            if (sessionFolder == null) {
                log.warn("设备 {} 的会话文件夹不存在", deviceId);
                return null;
            }
            
            // 原始音频文件
            Path audioPath = Paths.get(sessionFolder, "audio.wav");
            if (!Files.exists(audioPath)) {
                log.warn("设备 {} 的音频文件不存在: {}", deviceId, audioPath);
                return null;
            }
            
            // 创建临时片段，避免文件锁冲突
            String tempFileName = String.format("audio_segment_%d.wav", System.currentTimeMillis());
            Path tempPath = Paths.get(sessionFolder, tempFileName);

            WavFileUtils.writeLastSecondsAsCleanWav(audioPath, tempPath, Math.max(seconds, 1));

            log.info("创建音频临时副本: {} (大小: {} bytes)", tempFileName, Files.size(tempPath));
            return tempPath.toFile();
            
        } catch (Exception e) {
            log.error("导出音频数据段失败", e);
            return null;
        }
    }

    /**
     * 解析时间戳，如果失败尝试倒数第二条
     */
    private Long parseTimestamp(String timestampField, String line, List<String> allLines, String fileName) {
        try {
            // 去除可能的空格和引号
            String timestampStr = timestampField.trim().replace("\"", "");
            long timestamp = Long.parseLong(timestampStr);
            log.debug("成功解析时间戳: {} -> {}", timestampField, timestamp);
            return timestamp;
        } catch (NumberFormatException e) {
            log.error("无法解析最后一条数据的时间戳: '{}', 原始行: {}", timestampField, line);
            // 尝试倒数第二条
            if (allLines.size() > 2) {
                String secondLastLine = allLines.get(allLines.size() - 2);
                String[] secondLastParts = secondLastLine.split(",");
                if (secondLastParts.length > 0) {
                    try {
                        String timestampStr = secondLastParts[0].trim().replace("\"", "");
                        long timestamp = Long.parseLong(timestampStr);
                        log.info("使用倒数第二条数据的时间戳: {}", timestamp);
                        return timestamp;
                    } catch (NumberFormatException e2) {
                        log.error("倒数第二条也无法解析，放弃: {}", secondLastParts[0]);
                    }
                }
            }
            return null;
        }
    }
}
