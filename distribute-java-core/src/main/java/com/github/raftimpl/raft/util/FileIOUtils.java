package com.github.raftimpl.raft.util;

import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.zip.CRC32;

/**
 * 文件I/O工具类
 * 提供文件I/O操作的通用方法，包括文件读写、protobuf序列化/反序列化、CRC校验等功能
 * 
 */
@SuppressWarnings("unchecked")
public class FileIOUtils {

    private static final Logger LOG = LoggerFactory.getLogger(FileIOUtils.class);

    /**
     * 获取指定目录下所有文件的排序列表
     * 递归遍历目录，返回相对于基础目录的文件路径列表
     * 
     * @param directoryPath 要遍历的目录路径
     * @param baseDirectory 基础目录路径，用于计算相对路径
     * @return 排序后的文件相对路径列表
     * @throws IOException 如果目录操作失败
     */
    public static List<String> getOrderedFileList(
            String directoryPath, String baseDirectory) throws IOException {
        List<String> paths = new ArrayList<>();
        File baseDir = new File(baseDirectory);
        File targetDir = new File(directoryPath);
        
        // 检查目录是否有效
        if (!baseDir.isDirectory() || !targetDir.isDirectory()) {
            return paths;
        }
        
        // 获取基础路径的规范路径
        String basePath = baseDir.getCanonicalPath();
        if (!basePath.endsWith("/")) {
            basePath = basePath + "/";
        }
        
        // 遍历目录中的所有条目
        File[] entries = targetDir.listFiles();
        for (File entry : entries) {
            if (entry.isDirectory()) {
                // 递归处理子目录
                paths.addAll(getOrderedFileList(entry.getCanonicalPath(), basePath));
            } else {
                // 添加文件的相对路径
                paths.add(entry.getCanonicalPath().substring(basePath.length()));
            }
        }
        
        // 对路径进行排序
        Collections.sort(paths);
        return paths;
    }

    /**
     * 打开文件并返回RandomAccessFile对象
     * 
     * @param directory 目录路径
     * @param filename 文件名
     * @param mode 文件打开模式（如"rw", "r"等）
     * @return RandomAccessFile对象
     * @throws RuntimeException 如果文件不存在
     */
    public static RandomAccessFile createFileHandle(String directory, String filename, String mode) {
        try {
            String fullPath = directory + File.separator + filename;
            File fileHandle = new File(fullPath);
            return new RandomAccessFile(fileHandle, mode);
        } catch (FileNotFoundException ex) {
            LOG.warn("file not fount, file={}", filename);
            throw new RuntimeException("file not found, file=" + filename);
        }
    }

    /**
     * 关闭RandomAccessFile文件句柄
     * 
     * @param fileHandle 要关闭的文件句柄
     */
    public static void closeFileHandle(RandomAccessFile fileHandle) {
        try {
            if (fileHandle != null) {
                fileHandle.close();
            }
        } catch (IOException ex) {
            LOG.warn("close file error, msg={}", ex.getMessage());
        }
    }

    /**
     * 关闭FileInputStream文件流
     * 
     * @param inputStream 要关闭的输入流
     */
    public static void closeFileHandle(FileInputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ex) {
            LOG.warn("close file error, msg={}", ex.getMessage());
        }
    }

    /**
     * 关闭FileOutputStream文件流
     * 
     * @param outputStream 要关闭的输出流
     */
    public static void closeFileHandle(FileOutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException ex) {
            LOG.warn("close file error, msg={}", ex.getMessage());
        }
    }

    /**
     * 从文件中读取Protobuf消息对象
     * 文件格式: [8字节CRC32校验码][4字节内容长度][内容数据]
     * 
     * @param fileHandle 文件句柄
     * @param clazz Protobuf消息类型
     * @param <T> 消息类型泛型
     * @return 反序列化后的消息对象，失败时返回null
     */
    public static <T extends Message> T readProtoFromFile(RandomAccessFile fileHandle, Class<T> clazz) {
        try {
            // 读取存储的校验码
            long storedChecksum = fileHandle.readLong();
            // 读取内容长度
            int contentLength = fileHandle.readInt();
            int headerSize = (Long.SIZE + Integer.SIZE) / Byte.SIZE;
            
            // 检查文件剩余长度是否足够
            if (fileHandle.length() - headerSize < contentLength) {
                LOG.warn("file remainLength < dataLen");
                return null;
            }
            
            // 读取内容数据
            byte[] content = new byte[contentLength];
            int bytesRead = fileHandle.read(content);
            if (bytesRead != contentLength) {
                LOG.warn("readLen != dataLen");
                return null;
            }
            
            // 验证CRC32校验码
            long computedChecksum = getCRC32(content);
            if (storedChecksum != computedChecksum) {
                LOG.warn("crc32 check failed");
                return null;
            }
            
            // 使用反射调用parseFrom方法反序列化
            Method method = clazz.getMethod("parseFrom", byte[].class);
            T messageObj = (T) method.invoke(clazz, content);
            return messageObj;
        } catch (Exception ex) {
            LOG.warn("readProtoFromFile meet exception, {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 将Protobuf消息对象写入文件
     * 文件格式: [8字节CRC32校验码][4字节内容长度][内容数据]
     * 
     * @param fileHandle 文件句柄
     * @param message 要写入的Protobuf消息
     * @param <T> 消息类型泛型
     * @throws RuntimeException 如果写入失败
     */
    public static  <T extends Message> void writeProtoToFile(RandomAccessFile fileHandle, T message) {
        // 序列化消息为字节数组
        byte[] content = message.toByteArray();
        // 计算CRC32校验码
        long checksum = getCRC32(content);
        
        try {
            // 写入校验码
            fileHandle.writeLong(checksum);
            // 写入内容长度
            fileHandle.writeInt(content.length);
            // 写入内容数据
            fileHandle.write(content);
        } catch (IOException ex) {
            LOG.warn("writeProtoToFile meet exception, {}", ex.getMessage());
            throw new RuntimeException("write proto to file failed");
        }
    }

    /**
     * 计算字节数组的CRC32校验码
     * 
     * @param content 要计算校验码的字节数组
     * @return CRC32校验码值
     */
    public static long getCRC32(byte[] content) {
        CRC32 crc32 = new CRC32();
        crc32.update(content);
        return crc32.getValue();
    }

    // 保持向后兼容的方法名，委托给新方法
    public static List<String> getSortedFilesInDir(
            String directoryPath, String baseDirectory) throws IOException {
        return getOrderedFileList(directoryPath, baseDirectory);
    }

    public static RandomAccessFile openFile(String directory, String filename, String mode) {
        return createFileHandle(directory, filename, mode);
    }

    public static void closeFile(RandomAccessFile fileHandle) {
        closeFileHandle(fileHandle);
    }

    public static void closeFile(FileInputStream inputStream) {
        closeFileHandle(inputStream);
    }

    public static void closeFile(FileOutputStream outputStream) {
        closeFileHandle(outputStream);
    }
}
