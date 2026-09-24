package com.jojo.prompt.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * MySQL 计数直写微基准：与 Redis INCR 路径的 A/B 对照。
 *
 * <p>对应主项目 prompt.count.mode=direct-db 分支的真实 SQL：
 * {@code UPDATE prompt SET view_count = view_count + ? ... WHERE id = ?}
 * （单行热点更新，autocommit）。</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class MysqlCountBenchmark {

    private Connection connection;
    private PreparedStatement updateStmt;

    @Setup
    public void setup() throws Exception {
        String url = System.getProperty("bench.mysql.url",
                "jdbc:mysql://localhost:3306/ai_prompt?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String user = System.getProperty("bench.mysql.user", "root");
        String password = System.getProperty("bench.mysql.password", "123456");
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(true);
        // 专用基准表，避免污染业务表数据
        try (java.sql.Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS bench_count (id BIGINT PRIMARY KEY, c BIGINT NOT NULL)");
            stmt.execute("DELETE FROM bench_count");
            for (int i = 1; i <= 200; i++) {
                stmt.execute("INSERT INTO bench_count (id, c) VALUES (" + i + ", 0)");
            }
        }
        updateStmt = connection.prepareStatement(
                "UPDATE bench_count SET c = c + 1 WHERE id = ?");
    }

    @TearDown
    public void teardown() throws Exception {
        updateStmt.close();
        connection.close();
    }

    /** 单行热点计数更新（200 行随机分布，autocommit） */
    @Benchmark
    public int singleRowUpdate() throws Exception {
        updateStmt.setLong(1, ThreadLocalRandom.current().nextLong(1, 201));
        return updateStmt.executeUpdate();
    }
}
