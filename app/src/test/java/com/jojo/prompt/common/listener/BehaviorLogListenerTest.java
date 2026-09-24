package com.jojo.prompt.common.listener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jojo.prompt.common.event.PromptCreateEvent;
import com.jojo.prompt.common.event.PromptFavoriteEvent;
import com.jojo.prompt.common.event.PromptLikeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorLogListenerTest {

    private final BehaviorLogListener listener = new BehaviorLogListener();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(BehaviorLogListener.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void onPromptLikedShouldLogLikeBehavior() {
        listener.onPromptLiked(new PromptLikeEvent(100L, 10L, 20L, LocalDateTime.of(2026, 4, 14, 10, 0)));

        assertThat(findBehaviorLog()).contains("action=like").contains("relatedId=100");
    }

    @Test
    void onPromptFavoriteShouldLogFavoriteBehavior() {
        listener.onPromptFavorite(new PromptFavoriteEvent(101L, 11L, 21L, LocalDateTime.of(2026, 4, 14, 10, 1)));

        assertThat(findBehaviorLog()).contains("action=favorite").contains("relatedId=101");
    }

    @Test
    void onPromptCreatedShouldLogCreateBehavior() {
        listener.onPromptCreated(new PromptCreateEvent(102L, 12L, "title", LocalDateTime.of(2026, 4, 14, 10, 2)));

        assertThat(findBehaviorLog()).contains("action=create").contains("relatedId=102");
    }

    private String findBehaviorLog() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("log behavior ->"))
                .findFirst()
                .orElseThrow();
    }
}
