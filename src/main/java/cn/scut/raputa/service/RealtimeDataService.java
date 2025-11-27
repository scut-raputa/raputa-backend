package cn.scut.raputa.service;

import cn.scut.raputa.entity.CheckRecord;
import cn.scut.raputa.repository.CheckRecordRepository;
import cn.scut.raputa.repository.PatientRepository;
import cn.scut.raputa.utils.DataBuffer;
import cn.scut.raputa.utils.SocketTools;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实时数据接收服务
 * 主动连接树莓派6667端口接收IMU、GAS、AUDIO数据
 * 
 * @author RAPUTA Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeDataService {

    private final CsvDataService csvDataService;
    private final WebSocketService webSocketService;
    private final ModelPredictionService modelPredictionService;

    // 设备连接状态管理
    private final ConcurrentHashMap<String, DeviceConnection> deviceConnections = new ConcurrentHashMap<>();
    
    // CSV写入定时器
    private final ScheduledExecutorService csvWriteScheduler = Executors.newScheduledThreadPool(2);

    private final CheckRecordRepository checkRecordRepository;
    private final PatientRepository patientRepository;  

    /**
     * 设备连接信息
     */
    private static class DeviceConnection {
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;
        private Thread receiveThread;
        private final AtomicBoolean isConnected = new AtomicBoolean(false);
        private final AtomicBoolean isReceiving = new AtomicBoolean(false);
        private byte[] buffer = new byte[0];
        private LocalDateTime lastHeartbeat;
        private String deviceId;
        
        // CSV数据缓冲队列 - 参考原始项目
        private final DataBuffer imuBuffer = new DataBuffer(99999999);
        private final DataBuffer gasBuffer = new DataBuffer(99999999);
        
        // 数据计数器
        private int imuCount = 0;
        private int gasCount = 0;
        
        // 首条数据标记 - 用于舍弃第一条数据
        private boolean imuFirstData = true;
        private boolean gasFirstData = false;
        
        // WebSocket推送计数器 - 用于降低推送频率
        private int imuPushCount = 0;
        private int gasPushCount = 0;
        
        // CSV写入定时任务
        private java.util.concurrent.ScheduledFuture<?> imuWriteTask;
        private java.util.concurrent.ScheduledFuture<?> gasWriteTask;
        
        // 音频RTSP相关
        private FFmpegFrameGrabber audioGrabber;
        private FFmpegFrameRecorder audioRecorder;
        private Thread audioThread;
        private final AtomicBoolean audioReceiving = new AtomicBoolean(false);
        private String deviceIp;
        private String audioFilePath;
        private int audioRetryCount = 0; // 音频重试次数
        private static final int MAX_AUDIO_RETRY = 5; // 最大重试次数
        private long audioStartTimestamp = 0; // 音频开始时间戳（毫秒）
        private long audioFrameCount = 0; // 音频帧计数
        private Frame audioFirstFrame; // 保存第一帧，等待所有数据就绪后再初始化录制器
        
        // 数据就绪状态标志 - 三种数据都就绪后才开始保存文件
        private final AtomicBoolean imuReady = new AtomicBoolean(false);
        private final AtomicBoolean gasReady = new AtomicBoolean(false);
        private final AtomicBoolean audioReady = new AtomicBoolean(false);
        private final AtomicBoolean allDataReady = new AtomicBoolean(false);
        
        // 音频数据降采样 - 从48kHz降到200Hz
        private int audioPushCount = 0;
        private int audioPushMethodCallCount = 0; // 调用计数器
        private static final int AUDIO_DOWNSAMPLE_RATIO = 240; // 48000 / 200 = 240
        
        // 定时预测任务
        private java.util.concurrent.ScheduledFuture<?> predictionTask;
        private long lastPredictionTime = 0; // 上次预测的时间戳

        public DeviceConnection(String deviceId) {
            this.deviceId = deviceId;
            this.lastHeartbeat = LocalDateTime.now();
        }
        
        /**
         * 检查所有数据是否就绪
         */
        public boolean checkAllDataReady() {
            if (!allDataReady.get() && imuReady.get() && gasReady.get() && audioReady.get()) {
                allDataReady.set(true);
                return true;
            }
            return allDataReady.get();
        }
    }

    /**
     * 开始连接设备并接收数据
     */
    public CompletableFuture<Boolean> startDataReceiving(String deviceIp, String deviceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeviceConnection connection = new DeviceConnection(deviceId);
                connection.deviceIp = deviceIp; // 保存IP用于音频RTSP连接

                // 建立TCP连接
                Socket socket = new Socket(deviceIp, 6667);
                socket.setSoTimeout(15000);
                
                connection.socket = socket;
                connection.inputStream = socket.getInputStream();
                connection.outputStream = socket.getOutputStream();
                connection.isConnected.set(true);
                
                // 发送开始接收命令
                String command = "true";
                byte[] commandData = SocketTools.packSFream(command);
                connection.outputStream.write(commandData);
                connection.outputStream.flush();
                
                log.info("成功连接到设备 {}:{}，开始接收数据", deviceIp, 6667);
                
                // 启动数据接收线程
                connection.receiveThread = new Thread(() -> receiveDataLoop(connection));
                connection.receiveThread.setDaemon(true);
                connection.receiveThread.start();
                
                // 启动CSV写入定时器 - 参考原始项目的setTimerWIMU和setTimerWGas
                startCsvWriteTimers(connection);
                
                // 启动音频RTSP接收 - 参考原始项目的WaveFrom.play()
                startAudioReceiving(connection);
                
                // 启动定时预测任务 - 每10秒执行一次
                startPredictionTimer(connection);
                
                deviceConnections.put(deviceId, connection);
                return true;
                
            } catch (IOException e) {
                log.error("连接设备失败: {}", deviceIp, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> startDataReceiving(
            String deviceIp,
            String deviceId,
            String deviceName,
            String patientId,
            String patientName) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                DeviceConnection connection = new DeviceConnection(deviceId);
                connection.deviceIp = deviceIp; // 保存IP用于音频RTSP连接

                // 1) 先登记会话元信息（关键：必须在任何写入发生前）
                csvDataService.setSessionMeta(deviceId, patientId, patientName, deviceName);

                // 2) 建立TCP连接
                Socket socket = new Socket(deviceIp, 6667);
                socket.setSoTimeout(15000);
                connection.socket = socket;
                connection.inputStream  = socket.getInputStream();
                connection.outputStream = socket.getOutputStream();
                connection.isConnected.set(true);

                // 3) 发送开始接收命令
                String command = "true";
                byte[] commandData = SocketTools.packSFream(command);
                connection.outputStream.write(commandData);
                connection.outputStream.flush();

                log.info("成功连接到设备 {}:{}，开始接收数据", deviceIp, 6667);

                // 4) 启动接收线程
                connection.receiveThread = new Thread(() -> receiveDataLoop(connection));
                connection.receiveThread.setDaemon(true);
                connection.receiveThread.start();

                // 5) 启动CSV写定时器（此时已具备患者/设备信息，文件名不会 unknown）
                startCsvWriteTimers(connection);

                // 启动音频RTSP接收 - 参考原始项目的WaveFrom.play()
                startAudioReceiving(connection);
                
                // 启动定时预测任务 - 每10秒执行一次
                startPredictionTimer(connection);
                
                

                deviceConnections.put(deviceId, connection);
                return true;

            } catch (IOException e) {
                log.error("连接设备失败: {}", deviceIp, e);
                return false;
            }
        });
    }

    /**
     * 启动CSV写入定时器 - 参考原始项目的setTimerWIMU和setTimerWGas
     */
    private void startCsvWriteTimers(DeviceConnection connection) {
        // 确保之前的定时器已取消
        if (connection.imuWriteTask != null) {
            connection.imuWriteTask.cancel(false);
        }
        if (connection.gasWriteTask != null) {
            connection.gasWriteTask.cancel(false);
        }
        
        // IMU数据写入定时器 - 每200ms执行一次，每次处理400条数据
        connection.imuWriteTask = csvWriteScheduler.scheduleAtFixedRate(() -> {
            try {
                writeImuDataToCsv(connection);
            } catch (Exception e) {
                log.error("IMU数据CSV写入异常", e);
            }
        }, 1, 200, TimeUnit.MILLISECONDS);
        
        log.info("启动设备 {} 的IMU CSV写入定时器", connection.deviceId);
        
        // GAS数据写入定时器 - 每200ms执行一次，每次处理20条数据
        connection.gasWriteTask = csvWriteScheduler.scheduleAtFixedRate(() -> {
            try {
                writeGasDataToCsv(connection);
            } catch (Exception e) {
                log.error("GAS数据CSV写入异常", e);
            }
        }, 1, 200, TimeUnit.MILLISECONDS);
        
        log.info("启动设备 {} 的GAS CSV写入定时器", connection.deviceId);
    }
    
    /**
     * 启动定时预测任务 - 每10秒导出数据并调用模型预测
     */
    private void startPredictionTimer(DeviceConnection connection) {
        // 确保之前的任务已取消
        if (connection.predictionTask != null) {
            connection.predictionTask.cancel(false);
        }
        
        // 定时预测任务 - 每10秒执行一次，首次15秒后开始（等待数据积累）
        connection.predictionTask = csvWriteScheduler.scheduleAtFixedRate(() -> {
            try {
                // 只有在所有数据就绪后才执行预测
                if (connection.allDataReady.get()) {
                    performPrediction(connection);
                }
            } catch (Exception e) {
                log.error("定时预测异常", e);
            }
        }, 7, 5, TimeUnit.SECONDS);
        
        log.info("启动设备 {} 的定时预测任务（每10秒）", connection.deviceId);
    }
    
    /**
     * 执行模型预测 - 导出最近10秒的数据并调用模型
     */
    private void performPrediction(DeviceConnection connection) {
        try {
            long currentTime = System.currentTimeMillis();
            
            log.info("开始执行设备 {} 的模型预测", connection.deviceId);
            
            // 导出最近10秒的数据段
            File audioSegment = csvDataService.exportAudioSegment(connection.deviceId, 5);
            File imuSegment = csvDataService.exportDataSegment(connection.deviceId, "imu", 5);
            File gasSegment = csvDataService.exportDataSegment(connection.deviceId, "gas", 5);
            
            if (audioSegment == null || imuSegment == null || gasSegment == null) {
                log.warn("设备 {} 数据段导出失败，跳过本次预测", connection.deviceId);
                return;
            }
            
            // 调用模型预测
            ModelPredictionService.PredictionResult result = 
                modelPredictionService.uploadAndPredict(audioSegment, imuSegment, gasSegment);
            
            if (result != null) {
                // 推送结果到前端
                webSocketService.pushPredictionResult(connection.deviceId, result);
                
                // 记录预测时间
                connection.lastPredictionTime = currentTime;
                
                if (result.hasSwallowEvents()) {
                    log.info("设备 {} 预测成功，检测到 {} 个吴咙事件", 
                        connection.deviceId, result.getSwallowEvents().size());
                } else if (result.getMessage() != null) {
                    log.info("设备 {} 预测结果: {}", connection.deviceId, result.getMessage());
                }
            } else {
                log.error("设备 {} 模型预测失败", connection.deviceId);
            }
            
            // 清理临时文件
            if (audioSegment != null && audioSegment.exists()) audioSegment.delete();
            if (imuSegment != null && imuSegment.exists()) imuSegment.delete();
            if (gasSegment != null && gasSegment.exists()) gasSegment.delete();
            
        } catch (Exception e) {
            log.error("执行预测失败", e);
        }
    }
    
    /**
     * 写入IMU数据到CSV - 参考原始项目的wIMU方法
     */
    private void writeImuDataToCsv(DeviceConnection connection) {
        // 检查所有数据是否就绪，未就绪则不保存
        if (!connection.allDataReady.get()) {
            return;
        }
        
        List<String[]> valList = new ArrayList<>();
        
        // 动态调整处理量 - 参考原项目的动态调整逻辑
        int bufferSize = connection.imuBuffer.getSize();
        int lsize = 400; // 默认处理 400 条
        
        // 如果积压严重,增加单次处理量
        if (bufferSize > 2000) {
            lsize = 1500; // 增加到 600 条
            log.warn("设备 {} IMU缓冲区严重积压: {} 条数据,增加处理量", connection.deviceId, bufferSize);
        } else if (bufferSize > 1500) {
            lsize = 1000; // 增加到 500 条
        }
        
        for (int i = 0; i < lsize; i++) {
            if (connection.imuBuffer.getSize() > 0) {
                Object obj = connection.imuBuffer.poll();
                if (obj != null) {
                    String imuStr = (String) obj;
                    String[] valArr = getValForJsonStr(imuStr, 2); // type=2表示IMU数据
                    if (valArr != null) {
                        connection.imuCount++;
                        valList.add(valArr);
                    }
                }
            } else {
                break;
            }
        }
        
        if (!valList.isEmpty()) {
            csvDataService.writeImuData(connection.deviceId, valList);
        }
    }
    
    /**
     * 写入GAS数据到CSV - 参考原始项目的wGas方法
     */
    private void writeGasDataToCsv(DeviceConnection connection) {
        // 检查所有数据是否就绪，未就绪则不保存
        if (!connection.allDataReady.get()) {
            return;
        }
        
        List<String[]> valList = new ArrayList<>();
        
        // 动态调整处理量
        int bufferSize = connection.gasBuffer.getSize();
        int lsize = 20; // 默认处理 20 条
        
        // 如果积压严重,增加单次处理量
        if (bufferSize > 100) {
            lsize = 40; // 增加到 40 条
        } else if (bufferSize > 50) {
            lsize = 30; // 增加到 30 条
        }
        
        for (int i = 0; i < lsize; i++) {
            if (connection.gasBuffer.getSize() > 0) {
                Object obj = connection.gasBuffer.poll();
                if (obj != null) {
                    String gasStr = (String) obj;
                    String[] valArr = getValForJsonStr(gasStr, 3); // type=3表示GAS数据
                    if (valArr != null) {
                        connection.gasCount++;
                        valList.add(valArr);
                    }
                }
            } else {
                break;
            }
        }
        
        if (!valList.isEmpty()) {
            csvDataService.writeGasData(connection.deviceId, valList);
        }
    }
    
    /**
     * 解析JSON字符串获取数据数组 - 参考原始项目的getValForJsonStr方法
     */
    private String[] getValForJsonStr(String json, int type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        try {
            JsonNode jsonNode = SocketTools.getJsonObject(json);
            if (jsonNode == null || jsonNode.isNull()) {
                return null;
            }
            
            String timestamp = String.valueOf(jsonNode.get("timestamp").asLong());
            String timestampus = String.valueOf(jsonNode.get("timestampus").asLong());
            
            if (type == 2) { // IMU数据
                JsonNode accNode = jsonNode.get("acc");
                if (accNode != null && !accNode.isNull()) {
                    String[] valList = new String[4];
                    valList[0] = setSTimeToLTime(timestamp, timestampus);
                    valList[1] = String.valueOf(accNode.get("x").asInt());
                    valList[2] = String.valueOf(accNode.get("y").asInt());
                    valList[3] = String.valueOf(accNode.get("z").asInt());
                    return valList;
                }
            } else if (type == 3) { // GAS数据
                String flow = "0";
                if (jsonNode.get("flow") != null && !jsonNode.get("flow").isNull()) {
                    flow = String.valueOf(jsonNode.get("flow").asInt());
                }
                String[] valList = new String[2];
                valList[0] = setSTimeToLTime(timestamp, timestampus);
                valList[1] = flow;
                return valList;
            }
            
        } catch (Exception e) {
            log.error("解析JSON数据失败: {}", json, e);
        }
        
        return null;
    }
    
    /**
     * 时间戳转换 - 参考原始项目的setSTimeToLTime方法
     * timestamp: 秒级时间戳
     * timestampus: 微秒部分 (0-999999)
     */
    private String setSTimeToLTime(String timestamp, String timestampus) {
        try {
            // 完全按照原项目的方式实现
            float utile = Integer.valueOf(timestampus); // 原项目用 Integer.valueOf
            float rlt = utile / 1000000f; // 换算成秒 (0.000000 - 0.999999)
            int mtime = (int) (rlt * 1000); // 转换为毫秒部分 (0-999)
            long timestp = Long.valueOf(timestamp) * 1000 + mtime;
            
            // 调试日志 - 可以暂时开启查看转换情况
            // log.debug("时间戳转换: timestamp={}, timestampus={}, mtime={}, result={}", 
            //          timestamp, timestampus, mtime, timestp);
            
            return String.valueOf(timestp);
        } catch (Exception e) {
            log.error("时间戳转换失败: timestamp={}, timestampus={}", timestamp, timestampus, e);
            return String.valueOf(System.currentTimeMillis());
        }
    }

    /**
     * 停止数据接收
     */
    public CompletableFuture<Boolean> stopDataReceiving(String deviceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeviceConnection connection = deviceConnections.get(deviceId);
                if (connection != null && connection.isConnected.get()) {
                    
                    // 发送停止命令
                    String command = "false";
                    byte[] commandData = SocketTools.packSFream(command);
                    connection.outputStream.write(commandData);
                    connection.outputStream.flush();
                    
                    // 关闭连接
                    connection.isReceiving.set(false);
                    connection.isConnected.set(false);
                    
                    if (connection.receiveThread != null) {
                        connection.receiveThread.interrupt();
                    }
                    
                    // 停止CSV写入定时器
                    if (connection.imuWriteTask != null) {
                        connection.imuWriteTask.cancel(false);
                        log.info("停止设备 {} 的IMU CSV写入定时器", deviceId);
                    }
                    if (connection.gasWriteTask != null) {
                        connection.gasWriteTask.cancel(false);
                        log.info("停止设备 {} 的GAS CSV写入定时器", deviceId);
                    }
                    
                    // 停止定时预测任务
                    if (connection.predictionTask != null) {
                        connection.predictionTask.cancel(false);
                        log.info("停止设备 {} 的定时预测任务", deviceId);
                    }
                    
                    // 停止音频接收并保存文件
                    stopAudioReceiving(connection);
                    
                    // 保存剩余缓冲数据（在关闭定时器后）
                    saveRemainingData(connection);

                    // === 新增：停止时记录一次检查记录，并把 patient.checked 置 true ===
                    try {
                        // 1) 取会话元信息（停止后才调用，依赖上一步 startDataReceiving 时 setSessionMeta）
                        String pid   = csvDataService.getSessionPatientId(deviceId);
                        String pname = csvDataService.getSessionPatientName(deviceId);
                        String staff = csvDataService.getSessionDeviceName(deviceId); // 没有操作者就用设备名；也可换成当前登录用户

                        if (pid != null && !pid.isBlank()) {
                            // 2) 追加一条 check_record
                            CheckRecord rec = new CheckRecord();
                            rec.setPatientId(pid);
                            rec.setName((pname == null || pname.isBlank()) ? "未知" : pname);
                            rec.setStaff((staff == null || staff.isBlank()) ? "系统" : staff);
                            rec.setCheckTime(java.time.LocalDateTime.now(CheckRecord.ZONE_CN));
                            // ★ 这里的枚举值请替换为你项目里真实存在的那个（示例用 NORMAL 占位）
                            rec.setResult(cn.scut.raputa.enums.CheckResult.NORMAL);
                            checkRecordRepository.save(rec);

                            // 3) 如果患者 checked==false，则置 true
                            patientRepository.findById(pid).ifPresent(p -> {
                                if (!p.isChecked()) {
                                    p.setChecked(true);
                                    patientRepository.save(p);
                                }
                            });

                            log.info("已写入检查记录 & 标记患者({})为已检测", pid);
                        } else {
                            log.warn("停止检测：未能获取 patientId（deviceId={}），跳过检查记录写入", deviceId);
                        }
                    } catch (Exception e) {
                        log.error("停止检测时写入检查记录/更新患者状态失败：deviceId={}", deviceId, e);
                    }
                    
                    // 关闭CSV写入器
                    csvDataService.closeWriter(deviceId);
                    
                    if (connection.socket != null && !connection.socket.isClosed()) {
                        connection.socket.close();
                    }
                    
                    deviceConnections.remove(deviceId);
                    log.info("停止设备 {} 数据接收", deviceId);
                    return true;
                }
                return false;
                
            } catch (IOException e) {
                log.error("停止设备数据接收失败: {}", deviceId, e);
                return false;
            }
        });
    }

    /**
     * 数据接收循环
     */
    private void receiveDataLoop(DeviceConnection connection) {
        connection.isReceiving.set(true);
        byte[] receiveBuffer = new byte[1024];
        
        while (connection.isReceiving.get() && !Thread.currentThread().isInterrupted()) {
            try {
                if (connection.socket != null && connection.socket.isConnected()) {
                    int length = connection.inputStream.read(receiveBuffer);
                    if (length > 0) {
                        byte[] data = new byte[length];
                        System.arraycopy(receiveBuffer, 0, data, 0, length);
                        processReceivedData(data, connection);
                        connection.lastHeartbeat = LocalDateTime.now();
                    } else {
                        log.warn("设备 {} 连接断开", connection.deviceId);
                        break;
                    }
                } else {
                    break;
                }
            } catch (IOException e) {
                log.error("接收数据异常: {}", connection.deviceId, e);
                break;
            }
        }
        
        // 清理连接
        cleanupConnection(connection);
    }

    /**
     * 处理接收到的数据 - 参考原始TerminalRTData.java的逻辑
     */
    private void processReceivedData(byte[] data, DeviceConnection connection) {
        try {
            // 合并到缓冲区
            connection.buffer = SocketTools.byteArrAdd(connection.buffer, data);
            
            // 临时调试：打印原始数据
            if (data.length > 0) {
                log.debug("设备 {} 接收到 {} 字节数据", connection.deviceId, data.length);
            }
            
            int min = 16; // 最小包长度
            while (connection.buffer.length >= min) {
                int start = 0;
                byte[] fhead = SocketTools.subArray(connection.buffer, start, start + 4);
                if (!SocketTools.encodeHexString(fhead).equals("000055aa")) {
                    // 数据帧头无效，移除第一个字节继续
                    connection.buffer = SocketTools.getNewArray(connection.buffer, 1);
                    continue;
                }
                
                start = start + 12;
                byte[] fel = SocketTools.subArray(connection.buffer, start, start + 4);
                int sdleng = SocketTools.bytesToInt(fel);
                if (sdleng > 4 * 1024) {
                    log.warn("数据帧粘包");
                    connection.buffer = SocketTools.getNewArray(connection.buffer, 1);
                    continue;
                }
                
                if (connection.buffer.length < (sdleng + min)) {
                    // 数据帧不完整，等待更多数据
                    break;
                }
                
                List<byte[]> rlt = SocketTools.anlyBufData(connection.buffer);
                if (rlt != null) {
                    byte[] ft = rlt.get(2);
                    byte[] fds = rlt.get(3);
                    
                    String frameTypeStr = SocketTools.encodeHexString(ft);
                    
                    if ("00000001".equals(frameTypeStr)) {
                        // 控制返回结果
                        String dataStr = new String(fds, 0, fds.length);
                        log.debug("设备 {} 控制响应: {}", connection.deviceId, dataStr);
                        
                    } else if ("00000002".equals(frameTypeStr)) {
                        // IMU和GAS数据
                        String dataStr = new String(fds, 0, fds.length);
                        // 减少日志输出,避免影响性能
                        // log.debug("设备 {} 接收到传感器数据", connection.deviceId);
                        processSensorData(dataStr, connection.deviceId);
                    } else {
                        log.debug("设备 {} 接收到未知帧类型: {}", connection.deviceId, frameTypeStr);
                    }
                    
                    // 移除已处理的数据
                    connection.buffer = SocketTools.getNewArray(connection.buffer, min + sdleng);
                } else {
                    // 解析失败，移除第一个字节继续
                    connection.buffer = SocketTools.getNewArray(connection.buffer, 1);
                }
            }
            
        } catch (Exception e) {
            log.error("处理接收数据失败: {}", connection.deviceId, e);
        }
    }

    /**
     * 保存剩余缓冲数据到CSV - 参考原始项目的addGasIum方法
     */
    private void saveRemainingData(DeviceConnection connection) {
        try {
            // 保存剩余的IMU数据
            List<String[]> imuList = new ArrayList<>();
            while (connection.imuBuffer.getSize() > 0) {
                Object obj = connection.imuBuffer.poll();
                if (obj != null) {
                    String imuStr = (String) obj;
                    String[] valArr = getValForJsonStr(imuStr, 2);
                    if (valArr != null) {
                        imuList.add(valArr);
                    }
                }
            }
            if (!imuList.isEmpty()) {
                csvDataService.writeImuData(connection.deviceId, imuList);
                log.info("保存剩余 {} 条IMU数据", imuList.size());
            }
            
            // 保存剩余的GAS数据
            List<String[]> gasList = new ArrayList<>();
            while (connection.gasBuffer.getSize() > 0) {
                Object obj = connection.gasBuffer.poll();
                if (obj != null) {
                    String gasStr = (String) obj;
                    String[] valArr = getValForJsonStr(gasStr, 3);
                    if (valArr != null) {
                        gasList.add(valArr);
                    }
                }
            }
            if (!gasList.isEmpty()) {
                csvDataService.writeGasData(connection.deviceId, gasList);
                log.info("保存剩余 {} 条GAS数据", gasList.size());
            }
            
            log.info("设备 {} CSV数据统计: IMU={}, GAS={}", 
                    connection.deviceId, connection.imuCount, connection.gasCount);
            
        } catch (Exception e) {
            log.error("保存剩余数据失败: {}", connection.deviceId, e);
        }
    }

    /**
     * 处理传感器数据 - 放入CSV缓冲队列 + 推送到WebSocket
     * 参考原项目 TerminalRTData.anlyTcpData - 原项目不保存数据库,只写CSV
     */
    public void processSensorData(String jsonData, String deviceId) {
        try {
            // 验证JSON有效性
            if (!SocketTools.isJsonString(jsonData)) {
                log.warn("解析错误,非有效json: {}", jsonData);
                return;
            }
            
            DeviceConnection connection = deviceConnections.get(deviceId);
            if (connection == null) {
                return;
            }
            
            JsonNode jsonNode = SocketTools.getJsonObject(jsonData);
            if (jsonNode == null || jsonNode.isNull()) {
                return;
            }
            
            // 提取时间戳
            Long timestamp = jsonNode.has("timestamp") ? jsonNode.get("timestamp").asLong() : 0L;
            Long timestampus = jsonNode.has("timestampus") ? jsonNode.get("timestampus").asLong() : 0L;
            // 计算完整时间戳(毫秒)
            Long fullTimestamp = timestamp * 1000 + (timestampus / 1000);
            
            // 处理IMU数据 - 舍弃第一条,后续数据放入缓冲队列 + 推送WebSocket
            JsonNode accNode = jsonNode.get("acc");
            if (accNode != null && !accNode.isNull()) {
                if (connection.imuFirstData) {
                    // 舍弃第一条IMU数据
                    connection.imuFirstData = false;
                    log.info("设备 {} 舍弃首条IMU数据", deviceId);
                } else {
                    // 标记IMU数据就绪
                    if (!connection.imuReady.get()) {
                        connection.imuReady.set(true);
                        log.info("设备 {} IMU数据就绪", deviceId);
                        checkAndStartRecording(connection);
                    }
                    
                    // 放入CSV缓冲队列 (所有数据都保存)
                    connection.imuBuffer.put(jsonData);
                    
                    // 推送到WebSocket - 抽样推送,降低频率 (每20个推送1个,从2000Hz降到100Hz)
                    connection.imuPushCount++;
                    if (connection.imuPushCount % 20 == 0 && accNode.has("x") && accNode.has("y") && accNode.has("z")) {
                        Integer x = accNode.get("x").asInt();
                        Integer y = accNode.get("y").asInt();
                        Integer z = accNode.get("z").asInt();
                        webSocketService.pushImuData(deviceId, fullTimestamp, x, y, z);
                    }
                }
            }
            
            // 处理GAS数据 - 舍弃第一条,后续数据放入缓冲队列 + 推送WebSocket
            JsonNode flowNode = jsonNode.get("flow");
            if (flowNode != null && !flowNode.isNull()) {
                if (connection.gasFirstData) {
                    // 舍弃第一条GAS数据
                    connection.gasFirstData = false;
                    log.info("设备 {} 舍弃首条GAS数据", deviceId);
                } else {
                    // 标记GAS数据就绪
                    if (!connection.gasReady.get()) {
                        connection.gasReady.set(true);
                        log.info("设备 {} GAS数据就绪", deviceId);
                        checkAndStartRecording(connection);
                    }
                    
                    // 放入CSV缓冲队列 (所有数据都保存)
                    connection.gasBuffer.put(jsonData);
                    
                    // 推送到WebSocket - 抽样推送 (每2个推送1个,降低一半频率)
                    connection.gasPushCount++;
                    if (connection.gasPushCount % 2 == 0) {
                        Integer flow = flowNode.asInt();
                        webSocketService.pushGasData(deviceId, fullTimestamp, flow);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("处理传感器数据失败: deviceId={}, error={}", deviceId, e.getMessage());
        }
    }

    /**
     * 清理连接
     */
    private void cleanupConnection(DeviceConnection connection) {
        try {
            if (connection.inputStream != null) {
                connection.inputStream.close();
            }
            if (connection.outputStream != null) {
                connection.outputStream.close();
            }
            if (connection.socket != null && !connection.socket.isClosed()) {
                connection.socket.close();
            }
        } catch (IOException e) {
            log.error("清理连接失败", e);
        }
    }

    /**
     * 获取设备连接状态
     */
    public boolean isDeviceConnected(String deviceId) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        return connection != null && connection.isConnected.get();
    }

    /**
     * 获取设备接收状态
     */
    public boolean isDeviceReceiving(String deviceId) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        return connection != null && connection.isReceiving.get();
    }

    /**
     * 获取所有连接的设备
     */
    public List<String> getConnectedDevices() {
        return deviceConnections.entrySet().stream()
                .filter(entry -> entry.getValue().isConnected.get())
                .map(entry -> entry.getKey())
                .toList();
    }

    /**
     * 获取设备最后心跳时间
     */
    public LocalDateTime getLastHeartbeat(String deviceId) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        return connection != null ? connection.lastHeartbeat : null;
    }
    
    /**
     * 获取CSV文件列表
     */
    public List<String> getCsvFileList() {
        return csvDataService.getCsvFileList();
    }
    
    /**
     * 删除CSV文件
     */
    public boolean deleteCsvFile(String fileName) {
        return csvDataService.deleteCsvFile(fileName);
    }
    
    /**
     * 获取设备数据统计信息
     */
    public DeviceDataStats getDeviceDataStats(String deviceId) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        if (connection != null) {
            return new DeviceDataStats(
                deviceId,
                connection.imuCount,
                connection.gasCount,
                connection.imuBuffer.getSize(),
                connection.gasBuffer.getSize()
            );
        }
        return new DeviceDataStats(deviceId, 0, 0, 0, 0);
    }
    
    /**
     * 启动音频RTSP接收 - 参考原始项目的WaveFrom.play()
     */
    private void startAudioReceiving(DeviceConnection connection) {
        log.info("设备 {} 开始启动音频接收线程...", connection.deviceId);
        
        connection.audioThread = new Thread(() -> {
            while (connection.isConnected.get() && connection.audioRetryCount < DeviceConnection.MAX_AUDIO_RETRY) {
                boolean shouldRetry = false; // 标记是否需要重试
                try {
                    // 构建RTSP URL
                    String rtspUrl = "rtsp://" + connection.deviceIp + ":8554/stream/audio";
                    log.info("开始连接音频RTSP: {} (尝试 {}/{})", rtspUrl, connection.audioRetryCount + 1, DeviceConnection.MAX_AUDIO_RETRY);
                    
                    // 创建FFmpeg音频抓取器
                    connection.audioGrabber = FFmpegFrameGrabber.createDefault(rtspUrl);
                    connection.audioGrabber.setOption("rtsp_transport", "tcp");
                    connection.audioGrabber.setTimeout(5000);
                    connection.audioGrabber.start();
                    
                    connection.audioReceiving.set(true);
                    log.info("音频RTSP连接成功: {}", rtspUrl);
                    
                    // 获取第一帧并保存
                    Frame firstFrame = connection.audioGrabber.grabSamples();
                    if (firstFrame != null && firstFrame.audioChannels > 0) {
                        // 保存第一帧，等待所有数据就绪
                        connection.audioFirstFrame = firstFrame;
                        log.info("设备 {} 音频第一帧已抓取 (声道={}, 采样={}/s)", 
                            connection.deviceId, firstFrame.audioChannels, connection.audioGrabber.getSampleRate());
                        
                        // 标记音频数据就绪
                        if (!connection.audioReady.get()) {
                            connection.audioReady.set(true);
                            log.info("设备 {} 音频数据就绪", connection.deviceId);
                            checkAndStartRecording(connection);
                        }
                        
                        // 重置重试计数
                        connection.audioRetryCount = 0;
                        
                        // 持续接收音频帧
                        while (connection.audioReceiving.get() && !Thread.currentThread().isInterrupted()) {
                            Frame frame = connection.audioGrabber.grabSamples();
                            if (frame != null && frame.audioChannels > 0) {
                                // 立即推送到WebSocket（不受录制状态影响）
                                pushAudioToWebSocket(connection, frame);
                                // 录制音频帧（仅在所有数据就绪后）
                                recordAudioFrame(connection, frame);
                            } else {
                                // 检查是否是因为用户主动停止
                                if (connection.audioReceiving.get() && connection.isConnected.get()) {
                                    log.warn("设备 {} 音频流中断，尝试重连", connection.deviceId);
                                    shouldRetry = true;
                                } else {
                                    log.info("设备 {} 音频接收正常停止", connection.deviceId);
                                }
                                break;
                            }
                        }
                    } else {
                        log.warn("设备 {} 未能抓取到有效的音频第一帧", connection.deviceId);
                        shouldRetry = true; // 第一帧失败，需要重试
                    }
                    
                } catch (Exception e) {
                    log.error("设备 {} 音频接收异常 (尝试 {}/{}): {}", 
                            connection.deviceId, connection.audioRetryCount + 1, DeviceConnection.MAX_AUDIO_RETRY, e.getMessage());
                    shouldRetry = true; // 异常情况，需要重试
                }
                
                // 只有在需要重试时才清理资源
                if (shouldRetry && connection.isConnected.get()) {
                    log.debug("设备 {} 准备重试，清理音频资源", connection.deviceId);
                    cleanupAudioResources(connection);
                    
                    // 如果还在连接状态且未达到最大重试次数，则重试
                    if (connection.audioRetryCount < DeviceConnection.MAX_AUDIO_RETRY) {
                        connection.audioRetryCount++;
                        try {
                            log.info("设备 {} 等待0.5秒后重试音频连接...", connection.deviceId);
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            log.info("设备 {} 音频重试被中断", connection.deviceId);
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    // 正常停止或用户主动停止，不清理资源，保留录制器用于后续保存
                    log.debug("设备 {} 音频接收循环退出（不清理资源）", connection.deviceId);
                    break;
                }
            }
            
            // 达到最大重试次数
            if (connection.audioRetryCount >= DeviceConnection.MAX_AUDIO_RETRY) {
                log.error("设备 {} 音频连接失败，已达到最大重试次数 {}", connection.deviceId, DeviceConnection.MAX_AUDIO_RETRY);
            }
        });
        connection.audioThread.setDaemon(true);
        connection.audioThread.start();
        
        log.info("设备 {} 音频接收线程已启动", connection.deviceId);
    }
    
    /**
     * 清理音频资源 - 参考原始项目的stopAndStartWaveGrabber
     * 注意: 这个方法用于重试连接时清理，不保存文件
     */
    private void cleanupAudioResources(DeviceConnection connection) {
        try {
            connection.audioReceiving.set(false);
            
            // 先关闭抓取器（停止接收新数据）
            if (connection.audioGrabber != null) {
                try {
                    connection.audioGrabber.release();
                    connection.audioGrabber.stop();
                    connection.audioGrabber.close();
                } catch (Exception e) {
                    log.warn("关闭音频抓取器异常: {}", e.getMessage());
                }
                connection.audioGrabber = null;
            }
            
            // 关闭录制器（丢弃未保存的数据）
            if (connection.audioRecorder != null) {
                synchronized (connection.audioRecorder) {
                    try {
                        connection.audioRecorder.release();
                        connection.audioRecorder.stop();
                        connection.audioRecorder.close();
                    } catch (Exception e) {
                        log.warn("关闭音频录制器异常: {}", e.getMessage());
                    }
                    connection.audioRecorder = null;
                }
            }
            
            log.debug("设备 {} 音频资源已清理（准备重试）", connection.deviceId);
            
        } catch (Exception e) {
            log.error("清理音频资源异常", e);
        }
    }
    
    /**
     * 检查并启动录制 - 当三种数据都就绪时
     */
    private void checkAndStartRecording(DeviceConnection connection) {
        if (connection.checkAllDataReady()) {
            log.info("设备 {} 所有数据就绪 (IMU, GAS, AUDIO)，开始保存文件", connection.deviceId);
            // 注意：CSV写入定时器已在startCsvWriteTimers中启动，会自动检查allDataReady标志
            
            // 立即初始化音频录制器（使用保存的第一帧）
            if (connection.audioFirstFrame != null && connection.audioRecorder == null) {
                initAudioRecorder(connection, connection.audioFirstFrame);
                connection.audioStartTimestamp = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * 初始化音频录制器 - 参考原始项目的setWaveRecorder方法
     */
    private void initAudioRecorder(DeviceConnection connection, Frame firstFrame) {
        try {
            // 检查是否已经初始化过
            if (connection.audioRecorder != null) {
                return;
            }
            
            // 生成文件名: audio.wav （保存到会话文件夹中）
            String fileName = "audio.wav";
            
            // 获取会话文件夹
            String sessionFolder = csvDataService.getSessionFolder(connection.deviceId);
            if (sessionFolder == null) {
                log.error("设备 {} 的会话文件夹不存在，无法保存音频文件", connection.deviceId);
                return;
            }
            File audioFile = new File(sessionFolder, fileName);
            connection.audioFilePath = audioFile.getAbsolutePath();
            
            // 记录原始声道数
            int originalChannels = connection.audioGrabber.getAudioChannels();
            int originalSampleRate = connection.audioGrabber.getSampleRate();
            log.info("设备 {} 音频参数: 采样率={}Hz, 原始声道数={}", 
                connection.deviceId, originalSampleRate, originalChannels);
            
            // 强制使用单声道 - 解决RTSP流双声道但数据不匹配的问题
            int channelsToUse = 1;
            
            // 创建录制器 - 强制使用单声道
            connection.audioRecorder = new FFmpegFrameRecorder(
                connection.audioFilePath, 
                channelsToUse  // 强制单声道
            );
            
            // 设置音频参数 - 参考原始项目
            connection.audioRecorder.setAudioOption("crf", "0");
            connection.audioRecorder.setAudioQuality(0);
            connection.audioRecorder.setAudioChannels(channelsToUse);  // 强制单声道
            connection.audioRecorder.setSampleRate(originalSampleRate);
            connection.audioRecorder.setFormat("wav");
            connection.audioRecorder.setAudioCodec(avcodec.AV_CODEC_ID_PCM_S16LE);
            
            // 启动录制器
            connection.audioRecorder.start();
            
            // 录制第一帧
            connection.audioRecorder.setTimestamp(firstFrame.timestamp);
            connection.audioRecorder.recordSamples(firstFrame.samples);
            
            log.info("设备 {} 音频录制器初始化成功,文件: {}, 输出声道数: {}", 
                connection.deviceId, fileName, channelsToUse);
            
        } catch (Exception e) {
            log.error("设备 {} 初始化音频录制器失败", connection.deviceId, e);
        }
    }
    
    /**
     * 录制音频帧 - 参考原始项目的waveRecorderSt方法
     */
    private void recordAudioFrame(DeviceConnection connection, Frame frame) {
        try {
            // 仅在所有数据就绪后才录制
            if (!connection.allDataReady.get()) {
                return;
            }
            
            // 如果录制器还未初始化（所有数据刚就绪），先初始化
            if (connection.audioRecorder == null && connection.audioFirstFrame != null) {
                initAudioRecorder(connection, connection.audioFirstFrame);
                connection.audioStartTimestamp = System.currentTimeMillis();
            }
            
            // 检查是否还在接收状态（避免停止后继续写入）
            if (!connection.audioReceiving.get()) {
                return;
            }
            
            if (connection.audioRecorder != null) {
                synchronized (connection.audioRecorder) {
                    // 再次检查，防止在等待锁期间被关闭
                    if (connection.audioRecorder != null && connection.audioReceiving.get()) {
                        connection.audioRecorder.setTimestamp(frame.timestamp);
                        connection.audioRecorder.recordSamples(frame.samples);
                        connection.audioFrameCount++;
                    }
                }
            }
        } catch (Exception e) {
            // 不输出完整堆栈，避免日志滥满
            log.warn("设备 {} 录制音频帧失败: {}", connection.deviceId, e.getMessage());
        }
    }
    
    /**
     * 提取音频数据并降采样推送到WebSocket
     */
    private void pushAudioToWebSocket(DeviceConnection connection, Frame frame) {
        try {
            if (frame.samples == null || frame.samples.length == 0) {
                return;
            }
            
            connection.audioPushMethodCallCount++;
            
            // 获取第一个声道的数据
            java.nio.Buffer buffer = frame.samples[0];
            
            // 支持ShortBuffer和FloatBuffer两种类型
            if (buffer instanceof java.nio.ShortBuffer) {
                // ShortBuffer类型 - 转换为浮点数
                java.nio.ShortBuffer shortBuffer = (java.nio.ShortBuffer) buffer;
                int capacity = shortBuffer.capacity();
                
                // 降采样：每 AUDIO_DOWNSAMPLE_RATIO 个样本取一个
                for (int i = 0; i < capacity; i++) {
                    connection.audioPushCount++;
                    if (connection.audioPushCount % DeviceConnection.AUDIO_DOWNSAMPLE_RATIO == 0) {
                        // Short值范围是-32768到32767，转换为-1.0到1.0的浮点数
                        short shortValue = shortBuffer.get(i);
                        float amplitude = shortValue / 32768.0f;
                        long timestamp = System.currentTimeMillis();
                        webSocketService.pushAudioData(connection.deviceId, timestamp, amplitude);
                    }
                }
            } else if (buffer instanceof java.nio.FloatBuffer) {
                // FloatBuffer类型
                java.nio.FloatBuffer floatBuffer = (java.nio.FloatBuffer) buffer;
                int capacity = floatBuffer.capacity();
                
                // 降采样：每 AUDIO_DOWNSAMPLE_RATIO 个样本取一个
                for (int i = 0; i < capacity; i++) {
                    connection.audioPushCount++;
                    if (connection.audioPushCount % DeviceConnection.AUDIO_DOWNSAMPLE_RATIO == 0) {
                        float amplitude = floatBuffer.get(i);
                        long timestamp = System.currentTimeMillis();
                        webSocketService.pushAudioData(connection.deviceId, timestamp, amplitude);
                    }
                }
            } else {
                log.warn("[音频推送] 设备 {} 音频数据类型不支持，实际类型: {}", 
                    connection.deviceId, buffer.getClass().getName());
                return;
            }
            
        } catch (Exception e) {
            log.error("设备 {} 推送音频数据到WebSocket失败", connection.deviceId, e);
        }
    }
    
    /**
     * 停止音频接收并保存文件 - 参考原始项目的playStop方法
     */
    private void stopAudioReceiving(DeviceConnection connection) {
        try {
            log.info("开始停止设备 {} 的音频接收...", connection.deviceId);
            
            // 1. 先标记停止接收，让音频线程停止抓取新帧
            connection.audioReceiving.set(false);
            
            // 2. 中断音频线程，停止帧抓取循环
            if (connection.audioThread != null) {
                connection.audioThread.interrupt();
                try {
                    // 等待音频线程结束（最多2秒）
                    connection.audioThread.join(2000);
                    if (connection.audioThread.isAlive()) {
                        log.warn("设备 {} 音频线程未能在2秒内结束", connection.deviceId);
                    }
                } catch (InterruptedException ie) {
                    log.warn("等待音频线程结束时被中断");
                    Thread.currentThread().interrupt();
                }
            }
            
            // 3. 等待一小段时间确保最后的数据被处理完毕
            Thread.sleep(500);
            
            // 4. 计算音频时长（在关闭录制器之前）
            long audioEndTimestamp = System.currentTimeMillis();
            double audioDurationSeconds = 0.0;
            if (connection.audioStartTimestamp > 0) {
                long durationMs = audioEndTimestamp - connection.audioStartTimestamp;
                audioDurationSeconds = durationMs / 1000.0;
            }
            
            // 5. 安全关闭录制器（确保数据刷新到磁盘）
            boolean hasRecorder = connection.audioRecorder != null;
            String audioFilePath = connection.audioFilePath; // 保存路径，防止被清空
            if (connection.audioRecorder != null) {
                synchronized (connection.audioRecorder) {
                    try {
                        // 正确的关闭顺序：stop() -> release() -> close()
                        // stop() 停止编码并写入文件尾
                        connection.audioRecorder.stop();
                        log.debug("设备 {} 音频录制器已停止", connection.deviceId);
                        
                        // release() 释放编码器资源
                        connection.audioRecorder.release();
                        log.debug("设备 {} 音频录制器已释放", connection.deviceId);
                        
                        // close() 关闭文件句柄
                        connection.audioRecorder.close();
                        log.debug("设备 {} 音频录制器已关闭", connection.deviceId);
                        
                        connection.audioRecorder = null;
                    } catch (Exception e) {
                        log.error("设备 {} 关闭音频录制器时出错: {}", connection.deviceId, e.getMessage(), e);
                    }
                }
            }
            
            // 等待文件系统刷新（特别重要！）
            Thread.sleep(200);
            
            // 6. 关闭抓取器
            if (connection.audioGrabber != null) {
                try {
                    connection.audioGrabber.release();
                    connection.audioGrabber.stop();
                    connection.audioGrabber.close();
                    connection.audioGrabber = null;
                    log.debug("设备 {} 音频抓取器已关闭", connection.deviceId);
                } catch (Exception e) {
                    log.error("设备 {} 关闭音频抓取器时出错: {}", connection.deviceId, e.getMessage());
                }
            }
            
            // 7. 输出详细信息
            log.info("========================================");
            File finalAudioFile = null; // 保存文件对象用于最终验证
            if (hasRecorder && audioFilePath != null) {
                // 检查文件是否存在并获取大小
                File audioFile = new File(audioFilePath);
                finalAudioFile = audioFile; // 保存引用
                if (audioFile.exists()) {
                    long fileSize = audioFile.length();
                    log.info("设备 {} 音频文件已保存: {}", connection.deviceId, audioFilePath);
                    log.info("音频文件大小: {} KB ({} bytes)", fileSize / 1024, fileSize);
                    log.info("音频时长: {} 秒", String.format("%.2f", audioDurationSeconds));
                    log.info("音频帧数: {} 帧", connection.audioFrameCount);
                    
                    // 再次验证文件是否真的存在且可读
                    if (!audioFile.canRead()) {
                        log.error("警告：音频文件存在但不可读！路径: {}", audioFilePath);
                    }
                } else {
                    log.error("设备 {} 音频文件未找到: {}", connection.deviceId, audioFilePath);
                    // 列出目录中的文件，帮助诊断
                    File parentDir = audioFile.getParentFile();
                    if (parentDir != null && parentDir.exists()) {
                        String[] files = parentDir.list();
                        if (files != null) {
                            log.error("会话目录中的文件: {}", String.join(", ", files));
                        }
                    }
                }
            } else {
                log.warn("设备 {} 音频录制器未初始化，没有保存音频文件", connection.deviceId);
                // 详细诊断原因
                log.warn("数据就绪状态: IMU={}, GAS={}, AUDIO={}, 全部就绪={}", 
                    connection.imuReady.get(), 
                    connection.gasReady.get(), 
                    connection.audioReady.get(),
                    connection.allDataReady.get());
                
                if (connection.audioRetryCount >= DeviceConnection.MAX_AUDIO_RETRY) {
                    log.warn("原因: 音频连接失败次数超过最大重试次数 {}", DeviceConnection.MAX_AUDIO_RETRY);
                } else if (!connection.audioReady.get()) {
                    log.warn("原因: 音频数据未就绪 - 可能是RTSP连接失败或未抓取到音频帧");
                } else if (!connection.allDataReady.get()) {
                    log.warn("原因: 其他数据未就绪，未触发录制器初始化");
                } else if (connection.audioFirstFrame == null) {
                    log.warn("原因: 音频第一帧未保存");
                }
            }
            log.info("========================================");
            
            log.info("设备 {} 音频接收已停止", connection.deviceId);
            
            // 最终验证：在方法结束前再次检查文件是否还在
            if (finalAudioFile != null) {
                try {
                    Thread.sleep(100); // 等待一小段时间
                    if (finalAudioFile.exists()) {
                        log.info("[最终验证] 音频文件仍然存在: {} (大小: {} bytes)", 
                            finalAudioFile.getAbsolutePath(), finalAudioFile.length());
                    } else {
                        log.error("[最终验证] 警告！音频文件已消失: {}", finalAudioFile.getAbsolutePath());
                        // 列出目录内容
                        File parentDir = finalAudioFile.getParentFile();
                        if (parentDir != null && parentDir.exists()) {
                            String[] files = parentDir.list();
                            if (files != null) {
                                log.error("[最终验证] 会话目录中的文件: {}", String.join(", ", files));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[最终验证] 检查文件时出错", e);
                }
            }
            
        } catch (Exception e) {
            log.error("设备 {} 停止音频接收失败", connection.deviceId, e);
        }
    }
    
    /**
     * 设备数据统计信息
     */
    public record DeviceDataStats(
        String deviceId,
        int imuCount,
        int gasCount,
        int imuBufferSize,
        int gasBufferSize
    ) {}
}
