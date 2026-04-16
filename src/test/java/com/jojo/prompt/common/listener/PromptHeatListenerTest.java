package com.jojo.prompt.common.listener;

import com.jojo.prompt.common.event.PromptHeatEvent;
import com.jojo.prompt.service.RedisCacheService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.LocalDateTime;

class PromptHeatListenerTest {

    private final RedisCacheService redisCacheService = Mockito.mock(RedisCacheService.class);
    private final PromptHeatListener listener = new PromptHeatListener(redisCacheService);

    @ParameterizedTest
    @CsvSource({
            "view,1.0",
            "copy,3.0",
            "like,5.0",
            "unlike,-5.0",
            "favorite,8.0",
            "unfavorite,-8.0"
    })
    void shouldUpdateHotRankingForSupportedActions(String action, double delta) {
        listener.onPromptHeatListener(new PromptHeatEvent(100L, 10L, action, LocalDateTime.of(2026, 4, 14, 12, 0)));

        Mockito.verify(redisCacheService).updateHotRanking(100L, "hot", delta);
        Mockito.reset(redisCacheService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "share", "unknown"})
    void shouldIgnoreUnsupportedActions(String action) {
        listener.onPromptHeatListener(new PromptHeatEvent(100L, 10L, action, LocalDateTime.of(2026, 4, 14, 12, 1)));

        Mockito.verifyNoInteractions(redisCacheService);
    }
}
