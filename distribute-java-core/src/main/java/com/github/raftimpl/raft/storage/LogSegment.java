package com.github.raftimpl.raft.storage;

import com.github.raftimpl.raft.proto.RaftProto;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * 日志分段类
 * 用于管理Raft日志的分段存储，每个LogSegment对应一个日志文件
 * 支持日志条目的读取、写入和索引管理
 * 
 */
public class LogSegment {

    /**
     * 日志记录类
     * 封装了日志条目及其在文件中的偏移量
     */
    public static class Record {
        /** 日志条目在文件中的偏移量 */
        public long offset;
        /** 实际的日志条目 */
        public RaftProto.LogEntry entry;
        
        /**
         * 构造函数
         * 
         * @param offset 文件偏移量
         * @param entry 日志条目
         */
        public Record(long offset, RaftProto.LogEntry entry) {
            this.offset = offset;
            this.entry = entry;
        }
    }

    /** 标识该段是否可写入 */
    private boolean canWrite;
    
    /** 该段中第一个日志条目的索引 */
    private long firstIndex;
    
    /** 该段中最后一个日志条目的索引 */
    private long lastIndex;
    
    /** 该段文件的大小（字节） */
    private long dataSize;
    
    /** 该段对应的文件名 */
    private String fileName;
    
    /** 随机访问文件对象，用于读写操作 */
    private RandomAccessFile randomAccessFile;
    
    /** 内存中缓存的日志条目列表 */
    private List<Record> recordList = new ArrayList<>();

    /**
     * 根据日志索引获取日志条目
     * 
     * @param logIndex 日志索引
     * @return 对应的日志条目，如果不存在则返回null
     */
    public RaftProto.LogEntry getEntry(long logIndex) {
        // 检查段是否为空
        if (firstIndex == 0 || lastIndex == 0) {
            return null;
        }
        // 检查索引是否在该段范围内
        if (logIndex < firstIndex || logIndex > lastIndex) {
            return null;
        }
        // 计算在列表中的位置并返回对应条目
        int positionInList = (int) (logIndex - firstIndex);
        return recordList.get(positionInList).entry;
    }

    /**
     * 检查该段是否可写
     * 
     * @return 是否可写
     */
    public boolean isCanWrite() {
        return canWrite;
    }

    /**
     * 设置该段的可写状态
     * 
     * @param canWrite 是否可写
     */
    public void setCanWrite(boolean canWrite) {
        this.canWrite = canWrite;
    }

    /**
     * 获取起始索引
     * 
     * @return 起始索引
     */
    public long getStartIndex() {
        return firstIndex;
    }

    /**
     * 设置起始索引
     * 
     * @param startIndex 起始索引
     */
    public void setStartIndex(long startIndex) {
        this.firstIndex = startIndex;
    }

    /**
     * 获取结束索引
     * 
     * @return 结束索引
     */
    public long getEndIndex() {
        return lastIndex;
    }

    /**
     * 设置结束索引
     * 
     * @param endIndex 结束索引
     */
    public void setEndIndex(long endIndex) {
        this.lastIndex = endIndex;
    }

    /**
     * 获取文件大小
     * 
     * @return 文件大小（字节）
     */
    public long getFileSize() {
        return dataSize;
    }

    /**
     * 设置文件大小
     * 
     * @param fileSize 文件大小（字节）
     */
    public void setFileSize(long fileSize) {
        this.dataSize = fileSize;
    }

    /**
     * 获取文件名
     * 
     * @return 文件名
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置文件名
     * 
     * @param fileName 文件名
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 获取随机访问文件对象
     * 
     * @return 随机访问文件对象
     */
    public RandomAccessFile getRandomAccessFile() {
        return randomAccessFile;
    }

    /**
     * 设置随机访问文件对象
     * 
     * @param randomAccessFile 随机访问文件对象
     */
    public void setRandomAccessFile(RandomAccessFile randomAccessFile) {
        this.randomAccessFile = randomAccessFile;
    }

    /**
     * 获取日志条目列表
     * 
     * @return 日志条目列表
     */
    public List<Record> getEntries() {
        return recordList;
    }

    /**
     * 设置日志条目列表
     * 
     * @param entries 日志条目列表
     */
    public void setEntries(List<Record> entries) {
        this.recordList = entries;
    }
}
