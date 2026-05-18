package cn.scut.raputa.service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class WavFileUtils {

    private WavFileUtils() {
    }

    static void writeCleanCopy(Path sourcePath, Path targetPath) throws IOException {
        ParsedWav wav = parseWav(sourcePath);
        writeCleanWav(wav.data(), wav.format(), targetPath);
    }

    static void writeLastSecondsAsCleanWav(Path sourcePath, Path targetPath, int seconds) throws IOException {
        ParsedWav wav = parseWav(sourcePath);
        AudioFormat format = wav.format();
        int frameSize = format.getFrameSize();
        if (frameSize <= 0) {
            throw new IOException("音频 frameSize 非法: " + frameSize);
        }

        long framesToKeep = Math.max(1L, Math.round(Math.max(seconds, 1) * format.getFrameRate()));
        long bytesToKeep = framesToKeep * frameSize;
        byte[] data = wav.data();
        int start = data.length > bytesToKeep ? data.length - Math.toIntExact(bytesToKeep) : 0;
        start = alignToFrame(start, frameSize);
        byte[] clipped = Arrays.copyOfRange(data, start, data.length);
        clipped = trimToFrameBoundary(clipped, frameSize);
        if (clipped.length == 0) {
            throw new IOException("裁剪后的音频为空");
        }

        writeCleanWav(clipped, format, targetPath);
    }

    static void writeSliceAsCleanWav(Path sourcePath, Path targetPath, long startMs, long endMs) throws IOException {
        ParsedWav wav = parseWav(sourcePath);
        AudioFormat format = wav.format();
        int frameSize = format.getFrameSize();
        if (frameSize <= 0) {
            throw new IOException("音频 frameSize 非法: " + frameSize);
        }

        long startFrame = Math.max(0L, Math.round(startMs / 1000.0 * format.getFrameRate()));
        long endFrame = Math.max(startFrame + 1L, Math.round(endMs / 1000.0 * format.getFrameRate()));
        long totalFrames = wav.data().length / frameSize;
        startFrame = Math.min(startFrame, Math.max(totalFrames - 1L, 0L));
        endFrame = Math.min(endFrame, totalFrames);

        int startByte = Math.toIntExact(startFrame * frameSize);
        int endByte = Math.toIntExact(Math.max(startFrame + 1L, endFrame) * frameSize);
        endByte = Math.min(endByte, wav.data().length);
        byte[] clipped = Arrays.copyOfRange(wav.data(), startByte, endByte);
        clipped = trimToFrameBoundary(clipped, frameSize);
        if (clipped.length == 0) {
            throw new IOException("裁剪后的音频为空");
        }

        writeCleanWav(clipped, format, targetPath);
    }

    private static ParsedWav parseWav(Path sourcePath) throws IOException {
        byte[] bytes = Files.readAllBytes(sourcePath);
        if (bytes.length < 44) {
            throw new IOException("WAV 文件过短: " + sourcePath.getFileName());
        }
        if (!"RIFF".equals(ascii(bytes, 0, 4)) && !"RF64".equals(ascii(bytes, 0, 4))) {
            throw new IOException("不是 RIFF/RF64 WAV 文件: " + sourcePath.getFileName());
        }
        if (!"WAVE".equals(ascii(bytes, 8, 4))) {
            throw new IOException("不是 WAVE 文件: " + sourcePath.getFileName());
        }

        FormatChunk formatChunk = null;
        int dataOffset = -1;
        int dataLength = -1;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunkId = ascii(bytes, offset, 4);
            long declaredSize = uint32LE(bytes, offset + 4);
            int chunkDataOffset = offset + 8;
            long available = Math.max(0L, bytes.length - (long) chunkDataOffset);
            long actualSize = declaredSize == 0xFFFF_FFFFL
                    ? available
                    : Math.min(declaredSize, available);

            if ("fmt ".equals(chunkId)) {
                formatChunk = parseFormatChunk(bytes, chunkDataOffset, Math.toIntExact(actualSize));
            } else if ("data".equals(chunkId)) {
                dataOffset = chunkDataOffset;
                dataLength = Math.toIntExact(actualSize);
                break;
            }

            long nextOffset = chunkDataOffset + actualSize + (actualSize % 2);
            if (nextOffset <= offset || nextOffset > bytes.length) {
                break;
            }
            offset = Math.toIntExact(nextOffset);
        }

        if (formatChunk == null) {
            throw new IOException("WAV 缺少 fmt chunk: " + sourcePath.getFileName());
        }
        if (dataOffset < 0 || dataLength <= 0) {
            throw new IOException("WAV 缺少有效 data chunk: " + sourcePath.getFileName());
        }

        byte[] data = Arrays.copyOfRange(bytes, dataOffset, dataOffset + dataLength);
        data = trimToFrameBoundary(data, formatChunk.format().getFrameSize());
        if (data.length == 0) {
            throw new IOException("WAV 音频数据为空: " + sourcePath.getFileName());
        }
        return new ParsedWav(formatChunk.format(), data);
    }

    private static FormatChunk parseFormatChunk(byte[] bytes, int offset, int size) throws IOException {
        if (size < 16 || offset + size > bytes.length) {
            throw new IOException("WAV fmt chunk 非法");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, size).order(ByteOrder.LITTLE_ENDIAN);
        int audioFormat = Short.toUnsignedInt(buffer.getShort());
        int channels = Short.toUnsignedInt(buffer.getShort());
        long sampleRateLong = Integer.toUnsignedLong(buffer.getInt());
        buffer.getInt(); // byte rate
        int blockAlign = Short.toUnsignedInt(buffer.getShort());
        int bitsPerSample = Short.toUnsignedInt(buffer.getShort());

        if (channels <= 0 || sampleRateLong <= 0 || sampleRateLong > Integer.MAX_VALUE || bitsPerSample <= 0) {
            throw new IOException("WAV fmt 参数非法");
        }

        int bytesPerSample = Math.max(1, bitsPerSample / 8);
        int frameSize = blockAlign > 0 ? blockAlign : channels * bytesPerSample;
        AudioFormat.Encoding encoding = switch (audioFormat) {
            case 1 -> bitsPerSample == 8
                    ? AudioFormat.Encoding.PCM_UNSIGNED
                    : AudioFormat.Encoding.PCM_SIGNED;
            case 3 -> AudioFormat.Encoding.PCM_FLOAT;
            default -> throw new IOException("暂不支持的 WAV 编码格式: " + audioFormat);
        };

        AudioFormat format = new AudioFormat(
                encoding,
                (float) sampleRateLong,
                bitsPerSample,
                channels,
                frameSize,
                (float) sampleRateLong,
                false);
        return new FormatChunk(format);
    }

    private static void writeCleanWav(byte[] audioBytes, AudioFormat format, Path targetPath) throws IOException {
        int frameSize = format.getFrameSize();
        byte[] alignedBytes = trimToFrameBoundary(audioBytes, frameSize);
        long frameLength = alignedBytes.length / frameSize;
        Files.createDirectories(targetPath.getParent());
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(alignedBytes),
                format,
                frameLength)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, targetPath.toFile());
        }
    }

    private static byte[] trimToFrameBoundary(byte[] data, int frameSize) {
        if (frameSize <= 0 || data.length == 0) {
            return data;
        }
        int alignedLength = data.length - (data.length % frameSize);
        return alignedLength == data.length ? data : Arrays.copyOf(data, alignedLength);
    }

    private static int alignToFrame(int value, int frameSize) {
        if (frameSize <= 0) {
            return value;
        }
        return value + ((frameSize - (value % frameSize)) % frameSize);
    }

    private static long uint32LE(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt());
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private record ParsedWav(AudioFormat format, byte[] data) {
    }

    private record FormatChunk(AudioFormat format) {
    }
}
