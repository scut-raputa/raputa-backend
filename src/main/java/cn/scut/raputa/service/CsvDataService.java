package cn.scut.raputa.service;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
public class CsvDataService {
    
    private static final String CSV_DIRECTORY = "D:/health_plat_bk/data/csv";
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    // 存储每个设备的文件路径和CSVWriter
    private final ConcurrentHashMap<String, CSVWriter> imuWriters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CSVWriter> gasWriters = new ConcurrentHashMap<>();
    
    // 存储每个设备的文件名（包含时间戳）
    private final ConcurrentHashMap<String, String> imuFileNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> gasFileNames = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> sessionPatientIds   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionPatientNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionDeviceNames  = new ConcurrentHashMap<>();

    //取出CSV文件路径目录
    public String getCsvDirectory() {
        return CSV_DIRECTORY;
    }

    public void setSessionMeta(String deviceId, String patientId, String patientName, String deviceName) {
        sessionPatientIds.put(deviceId, patientId == null ? "" : patientId.trim());
        sessionPatientNames.put(deviceId, patientName == null ? "" : patientName.trim());
        sessionDeviceNames.put(deviceId, deviceName == null ? "" : deviceName.trim());
        log.info("登记会话元信息 deviceId={}, patientId={}, patientName={}, deviceName={}",
                deviceId, patientId, patientName, deviceName);
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
                    String fileName = generateFileName(deviceId, "imu");
                    imuFileNames.put(deviceId, fileName);
                    Path filePath = createCsvFile(fileName);
                    CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toFile(), true));
                    if (Files.size(filePath) == 0) {
                        csvWriter.writeNext(new String[]{"timestamp", "x", "y", "z"});
                        csvWriter.flush();
                    }
                    log.info("创建新的IMU CSV文件: {}", fileName);
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
                
                String fileName = imuFileNames.get(deviceId);
                log.debug("成功写入 {} 条IMU数据到文件: {}", dataList.size(), fileName);
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
                    String fileName = generateFileName(deviceId, "gas");
                    gasFileNames.put(deviceId, fileName);
                    Path filePath = createCsvFile(fileName);
                    CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toFile(), true));
                    if (Files.size(filePath) == 0) {
                        csvWriter.writeNext(new String[]{"timestamp", "flow"});
                        csvWriter.flush();
                    }
                    log.info("创建新的GAS CSV文件: {}", fileName);
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
                
                String fileName = gasFileNames.get(deviceId);
                log.debug("成功写入 {} 条GAS数据到文件: {}", dataList.size(), fileName);
            }
            
        } catch (IOException e) {
            log.error("写入GAS数据到CSV文件失败", e);
        }
    }
    
    /**
     * 生成文件名 - 包含时间戳，用于区分不同的连接会话
     */
    private String generateFileName(String deviceId, String dataType) {
        String ts = LocalDateTime.now().format(FILE_NAME_FORMATTER);
        String pid   = sanitize(sessionPatientIds.getOrDefault(deviceId, "unknown"));
        String pname = sanitize(sessionPatientNames.getOrDefault(deviceId, "unknown"));
        String dname = sanitize(sessionDeviceNames.getOrDefault(deviceId, deviceId)); // 没传就用deviceId兜底
        return String.format("%s_%s_%s_%s_%s.csv", dataType, pid, pname, dname, ts);
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
     */
    public void closeWriter(String deviceId) {
        try {
            CSVWriter imuWriter = imuWriters.remove(deviceId);
            if (imuWriter != null) {
                imuWriter.close();
                String fileName = imuFileNames.remove(deviceId);
                log.info("关闭设备 {} 的IMU CSV写入器，文件: {}", deviceId, fileName);
            }
            
            CSVWriter gasWriter = gasWriters.remove(deviceId);
            if (gasWriter != null) {
                gasWriter.close();
                String fileName = gasFileNames.remove(deviceId);
                log.info("关闭设备 {} 的GAS CSV写入器，文件: {}", deviceId, fileName);
            }
        } catch (IOException e) {
            log.error("关闭CSV写入器失败: {}", deviceId, e);
        }
    }
    
    /**
     * 创建CSV文件
     */
    private Path createCsvFile(String fileName) throws IOException {
        Path directory = Paths.get(CSV_DIRECTORY);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        
        Path filePath = directory.resolve(fileName);
        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }
        
        return filePath;
    }
    
    /**
     * 获取CSV文件列表
     */
    public List<String> getCsvFileList() {
        try {
            Path directory = Paths.get(CSV_DIRECTORY);
            if (!Files.exists(directory)) {
                return new ArrayList<>();
            }
            
            return Files.list(directory)
                    .filter(path -> path.toString().endsWith(".csv"))
                    .map(path -> path.getFileName().toString())
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
            Path filePath = Paths.get(CSV_DIRECTORY, fileName);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("删除CSV文件失败: {}", fileName, e);
            return false;
        }
    }
}
