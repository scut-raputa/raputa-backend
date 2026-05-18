package cn.scut.raputa.service;

import cn.scut.raputa.config.InferenceProperties;
import cn.scut.raputa.dto.DeviceOccupationDTO;
import cn.scut.raputa.dto.RealtimeConnectResultDTO;
import cn.scut.raputa.entity.CaptureSession;
import cn.scut.raputa.entity.DeviceSessionLock;
import cn.scut.raputa.repository.CaptureSessionRepository;
import cn.scut.raputa.utils.DataBuffer;
import cn.scut.raputa.utils.SocketTools;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeDataService {

    private final CsvDataService csvDataService;
    private final WebSocketService webSocketService;
    private final ModelPredictionService modelPredictionService;
    private final CaptureSessionRepository captureSessionRepository;
    private final SessionPathResolver sessionPathResolver;
    private final FileStorageService fileStorageService;
    private final InferenceProperties inferenceProperties;
    private final DeviceLockService deviceLockService;

    @Value("${raputa.device-lock.heartbeat-interval-seconds:15}")
    private long lockHeartbeatIntervalSeconds;

    private final ConcurrentHashMap<String, DeviceConnection> deviceConnections = new ConcurrentHashMap<>();

    private final ScheduledExecutorService csvWriteScheduler = Executors.newScheduledThreadPool(2);

    private static class DeviceConnection {
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;
        private Thread receiveThread;
        private String sessionId;
        private final AtomicBoolean isConnected = new AtomicBoolean(false);
        private final AtomicBoolean isReceiving = new AtomicBoolean(false);
        private byte[] buffer = new byte[0];
        private LocalDateTime lastHeartbeat;
        private String deviceId;

        private final DataBuffer imuBuffer = new DataBuffer(99999999);
        private final DataBuffer gasBuffer = new DataBuffer(99999999);

        private int imuCount = 0;
        private int gasCount = 0;

        private boolean imuFirstData = true;
        private boolean gasFirstData = false;

        private int imuPushCount = 0;
        private int gasPushCount = 0;

        private java.util.concurrent.ScheduledFuture<?> imuWriteTask;
        private java.util.concurrent.ScheduledFuture<?> gasWriteTask;

        private FFmpegFrameGrabber audioGrabber;
        private FFmpegFrameRecorder audioRecorder;
        private Thread audioThread;
        private final AtomicBoolean audioReceiving = new AtomicBoolean(false);
        private String deviceIp;
        private String taskType = "asp";
        private String audioFilePath;
        private int audioRetryCount = 0;
        private static final int MAX_AUDIO_RETRY = 5;
        private long audioStartTimestamp = 0;
        private long audioFrameCount = 0;
        private Frame audioFirstFrame;

        private final AtomicBoolean imuReady = new AtomicBoolean(false);
        private final AtomicBoolean gasReady = new AtomicBoolean(false);
        private final AtomicBoolean audioReady = new AtomicBoolean(false);
        private final AtomicBoolean allDataReady = new AtomicBoolean(false);

        private int audioPushCount = 0;
        private static final int AUDIO_DOWNSAMPLE_RATIO = 240; // 48000 / 200 = 240

        private java.util.concurrent.ScheduledFuture<?> predictionTask;
        private java.util.concurrent.ScheduledFuture<?> lockHeartbeatTask;
        private final AtomicBoolean manualSegmentationMode = new AtomicBoolean(false);
        private final Queue<ManualSwallowSegment> pendingManualSegments = new ConcurrentLinkedQueue<>();
        private final long startedNanoTime = System.nanoTime();

        public DeviceConnection(String deviceId) {
            this.deviceId = deviceId;
            this.lastHeartbeat = LocalDateTime.now();
        }

        public boolean checkAllDataReady() {
            if (!allDataReady.get() && imuReady.get() && gasReady.get() && audioReady.get()) {
                allDataReady.set(true);
                return true;
            }
            return allDataReady.get();
        }
    }

    private record ManualSwallowSegment(double startSec, double endSec) {
    }

    private record DeviceLease(
            String sessionId,
            String deviceId,
            String patientId,
            String patientName,
            LocalDateTime startedAt) {
    }

    public CompletableFuture<Boolean> startDataReceiving(String deviceIp, String deviceId) {
        return startDataReceiving(deviceIp, deviceId, deviceId, "", "", "asp")
                .thenApply(RealtimeConnectResultDTO::isSuccess);
    }

    public CompletableFuture<RealtimeConnectResultDTO> startDataReceiving(
            String deviceIp,
            String deviceId,
            String deviceName,
            String patientId,
            String patientName) {
        return startDataReceiving(deviceIp, deviceId, deviceName, patientId, patientName, "asp");
    }

    public CompletableFuture<RealtimeConnectResultDTO> startDataReceiving(
            String deviceIp,
            String deviceId,
            String deviceName,
            String patientId,
            String patientName,
            String taskType) {
        return startDataReceiving(deviceIp, deviceId, deviceName, patientId, patientName, taskType, "realtime-connect");
    }

    public CompletableFuture<RealtimeConnectResultDTO> startDataReceiving(
            String deviceIp,
            String deviceId,
            String deviceName,
            String patientId,
            String patientName,
            String taskType,
            String holder) {

        return CompletableFuture.supplyAsync(() -> {
            Socket socket = null;
            String sessionId = UUID.randomUUID().toString();
            String normalizedTaskType = normalizeTaskType(taskType);

            DeviceLease existingLease = tryAcquireDeviceLease(deviceId, sessionId, patientId, patientName, holder);
            if (existingLease != null) {
                DeviceOccupationDTO occupation = toOccupation(existingLease, "设备已被占用，当前会话无法连接");
                log.warn("设备占用冲突: deviceId={}, occupiedBySession={}, patientId={}",
                        deviceId, existingLease.sessionId, existingLease.patientId);
                return RealtimeConnectResultDTO.occupied(deviceId, occupation, occupation.getReason());
            }

            try {
                CaptureSession captureSession = createRealtimeCaptureSession(
                    sessionId,
                    deviceId,
                    patientId,
                    patientName,
                    normalizedTaskType);

                DeviceConnection connection = new DeviceConnection(deviceId);
                connection.deviceIp = deviceIp;
                connection.sessionId = sessionId;
                connection.taskType = normalizedTaskType;

                csvDataService.setSessionMeta(
                    deviceId,
                    patientId,
                    patientName,
                    deviceName,
                    sessionId,
                    captureSession.getSessionDir());

                socket = new Socket(Proxy.NO_PROXY);
                socket.connect(new InetSocketAddress(deviceIp, 6667), 5000);
                socket.setSoTimeout(15000);
                connection.socket = socket;
                connection.inputStream  = socket.getInputStream();
                connection.outputStream = socket.getOutputStream();
                connection.isConnected.set(true);

                String command = "true";
                byte[] commandData = SocketTools.packSFream(command);
                connection.outputStream.write(commandData);
                connection.outputStream.flush();

                log.info("成功连接到设备 {}:{}，开始接收数据", deviceIp, 6667);

                connection.receiveThread = new Thread(() -> receiveDataLoop(connection));
                connection.receiveThread.setDaemon(true);
                connection.receiveThread.start();

                startCsvWriteTimers(connection);

                startAudioReceiving(connection);

                startPredictionTimer(connection);

                startLockHeartbeat(connection);

                updateCaptureSessionStatus(sessionId, "PROCESSING", null, null, null);

                deviceConnections.put(deviceId, connection);
                log.info("设备连接成功: deviceId={}, sessionId={}, taskType={}",
                        deviceId, sessionId, normalizedTaskType);
                return RealtimeConnectResultDTO.success(deviceId, sessionId);

            } catch (Exception e) {
                log.error("连接设备失败: {}", deviceIp, e);
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        log.warn("关闭失败的设备 socket 时出错", ex);
                    }
                }
                updateCaptureSessionStatus(sessionId, "FAILED", null, null, null);
                releaseDeviceLease(deviceId, sessionId, "设备连接失败");
                return RealtimeConnectResultDTO.failed(deviceId, "设备连接失败");
            }
        });
    }

    private String normalizeTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return "asp";
        }
        String normalized = taskType.trim().toLowerCase();
        if ("dys".equals(normalized)
                || "dysphagia".equals(normalized)
                || normalized.contains("吞咽障碍")) {
            return "dys";
        }
        return "asp";
    }

    private DeviceLease tryAcquireDeviceLease(String deviceId, String sessionId, String patientId, String patientName, String holder) {
        DeviceLockService.LockAcquireResult result = deviceLockService.tryAcquire(
                deviceId,
                sessionId,
                patientId == null ? "" : patientId.trim(),
                patientName == null ? "" : patientName.trim(),
                holder == null || holder.isBlank() ? "realtime-connect" : holder.trim());

        if (result.acquired()) {
            return null;
        }

        DeviceSessionLock lock = result.lock();
        if (lock == null) {
            return new DeviceLease(
                    "unknown",
                    deviceId,
                    patientId == null ? "" : patientId.trim(),
                    patientName == null ? "" : patientName.trim(),
                    LocalDateTime.now(CaptureSession.ZONE_CN));
        }

        return new DeviceLease(
                lock.getSessionId(),
                lock.getDeviceId(),
                lock.getPatientId(),
                lock.getPatientNameSnapshot(),
                lock.getStartedAt());
    }

    private void releaseDeviceLease(String deviceId, String sessionId, String reason) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }

        boolean released = deviceLockService.release(deviceId, sessionId, reason, false);
        if (!released) {
            log.warn("释放设备占用失败(会话不匹配): deviceId={}, sessionId={}, reason={}", deviceId, sessionId, reason);
        }
    }

    private DeviceOccupationDTO toOccupation(DeviceLease lease, String reason) {
        return DeviceOccupationDTO.builder()
                .deviceId(lease.deviceId)
                .occupied(true)
                .patientId(lease.patientId)
                .patientName(lease.patientName)
                .startedAt(lease.startedAt)
                .reason(reason)
                .build();
    }

    private CaptureSession createRealtimeCaptureSession(
            String sessionId,
            String deviceId,
            String patientId,
            String patientName,
            String taskType) {
        String sessionKey = sessionPathResolver.buildSessionKey("realtime", patientId, patientName);
        Path sessionDir = fileStorageService.ensureSessionDirectory(sessionKey);
        String relativeSessionDir = fileStorageService.toRelativePath(sessionDir);
        LocalDateTime now = LocalDateTime.now(CaptureSession.ZONE_CN);

        CaptureSession session = CaptureSession.builder()
                .id(sessionId)
                .mode("REALTIME")
                .status("CREATED")
                .patientId(patientId == null ? "" : patientId.trim())
                .deviceId(deviceId)
                .sessionKey(sessionKey)
                .patientNameSnapshot(patientName == null ? "" : patientName.trim())
                .sessionDir(relativeSessionDir)
                .inferenceServiceUrl(inferenceBaseUrlForTask(taskType))
                .startedAt(now)
                .build();
        return captureSessionRepository.save(session);
    }

    private String inferenceBaseUrlForTask(String taskType) {
        return "dys".equalsIgnoreCase(taskType)
                ? inferenceProperties.getDysphagiaBaseUrl()
                : inferenceProperties.getAspirationBaseUrl();
    }

    private void updateCaptureSessionStatus(
            String sessionId,
            String status,
            LocalDateTime stoppedAt,
            LocalDateTime finalizedAt,
            String modelSnapshotJson) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        captureSessionRepository.findById(sessionId).ifPresent(session -> {
            if (status != null && !status.isBlank()) {
                session.setStatus(status);
            }
            if (stoppedAt != null) {
                session.setStoppedAt(stoppedAt);
            }
            if (finalizedAt != null) {
                session.setFinalizedAt(finalizedAt);
            }
            if (modelSnapshotJson != null) {
                session.setModelSnapshotJson(modelSnapshotJson);
            }
            captureSessionRepository.save(session);
        });
    }

    private void startLockHeartbeat(DeviceConnection connection) {
        if (connection.lockHeartbeatTask != null) {
            connection.lockHeartbeatTask.cancel(false);
        }
        long interval = Math.max(lockHeartbeatIntervalSeconds, 5);
        connection.lockHeartbeatTask = csvWriteScheduler.scheduleAtFixedRate(() -> {
            try {
                deviceLockService.heartbeat(connection.deviceId, connection.sessionId);
            } catch (Exception ex) {
                log.warn("设备锁心跳失败: deviceId={}, sessionId={}, reason={}",
                        connection.deviceId, connection.sessionId, ex.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    public boolean setSegmentationMode(String deviceId, String mode) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        if (connection == null) {
            return false;
        }

        boolean manual = "MANUAL".equalsIgnoreCase(mode);
        connection.manualSegmentationMode.set(manual);
        connection.pendingManualSegments.clear();
        log.info("设备 {} 实时分割模式切换为 {}", deviceId, manual ? "MANUAL" : "AUTO");
        return true;
    }

    public boolean addManualSwallowSegment(String deviceId, double startSec, double endSec) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        if (connection == null || !connection.manualSegmentationMode.get()) {
            return false;
        }
        if (!Double.isFinite(startSec) || !Double.isFinite(endSec) || endSec <= startSec) {
            return false;
        }

        connection.pendingManualSegments.add(new ManualSwallowSegment(startSec, endSec));
        log.info("设备 {} 收到人工吞咽段: {}s - {}s", deviceId, startSec, endSec);
        return true;
    }

    private void startCsvWriteTimers(DeviceConnection connection) {

        if (connection.imuWriteTask != null) {
            connection.imuWriteTask.cancel(false);
        }
        if (connection.gasWriteTask != null) {
            connection.gasWriteTask.cancel(false);
        }

        connection.imuWriteTask = csvWriteScheduler.scheduleAtFixedRate(() -> {
            try {
                writeImuDataToCsv(connection);
            } catch (Exception e) {
                log.error("IMU数据CSV写入异常", e);
            }
        }, 1, 200, TimeUnit.MILLISECONDS);

        log.info("启动设备 {} 的IMU CSV写入定时器", connection.deviceId);

        connection.gasWriteTask = csvWriteScheduler.scheduleAtFixedRate(() -> {
            try {
                writeGasDataToCsv(connection);
            } catch (Exception e) {
                log.error("GAS数据CSV写入异常", e);
            }
        }, 1, 200, TimeUnit.MILLISECONDS);

        log.info("启动设备 {} 的GAS CSV写入定时器", connection.deviceId);
    }

    private boolean isPersistenceReady(DeviceConnection connection) {
        return connection.allDataReady.get();
    }

    private void startPredictionTimer(DeviceConnection connection) {

        if (connection.predictionTask != null) {
            connection.predictionTask.cancel(false);
        }

        connection.predictionTask = csvWriteScheduler.scheduleAtFixedRate(() -> {
            try {
                if (isPersistenceReady(connection)) {
                    performPrediction(connection);
                }
            } catch (Exception e) {
                log.error("定时预测异常", e);
            }
        }, 7, 5, TimeUnit.SECONDS);

        log.info("启动设备 {} 的定时预测任务（每10秒）", connection.deviceId);
    }

    private void performPrediction(DeviceConnection connection) {
        File audioSegment = null;
        File imuSegment = null;
        File gasSegment = null;
        try {
            log.info("开始执行设备 {} 的模型预测", connection.deviceId);

            boolean manualMode = connection.manualSegmentationMode.get();
            List<ManualSwallowSegment> manualSegments = drainManualSegments(connection);
            if (manualMode && manualSegments.isEmpty()) {
                ModelPredictionService.PredictionResult emptyResult =
                        new ModelPredictionService.PredictionResult();
                emptyResult.setMessage("未检测到人工吞咽段");
                emptyResult.setPredictionWindowSeconds(5);
                webSocketService.pushPredictionResult(connection.deviceId, emptyResult);
                log.info("设备 {} 处于人工分割模式，但当前没有人工吞咽段，跳过模型调用", connection.deviceId);
                return;
            }

            int predictionWindowSeconds = manualMode
                    ? calculateManualPredictionWindow(connection, manualSegments)
                    : 5;

            audioSegment = csvDataService.exportAudioSegment(connection.deviceId, predictionWindowSeconds);
            imuSegment = csvDataService.exportDataSegment(connection.deviceId, "imu", predictionWindowSeconds);
            gasSegment = csvDataService.exportDataSegment(connection.deviceId, "gas", predictionWindowSeconds);

            if (audioSegment == null || imuSegment == null || gasSegment == null) {
                log.warn("设备 {} 数据段导出失败，跳过本次预测", connection.deviceId);
                if (manualMode) {
                    manualSegments.forEach(connection.pendingManualSegments::add);
                }
                return;
            }

            List<List<Number>> manualEvents = manualMode
                    ? toModelManualEvents(connection, manualSegments, predictionWindowSeconds)
                    : null;

            if (manualMode && manualEvents.isEmpty()) {
                ModelPredictionService.PredictionResult emptyResult =
                        new ModelPredictionService.PredictionResult();
                emptyResult.setMessage("未检测到人工吞咽段");
                emptyResult.setPredictionWindowSeconds(predictionWindowSeconds);
                webSocketService.pushPredictionResult(connection.deviceId, emptyResult);
                return;
            }

            ModelPredictionService.PredictionResult result =
                modelPredictionService.uploadAndPredict(
                        connection.taskType,
                        audioSegment,
                        imuSegment,
                        gasSegment,
                        manualEvents,
                        manualMode ? predictionWindowSeconds : null);

            if (result != null) {
                result.setPredictionWindowSeconds(predictionWindowSeconds);

                webSocketService.pushPredictionResult(connection.deviceId, result);

                if (result.hasSwallowEvents()) {
                    log.info("设备 {} 预测成功，检测到 {} 个吞咽事件",
                        connection.deviceId, result.getSwallowEvents().size());
                } else if (result.getMessage() != null) {
                    log.info("设备 {} 预测结果: {}", connection.deviceId, result.getMessage());
                }
            } else {
                log.error("设备 {} 模型预测失败", connection.deviceId);
            }
        } catch (Exception e) {
            log.error("执行预测失败", e);
        } finally {

            if (audioSegment != null && audioSegment.exists()) {
                audioSegment.delete();
            }
            if (imuSegment != null && imuSegment.exists()) {
                imuSegment.delete();
            }
            if (gasSegment != null && gasSegment.exists()) {
                gasSegment.delete();
            }
        }
    }

    private List<ManualSwallowSegment> drainManualSegments(DeviceConnection connection) {
        List<ManualSwallowSegment> segments = new ArrayList<>();
        ManualSwallowSegment segment;
        while ((segment = connection.pendingManualSegments.poll()) != null) {
            segments.add(segment);
        }
        return segments;
    }

    private int calculateManualPredictionWindow(
            DeviceConnection connection,
            List<ManualSwallowSegment> manualSegments) {
        double nowSec = currentSessionSeconds(connection);
        double earliestStart = manualSegments.stream()
                .mapToDouble(ManualSwallowSegment::startSec)
                .min()
                .orElse(nowSec);
        int seconds = (int) Math.ceil(Math.max(5.0, nowSec - earliestStart + 1.0));
        return Math.min(Math.max(seconds, 5), 30);
    }

    private double currentSessionSeconds(DeviceConnection connection) {
        return (System.nanoTime() - connection.startedNanoTime) / 1_000_000_000.0;
    }

    private List<List<Number>> toModelManualEvents(
            DeviceConnection connection,
            List<ManualSwallowSegment> manualSegments,
            int predictionWindowSeconds) {
        double windowStartSec = Math.max(0, currentSessionSeconds(connection) - predictionWindowSeconds);
        long maxWindowMs = predictionWindowSeconds * 1000L;
        List<List<Number>> events = new ArrayList<>();

        for (ManualSwallowSegment segment : manualSegments) {
            long startMs = Math.max(0, Math.round((segment.startSec() - windowStartSec) * 1000));
            long endMs = Math.max(startMs + 1, Math.round((segment.endSec() - windowStartSec) * 1000));
            startMs = Math.min(startMs, maxWindowMs);
            endMs = Math.min(endMs, maxWindowMs);
            if (endMs > startMs) {
                events.add(List.of((Number) startMs, (Number) endMs));
            }
        }

        return events;
    }

    private void writeImuDataToCsv(DeviceConnection connection) {

        if (!isPersistenceReady(connection)) {
            return;
        }

        List<String[]> valList = new ArrayList<>();

        int bufferSize = connection.imuBuffer.getSize();
        int lsize = 400;

        if (bufferSize > 2000) {
            lsize = 1500;
            log.warn("设备 {} IMU缓冲区严重积压: {} 条数据,增加处理量", connection.deviceId, bufferSize);
        } else if (bufferSize > 1500) {
            lsize = 1000;
        }

        for (int i = 0; i < lsize; i++) {
            if (connection.imuBuffer.getSize() > 0) {
                Object obj = connection.imuBuffer.poll();
                if (obj != null) {
                    String imuStr = (String) obj;
                    String[] valArr = getValForJsonStr(imuStr, 2);
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

    private void writeGasDataToCsv(DeviceConnection connection) {

        if (!isPersistenceReady(connection)) {
            return;
        }

        List<String[]> valList = new ArrayList<>();

        int bufferSize = connection.gasBuffer.getSize();
        int lsize = 20;

        if (bufferSize > 100) {
            lsize = 40;
        } else if (bufferSize > 50) {
            lsize = 30;
        }

        for (int i = 0; i < lsize; i++) {
            if (connection.gasBuffer.getSize() > 0) {
                Object obj = connection.gasBuffer.poll();
                if (obj != null) {
                    String gasStr = (String) obj;
                    String[] valArr = getValForJsonStr(gasStr, 3);
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

            if (type == 2) {
                JsonNode accNode = jsonNode.get("acc");
                if (accNode != null && !accNode.isNull()) {
                    String[] valList = new String[4];
                    valList[0] = setSTimeToLTime(timestamp, timestampus);
                    valList[1] = String.valueOf(accNode.get("x").asInt());
                    valList[2] = String.valueOf(accNode.get("y").asInt());
                    valList[3] = String.valueOf(accNode.get("z").asInt());
                    return valList;
                }
            } else if (type == 3) {
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

    private String setSTimeToLTime(String timestamp, String timestampus) {
        try {

            float utile = Integer.valueOf(timestampus);
            float rlt = utile / 1000000f;
            int mtime = (int) (rlt * 1000);
            long timestp = Long.valueOf(timestamp) * 1000 + mtime;

            return String.valueOf(timestp);
        } catch (Exception e) {
            log.error("时间戳转换失败: timestamp={}, timestampus={}", timestamp, timestampus, e);
            return String.valueOf(System.currentTimeMillis());
        }
    }

    public CompletableFuture<Boolean> stopDataReceiving(String deviceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeviceConnection connection = deviceConnections.get(deviceId);
                if (connection != null && connection.isConnected.get()) {
                    String sessionId = connection.sessionId;

                    String command = "false";
                    byte[] commandData = SocketTools.packSFream(command);
                    if (connection.outputStream != null) {
                        connection.outputStream.write(commandData);
                        connection.outputStream.flush();
                    }

                    connection.isReceiving.set(false);
                    connection.isConnected.set(false);

                    if (connection.receiveThread != null) {
                        connection.receiveThread.interrupt();
                    }

                    if (connection.imuWriteTask != null) {
                        connection.imuWriteTask.cancel(false);
                        log.info("停止设备 {} 的IMU CSV写入定时器", deviceId);
                    }
                    if (connection.gasWriteTask != null) {
                        connection.gasWriteTask.cancel(false);
                        log.info("停止设备 {} 的GAS CSV写入定时器", deviceId);
                    }

                    if (connection.predictionTask != null) {
                        connection.predictionTask.cancel(false);
                        log.info("停止设备 {} 的定时预测任务", deviceId);
                    }
                    if (connection.lockHeartbeatTask != null) {
                        connection.lockHeartbeatTask.cancel(false);
                    }

                    stopAudioReceiving(connection);

                    saveRemainingData(connection);

                    csvDataService.closeWriter(deviceId);

                    if (connection.socket != null && !connection.socket.isClosed()) {
                        connection.socket.close();
                    }

                    deviceConnections.remove(deviceId, connection);
                    releaseDeviceLease(deviceId, sessionId, "手动停止会话");
                    updateCaptureSessionStatus(
                            sessionId,
                            "STOPPED",
                            LocalDateTime.now(CaptureSession.ZONE_CN),
                            null,
                            null);
                    log.info("停止设备 {} 数据接收", deviceId);
                    return true;
                }

                releaseDeviceLease(deviceId, null, "停止请求触发孤立占用清理");
                String staleSessionId = csvDataService.getSessionIdByDeviceId(deviceId);
                updateCaptureSessionStatus(
                        staleSessionId,
                        "STOPPED",
                        LocalDateTime.now(CaptureSession.ZONE_CN),
                        null,
                        null);
                return false;

            } catch (Exception e) {
                log.error("停止设备数据接收失败: {}", deviceId, e);
                return false;
            }
        });
    }

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

        cleanupConnection(connection);
    }

    private void processReceivedData(byte[] data, DeviceConnection connection) {
        try {

            connection.buffer = SocketTools.byteArrAdd(connection.buffer, data);

            if (data.length > 0) {
                log.debug("设备 {} 接收到 {} 字节数据", connection.deviceId, data.length);
            }

            int min = 16;
            while (connection.buffer.length >= min) {
                int start = 0;
                byte[] fhead = SocketTools.subArray(connection.buffer, start, start + 4);
                if (!SocketTools.encodeHexString(fhead).equals("000055aa")) {

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

                    break;
                }

                List<byte[]> rlt = SocketTools.anlyBufData(connection.buffer);
                if (rlt != null) {
                    byte[] ft = rlt.get(2);
                    byte[] fds = rlt.get(3);

                    String frameTypeStr = SocketTools.encodeHexString(ft);

                    if ("00000001".equals(frameTypeStr)) {

                        String dataStr = new String(fds, 0, fds.length);
                        log.debug("设备 {} 控制响应: {}", connection.deviceId, dataStr);

                    } else if ("00000002".equals(frameTypeStr)) {

                        String dataStr = new String(fds, 0, fds.length);

                        processSensorData(dataStr, connection.deviceId);
                    } else {
                        log.debug("设备 {} 接收到未知帧类型: {}", connection.deviceId, frameTypeStr);
                    }

                    connection.buffer = SocketTools.getNewArray(connection.buffer, min + sdleng);
                } else {

                    connection.buffer = SocketTools.getNewArray(connection.buffer, 1);
                }
            }

        } catch (Exception e) {
            log.error("处理接收数据失败: {}", connection.deviceId, e);
        }
    }

    private void saveRemainingData(DeviceConnection connection) {
        try {

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

    public void processSensorData(String jsonData, String deviceId) {
        try {

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

            Long timestamp = jsonNode.has("timestamp") ? jsonNode.get("timestamp").asLong() : 0L;
            Long timestampus = jsonNode.has("timestampus") ? jsonNode.get("timestampus").asLong() : 0L;

            Long fullTimestamp = timestamp * 1000 + (timestampus / 1000);

            JsonNode accNode = jsonNode.get("acc");
            if (accNode != null && !accNode.isNull()) {
                if (connection.imuFirstData) {

                    connection.imuFirstData = false;
                    log.info("设备 {} 舍弃首条IMU数据", deviceId);
                } else {

                    if (!connection.imuReady.get()) {
                        connection.imuReady.set(true);
                        log.info("设备 {} IMU数据就绪", deviceId);
                        checkAndStartRecording(connection);
                    }

                    connection.imuBuffer.put(jsonData);

                    connection.imuPushCount++;
                    if (connection.imuPushCount % 20 == 0 && accNode.has("x") && accNode.has("y") && accNode.has("z")) {
                        Integer x = accNode.get("x").asInt();
                        Integer y = accNode.get("y").asInt();
                        Integer z = accNode.get("z").asInt();
                        webSocketService.pushImuData(deviceId, fullTimestamp, x, y, z);
                    }
                }
            }

            JsonNode flowNode = jsonNode.get("flow");
            if (flowNode != null && !flowNode.isNull()) {
                if (connection.gasFirstData) {

                    connection.gasFirstData = false;
                    log.info("设备 {} 舍弃首条GAS数据", deviceId);
                } else {

                    if (!connection.gasReady.get()) {
                        connection.gasReady.set(true);
                        log.info("设备 {} GAS数据就绪", deviceId);
                        checkAndStartRecording(connection);
                    }

                    connection.gasBuffer.put(jsonData);

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

    private void cleanupConnection(DeviceConnection connection) {
        try {
            connection.isReceiving.set(false);
            connection.isConnected.set(false);

            if (connection.imuWriteTask != null) {
                connection.imuWriteTask.cancel(false);
            }
            if (connection.gasWriteTask != null) {
                connection.gasWriteTask.cancel(false);
            }
            if (connection.predictionTask != null) {
                connection.predictionTask.cancel(false);
            }
            if (connection.lockHeartbeatTask != null) {
                connection.lockHeartbeatTask.cancel(false);
            }

            if (connection.inputStream != null) {
                connection.inputStream.close();
            }
            if (connection.outputStream != null) {
                connection.outputStream.close();
            }
            if (connection.socket != null && !connection.socket.isClosed()) {
                connection.socket.close();
            }

            deviceConnections.remove(connection.deviceId, connection);
            releaseDeviceLease(connection.deviceId, connection.sessionId, "连接线程退出");
            updateCaptureSessionStatus(
                    connection.sessionId,
                    "FAILED",
                    LocalDateTime.now(CaptureSession.ZONE_CN),
                    null,
                    null);
            csvDataService.closeWriter(connection.deviceId);
        } catch (IOException e) {
            log.error("清理连接失败", e);
        }
    }

    public boolean isDeviceConnected(String deviceId) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        return connection != null && connection.isConnected.get();
    }

    public boolean isDeviceReceiving(String deviceId) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        return connection != null && connection.isReceiving.get();
    }

    public List<String> getConnectedDevices() {
        return deviceConnections.entrySet().stream()
                .filter(entry -> entry.getValue().isConnected.get())
                .map(entry -> entry.getKey())
                .toList();
    }

    public LocalDateTime getLastHeartbeat(String deviceId) {
        DeviceConnection connection = deviceConnections.get(deviceId);
        return connection != null ? connection.lastHeartbeat : null;
    }

    public List<String> getCsvFileList() {
        return csvDataService.getCsvFileList();
    }

    public boolean deleteCsvFile(String fileName) {
        return csvDataService.deleteCsvFile(fileName);
    }

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

    private void startAudioReceiving(DeviceConnection connection) {
        log.info("设备 {} 开始启动音频接收线程...", connection.deviceId);

        connection.audioThread = new Thread(() -> {
            while (connection.isConnected.get() && connection.audioRetryCount < DeviceConnection.MAX_AUDIO_RETRY) {
                boolean shouldRetry = false;
                try {

                    String rtspUrl = "rtsp://" + connection.deviceIp + ":8554/stream/audio";
                    log.info("开始连接音频RTSP: {} (尝试 {}/{})", rtspUrl, connection.audioRetryCount + 1, DeviceConnection.MAX_AUDIO_RETRY);

                    connection.audioGrabber = FFmpegFrameGrabber.createDefault(rtspUrl);
                    connection.audioGrabber.setOption("rtsp_transport", "tcp");
                    connection.audioGrabber.setTimeout(5000);
                    connection.audioGrabber.start();

                    connection.audioReceiving.set(true);
                    log.info("音频RTSP连接成功: {}", rtspUrl);

                    Frame firstFrame = connection.audioGrabber.grabSamples();
                    if (firstFrame != null && firstFrame.audioChannels > 0) {

                        connection.audioFirstFrame = firstFrame;
                        log.info("设备 {} 音频第一帧已抓取 (声道={}, 采样={}/s)",
                            connection.deviceId, firstFrame.audioChannels, connection.audioGrabber.getSampleRate());

                        if (!connection.audioReady.get()) {
                            connection.audioReady.set(true);
                            log.info("设备 {} 音频数据就绪", connection.deviceId);
                            checkAndStartRecording(connection);
                        }

                        connection.audioRetryCount = 0;

                        while (connection.audioReceiving.get() && !Thread.currentThread().isInterrupted()) {
                            Frame frame = connection.audioGrabber.grabSamples();
                            if (frame != null && frame.audioChannels > 0) {

                                pushAudioToWebSocket(connection, frame);

                                recordAudioFrame(connection, frame);
                            } else {

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
                        shouldRetry = true;
                    }

                } catch (Exception e) {
                    log.error("设备 {} 音频接收异常 (尝试 {}/{}): {}",
                            connection.deviceId, connection.audioRetryCount + 1, DeviceConnection.MAX_AUDIO_RETRY, e.getMessage());
                    shouldRetry = true;
                }

                if (shouldRetry && connection.isConnected.get()) {
                    log.debug("设备 {} 准备重试，清理音频资源", connection.deviceId);
                    cleanupAudioResources(connection);

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

                    log.debug("设备 {} 音频接收循环退出（不清理资源）", connection.deviceId);
                    break;
                }
            }

            if (connection.audioRetryCount >= DeviceConnection.MAX_AUDIO_RETRY) {
                log.error("设备 {} 音频连接失败，已达到最大重试次数 {}", connection.deviceId, DeviceConnection.MAX_AUDIO_RETRY);
            }
        });
        connection.audioThread.setDaemon(true);
        connection.audioThread.start();

        log.info("设备 {} 音频接收线程已启动", connection.deviceId);
    }

    private void cleanupAudioResources(DeviceConnection connection) {
        try {
            connection.audioReceiving.set(false);

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

    private void checkAndStartRecording(DeviceConnection connection) {
        if (connection.checkAllDataReady()) {
            log.info("设备 {} 所有数据就绪 (IMU, GAS, AUDIO)，开始保存文件", connection.deviceId);

            if (connection.audioFirstFrame != null && connection.audioRecorder == null) {
                initAudioRecorder(connection, connection.audioFirstFrame);
                connection.audioStartTimestamp = System.currentTimeMillis();
            }
        }
    }

    private void initAudioRecorder(DeviceConnection connection, Frame firstFrame) {
        try {

            if (connection.audioRecorder != null) {
                return;
            }

            String fileName = "audio.wav";

            String sessionFolder = csvDataService.getSessionFolder(connection.deviceId);
            if (sessionFolder == null) {
                log.error("设备 {} 的会话文件夹不存在，无法保存音频文件", connection.deviceId);
                return;
            }
            File audioFile = new File(sessionFolder, fileName);
            connection.audioFilePath = audioFile.getAbsolutePath();

            int originalChannels = connection.audioGrabber.getAudioChannels();
            int originalSampleRate = connection.audioGrabber.getSampleRate();
            log.info("设备 {} 音频参数: 采样率={}Hz, 原始声道数={}",
                connection.deviceId, originalSampleRate, originalChannels);

            int channelsToUse = 1;

            connection.audioRecorder = new FFmpegFrameRecorder(
                connection.audioFilePath,
                channelsToUse
            );

            connection.audioRecorder.setAudioOption("crf", "0");
            connection.audioRecorder.setAudioQuality(0);
            connection.audioRecorder.setAudioChannels(channelsToUse);
            connection.audioRecorder.setSampleRate(originalSampleRate);
            connection.audioRecorder.setFormat("wav");
            connection.audioRecorder.setAudioCodec(avcodec.AV_CODEC_ID_PCM_S16LE);

            connection.audioRecorder.start();

            connection.audioRecorder.setTimestamp(firstFrame.timestamp);
            connection.audioRecorder.recordSamples(firstFrame.samples);

            log.info("设备 {} 音频录制器初始化成功,文件: {}, 输出声道数: {}",
                connection.deviceId, fileName, channelsToUse);

        } catch (Exception e) {
            log.error("设备 {} 初始化音频录制器失败", connection.deviceId, e);
        }
    }

    private void recordAudioFrame(DeviceConnection connection, Frame frame) {
        try {

            if (!connection.allDataReady.get()) {
                return;
            }

            if (connection.audioRecorder == null && connection.audioFirstFrame != null) {
                initAudioRecorder(connection, connection.audioFirstFrame);
                connection.audioStartTimestamp = System.currentTimeMillis();
            }

            if (!connection.audioReceiving.get()) {
                return;
            }

            if (connection.audioRecorder != null) {
                synchronized (connection.audioRecorder) {

                    if (connection.audioRecorder != null && connection.audioReceiving.get()) {
                        connection.audioRecorder.setTimestamp(frame.timestamp);
                        connection.audioRecorder.recordSamples(frame.samples);
                        connection.audioFrameCount++;
                    }
                }
            }
        } catch (Exception e) {

            log.warn("设备 {} 录制音频帧失败: {}", connection.deviceId, e.getMessage());
        }
    }

    private void pushAudioToWebSocket(DeviceConnection connection, Frame frame) {
        try {
            if (frame.samples == null || frame.samples.length == 0) {
                return;
            }

            java.nio.Buffer buffer = frame.samples[0];

            if (buffer instanceof java.nio.ShortBuffer) {

                java.nio.ShortBuffer shortBuffer = (java.nio.ShortBuffer) buffer;
                int capacity = shortBuffer.capacity();

                for (int i = 0; i < capacity; i++) {
                    connection.audioPushCount++;
                    if (connection.audioPushCount % DeviceConnection.AUDIO_DOWNSAMPLE_RATIO == 0) {

                        short shortValue = shortBuffer.get(i);
                        float amplitude = shortValue / 32768.0f;
                        long timestamp = System.currentTimeMillis();
                        webSocketService.pushAudioData(connection.deviceId, timestamp, amplitude);
                    }
                }
            } else if (buffer instanceof java.nio.FloatBuffer) {

                java.nio.FloatBuffer floatBuffer = (java.nio.FloatBuffer) buffer;
                int capacity = floatBuffer.capacity();

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

    private void stopAudioReceiving(DeviceConnection connection) {
        try {
            log.info("开始停止设备 {} 的音频接收...", connection.deviceId);

            connection.audioReceiving.set(false);

            if (connection.audioThread != null) {
                connection.audioThread.interrupt();
                try {

                    connection.audioThread.join(2000);
                    if (connection.audioThread.isAlive()) {
                        log.warn("设备 {} 音频线程未能在2秒内结束", connection.deviceId);
                    }
                } catch (InterruptedException ie) {
                    log.warn("等待音频线程结束时被中断");
                    Thread.currentThread().interrupt();
                }
            }

            Thread.sleep(500);

            long audioEndTimestamp = System.currentTimeMillis();
            double audioDurationSeconds = 0.0;
            if (connection.audioStartTimestamp > 0) {
                long durationMs = audioEndTimestamp - connection.audioStartTimestamp;
                audioDurationSeconds = durationMs / 1000.0;
            }

            boolean hasRecorder = connection.audioRecorder != null;
            String audioFilePath = connection.audioFilePath;
            if (connection.audioRecorder != null) {
                synchronized (connection.audioRecorder) {
                    try {

                        connection.audioRecorder.stop();
                        log.debug("设备 {} 音频录制器已停止", connection.deviceId);

                        connection.audioRecorder.release();
                        log.debug("设备 {} 音频录制器已释放", connection.deviceId);

                        connection.audioRecorder.close();
                        log.debug("设备 {} 音频录制器已关闭", connection.deviceId);

                        connection.audioRecorder = null;
                    } catch (Exception e) {
                        log.error("设备 {} 关闭音频录制器时出错: {}", connection.deviceId, e.getMessage(), e);
                    }
                }
            }

            Thread.sleep(200);

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

            log.info("========================================");
            File finalAudioFile = null;
            if (hasRecorder && audioFilePath != null) {

                File audioFile = new File(audioFilePath);
                finalAudioFile = audioFile;
                if (audioFile.exists()) {
                    long fileSize = audioFile.length();
                    log.info("设备 {} 音频文件已保存: {}", connection.deviceId, audioFilePath);
                    log.info("音频文件大小: {} KB ({} bytes)", fileSize / 1024, fileSize);
                    log.info("音频时长: {} 秒", String.format("%.2f", audioDurationSeconds));
                    log.info("音频帧数: {} 帧", connection.audioFrameCount);

                    if (!audioFile.canRead()) {
                        log.error("警告：音频文件存在但不可读！路径: {}", audioFilePath);
                    }
                } else {
                    log.error("设备 {} 音频文件未找到: {}", connection.deviceId, audioFilePath);

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

                log.warn("数据就绪状态: IMU={}, GAS={}, AUDIO={}, 全部就绪={}",
                    connection.imuReady.get(),
                    connection.gasReady.get(),
                    connection.audioReady.get(),
                    connection.allDataReady.get());

                if (!connection.audioReady.get()) {
                    log.warn("原因: 音频数据未就绪 - 可能是RTSP连接失败或未抓取到音频帧");
                } else if (!connection.allDataReady.get()) {
                    log.warn("原因: 其他数据未就绪，未触发录制器初始化");
                } else if (connection.audioFirstFrame == null) {
                    log.warn("原因: 音频第一帧未保存");
                }
            }
            log.info("========================================");

            log.info("设备 {} 音频接收已停止", connection.deviceId);

            if (finalAudioFile != null) {
                try {
                    Thread.sleep(100);
                    if (finalAudioFile.exists()) {
                        log.info("[最终验证] 音频文件仍然存在: {} (大小: {} bytes)",
                            finalAudioFile.getAbsolutePath(), finalAudioFile.length());
                    } else {
                        log.error("[最终验证] 警告！音频文件已消失: {}", finalAudioFile.getAbsolutePath());

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

    public record DeviceDataStats(
        String deviceId,
        int imuCount,
        int gasCount,
        int imuBufferSize,
        int gasBufferSize
    ) {}
}
