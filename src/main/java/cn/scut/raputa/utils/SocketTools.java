package cn.scut.raputa.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * 网络工具类 - 处理UDP数据包解析
 * 
 * @author RAPUTA Team
 */
@Slf4j
public class SocketTools {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 解析缓冲区数据 - 按照原始协议格式解析
     * 
     * @param buffer 接收到的数据缓冲区
     * @return 解析后的数据列表
     */


    public static List<byte[]> anlyBufData(byte[] byteBuf) {
        List<byte[]> dList = new ArrayList<>();
        
        try {
            int start = 0, end = start + 4;
            byte[] fhead = subArray(byteBuf, start, end);
            if (!encodeHexString(fhead).equals("000055aa")) {
                log.error("数据帧头无效");
                return null;
            }
            dList.add(fhead);
            
            start = end;
            end = start + 4;
            byte[] fx = subArray(byteBuf, start, end);
            dList.add(fx);
            
            start = end;
            end = start + 4;
            byte[] ft = subArray(byteBuf, start, end);
            dList.add(ft);
            
            start = end;
            end = start + 4;
            byte[] fel = subArray(byteBuf, start, end);
            int sdleng = bytesToInt(fel);
            if (sdleng > 1024 * 4) {
                log.error("数据长度无效");
                return null;
            }
            
            start = end;
            end = start + (sdleng - 8);
            byte[] fdatas = subArray(byteBuf, start, end);
            dList.add(fdatas);
            
            // CRC32校验 - 参考原始项目逻辑
            CRC32 crc32 = new CRC32();
            crc32.update(subArray(byteBuf, 0, end));
            String js_crc = Long.toHexString(crc32.getValue());
            js_crc = hexTo8(js_crc);
            
            start = end;
            end = start + 4;
            byte[] fcrc32 = subArray(byteBuf, start, end);
            String fs_crc = encodeHexString(fcrc32);
            if (!fs_crc.equals(js_crc)) {
                log.error("数据crc32不匹配,fs_crc:{},js_crc:{}", fs_crc, js_crc);
                return null;
            }
            dList.add(fcrc32);
            
            start = end;
            end = start + 4;
            byte[] fend = subArray(byteBuf, start, end);
            if (!encodeHexString(fend).equals("0000aa55")) {
                log.error("数据帧尾无效");
                return null;
            }
            dList.add(fend);
            
        } catch (Exception e) {
            log.error("解析缓冲区数据失败", e);
            return null;
        }
        
        return dList;
    }
    
    /**
     * 解析JSON字符串
     * 
     * @param jsonString JSON字符串
     * @return JSON对象
     */
    public static JsonNode getJsonObject(String jsonString) {
        try {
            // 清理字符串，移除可能的控制字符
            String cleanedJson = jsonString.trim().replaceAll("[\\x00-\\x1F\\x7F]", "");
            log.debug("清理后的JSON: {}", cleanedJson);
            
            return objectMapper.readTree(cleanedJson);
        } catch (Exception e) {
            log.error("解析JSON字符串失败: {}", jsonString, e);
            return objectMapper.createObjectNode();
        }
    }
    
    /**
     * 验证是否为有效的JSON字符串
     * 参考原项目 SocketTools.isJsonString
     * 
     * @param json JSON字符串
     * @return 是否为有效JSON
     */
    public static boolean isJsonString(String json) {
        try {
            String cleanedJson = json.trim().replaceAll("[\\x00-\\x1F\\x7F]", "");
            objectMapper.readTree(cleanedJson);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] packSFream(String str) {
        String json="{\"enable\":"+str+",\"timeout\":7200}";
        byte[] f_h=getU16ToByte(0x55aa);
        byte[] f_x=getU16ToByte(0x00);
        byte[] f_t=getU16ToByte(0x01);
        byte[] f_val=json.getBytes();
        byte[] f_el=getU16ToByte(f_val.length+8);
        byte[] rArr1=new byte[0];
        rArr1=byteArrAdd(rArr1,f_h);
        rArr1=byteArrAdd(rArr1,f_x);
        rArr1=byteArrAdd(rArr1,f_t);
        rArr1=byteArrAdd(rArr1,f_el);
        rArr1=byteArrAdd(rArr1,f_val);
        CRC32 crc32 = new CRC32();
        crc32.update(rArr1);
        String crc32_u16=Long.toHexString(crc32.getValue());
        // System.out.println("CRC32:"+crc32_u16);
        int u16=Integer.parseInt(crc32_u16,16);
        byte[] f_crc=getU16ToByte(u16);
        byte[] f_e=getU16ToByte(0xaa55);
        rArr1=byteArrAdd(rArr1,f_crc);
        rArr1=byteArrAdd(rArr1,f_e);
        return rArr1;
    }



    /**
     * 验证IP地址格式
     * 
     * @param ip IP地址字符串
     * @return 是否为有效IP地址
     */
    public static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    // ========== 以下为原始工具方法 ==========
    
    public static byte[] getU16ToByte(int u16) {
        ByteBuffer byB = ByteBuffer.wrap(new byte[4]);
        byB.asIntBuffer().put(u16);
        return byB.array();
    }
    
    public static byte[] getLongToByte(long lg) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(lg);
        return buffer.array();
    }
    
    public static byte[] byteArrAdd(byte[] barr1, byte[] barr2) {
        byte[] nArr = new byte[barr1.length + barr2.length];
        for (int i = 0; i < barr1.length; i++) {
            nArr[i] = barr1[i];
        }
        int k = barr1.length;
        for (int i = 0; i < barr2.length; i++) {
            nArr[(k + i)] = barr2[i];
        }
        return nArr;
    }
    

    
    public static byte[] subArray(byte[] barr1, int sindex, int eindex) {
        if (eindex < sindex)
            return barr1;
        byte[] nArr = new byte[(eindex - sindex)];
        int count = 0;
        for (int i = 0; i < barr1.length; i++) {
            if (i >= sindex && i < eindex) {
                nArr[count] = barr1[i];
                count++;
            }
        }
        return nArr;
    }
    
    public static String hexTo8(String shex) {
        int i_hex = shex.length(), len = 8;
        int c_len = len - i_hex;
        String ss = "";
        for (int i = 0; i < c_len; i++)
            ss = ss + "0";
        return ss + shex;
    }
    
    public static int bytesToInt(byte[] b) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.put(b);
        buffer.flip(); // need flip
        return buffer.getInt();
    }
    
    public static String encodeHexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 获取新数组（移除前n个元素）
     */
    public static byte[] getNewArray(byte[] barr1, int index) {
        if (index < 0)
            return barr1;
        byte[] nArr = new byte[barr1.length - index];
        int count = 0;
        for (int i = 0; i < barr1.length; i++) {
            if (i >= index) {
                nArr[count] = barr1[i];
                count++;
            }
        }
        return nArr;
    }
}
