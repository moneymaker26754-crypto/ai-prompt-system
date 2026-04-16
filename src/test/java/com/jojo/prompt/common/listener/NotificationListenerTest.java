package com.jojo.prompt.common.listener;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jojo.prompt.common.event.PromptFavoriteEvent;
import com.jojo.prompt.common.event.PromptLikeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationListenerTest {

    private final NotificationListener listener = new NotificationListener();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(NotificationListener.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void onPromptLikedShouldSendNotificationToAuthorWhenActorDiffers() {
        listener.onPromptLiked(new PromptLikeEvent(100L, 10L, 20L, LocalDateTime.of(2026, 4, 14, 11, 0)));

        assertThat(sentMessages()).anyMatch(message ->
                message.contains("userId=20")
                        && message.contains("user 10 like your prompt")
                        && message.contains("type=like")
                        && message.contains("relatedId=100"));
    }

    @Test
    void onPromptLikedShouldSkipNotificationForSelfLike() {
        listener.onPromptLiked(new PromptLikeEvent(100L, 10L, 10L, LocalDateTime.of(2026, 4, 14, 11, 1)));

        assertThat(sentMessages()).isEmpty();
    }

    @Test
    void onPromptFavoriteShouldSendNotificationToAuthorWhenActorDiffers() {
        listener.onPromptFavorite(new PromptFavoriteEvent(101L, 11L, 21L, LocalDateTime.of(2026, 4, 14, 11, 2)));

        assertThat(sentMessages()).anyMatch(message ->
                message.contains("userId=21")
                        && message.contains("user 11 favorite your prompt")
                        && message.contains("type=favorite")
                        && message.contains("relatedId=101"));
    }

    @Test
    void onPromptFavoriteShouldSkipNotificationForSelfFavorite() {
        listener.onPromptFavorite(new PromptFavoriteEvent(101L, 11L, 11L, LocalDateTime.of(2026, 4, 14, 11, 3)));

        assertThat(sentMessages()).isEmpty();
    }

    private List<String> sentMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("send message ->"))
                .toList();
    }
}
