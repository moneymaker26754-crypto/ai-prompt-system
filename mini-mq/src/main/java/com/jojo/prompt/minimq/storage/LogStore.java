package com.jojo.prompt.minimq.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分段 append-only 存储引擎。
 *
 * <p>设计决策（自研 MQ 的核心卖点，面试可逐条展开）：</p>
 * <ol>
 *   <li><b>顺序写</b>：消息只追加，靠磁盘顺序写性能换取吞吐（随机写是磁盘的噩梦）；</li>
 *   <li><b>长度前缀记录</b>：4 字节大端长度 + payload，读取时可精确切分记录，
 *       也是恢复时判定「尾部半条损坏记录」的依据；</li>
 *   <li><b>分段滚动</b>：单文件超过 segmentBytes（默认 64MB）滚动新段，
 *       避免单文件无限增长，老段将来可独立归档/删除；</li>
 *   <li><b>内存索引 + 启动重建</b>：offset → (段文件, 位置) 索引放内存，
 *       open 时全量扫描段文件重建，扫描遇尾部损坏记录则截断并告警；</li>
 *   <li><b>写线程安全</b>：append 走 synchronized，保证 offset 分配与写盘原子。</li>
 * </ol>
 *
 * <p>诚实边界：无 mmap、无零拷贝、无 fsync 批量组提交（默认每条 flush），
 * 这些留给与 RabbitMQ 的对比基准作为后续优化项。</p>
 */
public class LogStore {

    private static final Logger log = LoggerFactory.getLogger(LogStore.class);
    private static final int LENGTH_BYTES = 4;
    private static final Pattern SEGMENT_NAME = Pattern.compile("(\\d{20})\\.log");

    private final Path dir;
    private final long segmentBytes;

    /** offset → 段内位置 */
    private final Map<Long, Long> index = new ConcurrentHashMap<>();
    /** 段起始 offset → 段文件（全程保持打开，close() 时统一释放） */
    private final Map<Long, RandomAccessFile> segmentFiles = new ConcurrentHashMap<>();
    /** 当前写入段 */
    private RandomAccessFile currentSegment;
    /** 当前段起始 offset */
    private long currentSegmentBase;
    /** 当前段文件大小（= 下一条记录的写入位置） */
    private long currentSegmentSize;
    /** 下一个全局 offset */
    private long nextOffset;

    public LogStore(Path dir) throws IOException {
        this(dir, 64L * 1024 * 1024);
    }

    public LogStore(Path dir, long segmentBytes) throws IOException {
        this.dir = dir;
        this.segmentBytes = segmentBytes;
        Files.createDirectories(dir);
        recover();
        openLastSegment();
    }

    /** 扫描全部段重建索引；尾部记录长度损坏则截断并告警 */
    private void recover() throws IOException {
        List<Path> files = listSegments();
        for (Path file : files) {
            long base = segmentBaseOf(file);
            RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw");
            segmentFiles.put(base, raf);
            long pos = 0;
            long size = raf.length();
            while (pos + LENGTH_BYTES <= size) {
                raf.seek(pos);
                int len = raf.readInt();
                if (len <= 0 || pos + LENGTH_BYTES + len > size) {
                    // 尾部半条/损坏记录：截断到上一完整记录末尾
                    log.warn("truncating corrupted tail record, segment={}, pos={}, len={}, size={}",
                            file.getFileName(), pos, len, size);
                    raf.setLength(pos);
                    break;
                }
                index.put(base + pos, pos);
                pos += LENGTH_BYTES + len;
            }
        }
        // 下一个全局 offset = 最大 offset + 其记录长度
        nextOffset = index.keySet().stream().max(Long::compareTo)
                .map(maxOff -> {
                    long pos = index.get(maxOff);
                    long base = maxOff - pos;
                    RandomAccessFile raf = segmentFiles.get(base);
                    try {
                        raf.seek(pos);
                        int len = raf.readInt();
                        return maxOff + LENGTH_BYTES + len;
                    } catch (IOException e) {
                        throw new IllegalStateException("recompute nextOffset failed", e);
                    }
                })
                .orElse(0L);
    }

    /** 打开（或创建）最后一段作为写入段 */
    private void openLastSegment() throws IOException {
        List<Path> files = listSegments();
        if (files.isEmpty()) {
            rollSegment(0L);
            return;
        }
        Path last = files.get(files.size() - 1);
        long base = segmentBaseOf(last);
        RandomAccessFile raf = segmentFiles.get(base);
        long size = raf.length();
        currentSegment = raf;
        currentSegmentBase = base;
        currentSegmentSize = size;
        raf.seek(size);
    }

    private List<Path> listSegments() throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> SEGMENT_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(this::segmentBaseOf))
                    .toList();
        }
    }

    /** 追加一条记录，返回全局 offset */
    public synchronized long append(byte[] payload) throws IOException {
        long offset = nextOffset;
        int recordBytes = LENGTH_BYTES + payload.length;
        if (currentSegment == null || currentSegmentSize + recordBytes > segmentBytes) {
            rollSegment(offset);
        }
        currentSegment.writeInt(payload.length);
        currentSegment.write(payload);
        currentSegment.getFD().sync(); // 默认每条消息 fsync：优先持久性；批量组提交留给基准对比
        index.put(offset, currentSegmentSize);
        currentSegmentSize += recordBytes;
        nextOffset += recordBytes;
        return offset;
    }

    private void rollSegment(long baseOffset) throws IOException {
        // 注意：旧段不能关闭——历史记录仍可能被 read()/readFrom() 读取，统一由 close() 释放
        Path file = dir.resolve(String.format("%020d.log", baseOffset));
        RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw");
        segmentFiles.put(baseOffset, raf);
        currentSegment = raf;
        currentSegmentBase = baseOffset;
        currentSegmentSize = 0;
    }

    /** 读取单条记录 payload */
    public byte[] read(long offset) throws IOException {
        Long pos = index.get(offset);
        if (pos == null) {
            throw new IllegalArgumentException("offset not found: " + offset);
        }
        RandomAccessFile raf = segmentFiles.get(offset - pos);
        synchronized (this) {
            raf.seek(pos);
            int len = raf.readInt();
            byte[] payload = new byte[len];
            raf.readFully(payload);
            return payload;
        }
    }

    /** 顺序读取 offset（含）之后的所有记录 */
    public List<byte[]> readFrom(long offset) throws IOException {
        List<Long> offsets = index.keySet().stream()
                .filter(o -> o >= offset)
                .sorted()
                .toList();
        List<byte[]> result = new ArrayList<>(offsets.size());
        for (Long off : offsets) {
            result.add(read(off));
        }
        return result;
    }

    /** 下一个全局 offset（供延迟消息预测派发 offset） */
    public long nextOffset() {
        return nextOffset;
    }

    private long segmentBaseOf(Path file) {
        Matcher m = SEGMENT_NAME.matcher(file.getFileName().toString());
        if (!m.matches()) {
            throw new IllegalArgumentException("invalid segment file name: " + file.getFileName());
        }
        return Long.parseLong(m.group(1));
    }

    public void close() throws IOException {
        for (RandomAccessFile raf : segmentFiles.values()) {
            raf.close();
        }
        segmentFiles.clear();
        currentSegment = null;
    }
}
