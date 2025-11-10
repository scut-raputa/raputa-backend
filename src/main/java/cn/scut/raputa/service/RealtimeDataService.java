package cn.scut.raputa.service;

import cn.scut.raputa.utils.DataBuffer;
import cn.scut.raputa.utils.SocketTools;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
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

    // 设备连接状态管理
    private final ConcurrentHashMap<String, DeviceConnection> deviceConnections = new ConcurrentHashMap<>();
    
    // CSV写入定时器
    private final ScheduledExecutorService csvWriteScheduler = Executors.newScheduledThreadPool(2);

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

        public DeviceConnection(String deviceId) {
            this.deviceId = deviceId;
            this.lastHeartbeat = LocalDateTime.now();
        }
    }

    /**
     * 开始连接设备并接收数据
     */
    public CompletableFuture<Boolean> startDataReceiving(String deviceIp, String deviceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeviceConnection connection = new DeviceConnection(deviceId);

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
     * 写入IMU数据到CSV - 参考原始项目的wIMU方法
     */
    private void writeImuDataToCsv(DeviceConnection connection) {
        List<String[]> valList = new ArrayList<>();
        
        // 动态调整处理量 - 参考原项目的动态调整逻辑
        int bufferSize = connection.imuBuffer.getSize();
        int lsize = 400; // 默认处理 400 条
        
        // 如果积压严重,增加单次处理量
        if (bufferSize > 2000) {
            lsize = 600; // 增加到 600 条
            log.warn("设备 {} IMU缓冲区严重积压: {} 条数据,增加处理量", connection.deviceId, bufferSize);
        } else if (bufferSize > 1500) {
            lsize = 500; // 增加到 500 条
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
                    
                    // 保存剩余缓冲数据（在关闭定时器后）
                    saveRemainingData(connection);
                    
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
