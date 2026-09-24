package com.jojo.prompt.minimq.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LogStore 存储引擎测试：往返、顺序、分段滚动、重启恢复、损坏截断。
 */
class LogStoreTest {

    @TempDir
    Path dir;

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void appendAndReadRoundTrip() throws IOException {
        LogStore store = new LogStore(dir);
        long off = store.append(bytes("hello"));
        assertThat(off).isZero();
        assertThat(new String(store.read(off), StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(store.nextOffset()).isEqualTo(4 + 5);
        store.close();
    }

    @Test
    void readFromReturnsOrderedSuffix() throws IOException {
        LogStore store = new LogStore(dir);
        long o1 = store.append(bytes("a"));
        long o2 = store.append(bytes("b"));
        long o3 = store.append(bytes("c"));
        List<byte[]> fromSecond = store.readFrom(o2);
        assertThat(fromSecond).extracting(b -> new String(b, StandardCharsets.UTF_8))
                .containsExactly("b", "c");
        assertThat(store.readFrom(o1)).hasSize(3);
        assertThat(store.readFrom(o3 + 100)).isEmpty();
        store.close();
    }

    @Test
    void readMissingOffsetRejected() throws IOException {
        LogStore store = new LogStore(dir);
        assertThatThrownBy(() -> store.read(42L))
                .isInstanceOf(IllegalArgumentException.class);
        store.close();
    }

    @Test
    void segmentRollsOverWhenSizeExceeded() throws IOException {
        LogStore store = new LogStore(dir, 256);
        // 每条记录 4 字节头 + 100 字节体；写 5 条必然滚动多个段
        byte[] body = new byte[100];
        List<Long> offsets = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            body[0] = (byte) i;
            offsets.add(store.append(body));
        }
        assertThat(Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".log")).count())
                .isGreaterThan(1);
        for (Long off : offsets) {
            assertThat(store.read(off)).hasSize(100);
        }
        store.close();
    }

    @Test
    void reopenRebuildsIndex() throws IOException {
        LogStore store = new LogStore(dir);
        long o1 = store.append(bytes("first"));
        long o2 = store.append(bytes("second"));
        store.close();

        LogStore reopened = new LogStore(dir);
        assertThat(new String(reopened.read(o1), StandardCharsets.UTF_8)).isEqualTo("first");
        assertThat(new String(reopened.read(o2), StandardCharsets.UTF_8)).isEqualTo("second");
        // 恢复后 offset 连续性不丢
        long o3 = reopened.append(bytes("third"));
        assertThat(o3).isEqualTo(o2 + 4 + 6);
        reopened.close();
    }

    @Test
    void corruptedTailRecordIsTruncatedOnRecovery() throws IOException {
        LogStore store = new LogStore(dir);
        long good = store.append(bytes("intact"));
        store.close();

        // 向最后一段追加半条损坏记录：声明 100 字节长度但只写 10 字节
        Path segment = Files.list(dir).findFirst().orElseThrow();
        try (RandomAccessFile raf = new RandomAccessFile(segment.toFile(), "rw")) {
            raf.seek(raf.length());
            raf.writeInt(100);
            raf.write(new byte[10]);
        }

        LogStore reopened = new LogStore(dir);
        assertThat(new String(reopened.read(good), StandardCharsets.UTF_8)).isEqualTo("intact");
        // 截断后下一条 append 从完整记录末尾开始
        long next = reopened.append(bytes("after"));
        assertThat(next).isEqualTo(good + 4 + 6);
        reopened.close();
    }
}
