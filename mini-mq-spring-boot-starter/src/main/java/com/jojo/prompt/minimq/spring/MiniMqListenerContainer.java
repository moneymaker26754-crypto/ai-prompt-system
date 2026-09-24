package com.jojo.prompt.minimq.spring;

import com.jojo.prompt.minimq.core.MiniMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @MiniMqListener 容器：扫描所有 bean 方法上的注解，按 (topic, group)
 * 建立拉取线程；消费成功 ack、异常 nack（由服务端重试/死信兜底）。
 *
 * <p>简化边界（诚实说明）：每 (topic,group) 一个拉取线程，不做分区级
 * 并发；与 mini-mq 核心的「单队列 + 组游标」模型一致。</p>
 */
@Slf4j
public class MiniMqListenerContainer implements ApplicationContextAware, InitializingBean, DisposableBean {

    private final MiniMqTemplate template;
    private final MiniMqProperties properties;
    private ApplicationContext applicationContext;
    private final List<ExecutorService> executors = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MiniMqListenerContainer(MiniMqTemplate template, MiniMqProperties properties) {
        this.template = template;
        this.properties = properties;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isEnabled()) {
            return;
        }
        List<ListenerBinding> bindings = scanBindings();
        if (bindings.isEmpty()) {
            log.info("no @MiniMqListener methods found, container idle");
            return;
        }
        running.set(true);
        for (ListenerBinding binding : bindings) {
            ExecutorService executor = Executors.newSingleThreadExecutor(r ->
                    new Thread(r, "mini-mq-listener-" + binding.topic + "-" + binding.group));
            executors.add(executor);
            executor.submit(() -> pollLoop(binding));
            log.info("mini-mq listener started, topic={}, group={}", binding.topic, binding.group);
        }
    }

    @Override
    public void destroy() {
        running.set(false);
        executors.forEach(ExecutorService::shutdownNow);
    }

    private void pollLoop(ListenerBinding binding) {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<MiniMessage> messages = template.poll(
                        binding.topic, binding.group, properties.getConsumer().getPollTimeoutMs());
                for (MiniMessage message : messages) {
                    boolean ok = dispatch(binding, message);
                    if (ok) {
                        template.ack(binding.topic, binding.group, message.offset());
                    } else {
                        template.nack(binding.topic, binding.group, message.offset());
                    }
                }
            } catch (Exception ex) {
                log.warn("mini-mq poll loop error, topic={}, group={}, will retry",
                        binding.topic, binding.group, ex);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private boolean dispatch(ListenerBinding binding, MiniMessage message) {
        try {
            Object[] args;
            if (binding.parameterType == String.class) {
                args = new Object[]{new String(message.payload(), java.nio.charset.StandardCharsets.UTF_8)};
            } else {
                args = new Object[]{message.payload()};
            }
            binding.method.invoke(binding.bean, args);
            return true;
        } catch (Exception ex) {
            log.warn("mini-mq listener invoke failed, topic={}, offset={}, will nack",
                    binding.topic, message.offset(), ex);
            return false;
        }
    }

    private List<ListenerBinding> scanBindings() {
        List<ListenerBinding> bindings = new ArrayList<>();
        for (String name : applicationContext.getBeanDefinitionNames()) {
            // 跳过容器自身与仍在创建中的 bean（容器在自身 afterPropertiesSet 中扫描）
            if ("miniMqListenerContainer".equals(name)) {
                continue;
            }
            Object bean;
            try {
                bean = applicationContext.getBean(name);
            } catch (org.springframework.beans.factory.BeanCurrentlyInCreationException ex) {
                log.debug("skip bean still in creation during listener scan: {}", name);
                continue;
            }
            for (Method method : bean.getClass().getDeclaredMethods()) {
                MiniMqListener annotation = method.getAnnotation(MiniMqListener.class);
                if (annotation == null) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1 || (params[0] != String.class && params[0] != byte[].class)) {
                    throw new IllegalStateException("@MiniMqListener method must accept exactly one "
                            + "String or byte[] parameter: " + method);
                }
                bindings.add(new ListenerBinding(annotation.topic(), annotation.group(), bean, method, params[0]));
            }
        }
        return bindings;
    }

    private record ListenerBinding(String topic, String group, Object bean, Method method,
                                   Class<?> parameterType) {
    }
}
