package com.jojo.prompt.common.handler;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.entity.Prompt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractReviewHandlerTest {

    @Test
    void reviewShouldInvokeHandlersInOrder() {
        List<String> invocations = new ArrayList<>();
        RecordingHandler first = new RecordingHandler("first", invocations, false);
        RecordingHandler second = new RecordingHandler("second", invocations, false);
        first.setNext(second);

        first.review(new Prompt());

        assertThat(invocations).containsExactly("first", "second");
    }

    @Test
    void reviewShouldStopWhenCurrentHandlerFails() {
        List<String> invocations = new ArrayList<>();
        RecordingHandler first = new RecordingHandler("first", invocations, true);
        RecordingHandler second = new RecordingHandler("second", invocations, false);
        first.setNext(second);

        assertThatThrownBy(() -> first.review(new Prompt()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("first failed");

        assertThat(invocations).containsExactly("first");
    }

    @Test
    void setNextShouldReturnNextHandler() {
        RecordingHandler first = new RecordingHandler("first", new ArrayList<>(), false);
        RecordingHandler second = new RecordingHandler("second", new ArrayList<>(), false);

        PromptReviewHandler returned = first.setNext(second);

        assertThat(returned).isSameAs(second);
    }

    private static final class RecordingHandler extends AbstractReviewHandler {
        private final String name;
        private final List<String> invocations;
        private final boolean shouldFail;

        private RecordingHandler(String name, List<String> invocations, boolean shouldFail) {
            this.name = name;
            this.invocations = invocations;
            this.shouldFail = shouldFail;
        }

        @Override
        protected void doReview(Prompt prompt) {
            invocations.add(name);
            if (shouldFail) {
                throw new BusinessException(name + " failed");
            }
        }

        @Override
        protected String getHandlerName() {
            return name;
        }
    }
}
