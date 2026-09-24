package com.jojo.prompt.common.config;

import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.infra.bloom.RedisBloomFilter;
import com.jojo.prompt.mapper.PromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prompt ID 布隆过滤器预热。
 *
 * <p>布隆无假阴性，但只认识「预热后加入」的 ID：新部署实例若直接启用拦截，
 * 会把存量 Prompt 误判为不存在（404）。因此：</p>
 * <ol>
 *   <li>应用就绪时批量读取存量 Prompt ID 写入布隆；</li>
 *   <li>预热完成前 {@link #isWarmed()} 为 false，查询链路跳过布隆（fail-open）；</li>
 *   <li>预热完成后新建的 Prompt 在创建路径实时加入。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptBloomWarmer {

    private final PromptMapper promptMapper;
    private final RedisBloomFilter promptIdBloomFilter;

    private final AtomicBoolean warmed = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long start = System.currentTimeMillis();
        int count = 0;
        try {
            List<Prompt> prompts = promptMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Prompt>()
                            .select(Prompt::getId));
            for (Prompt prompt : prompts) {
                promptIdBloomFilter.add(String.valueOf(prompt.getId()));
                count++;
            }
            warmed.set(true);
            log.info("prompt id bloom filter warmed, count={}, costMs={}, config={}",
                    count, System.currentTimeMillis() - start, promptIdBloomFilter.describe());
        } catch (Exception ex) {
            log.error("prompt id bloom filter warm-up failed, bloom disabled (fail-open)", ex);
        }
    }

    public boolean isWarmed() {
        return warmed.get();
    }
}
