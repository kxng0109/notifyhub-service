package io.github.kxng0109.notifyhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class RabbitMQConfig {
    public static final String DELAYED_EXCHANGE_NAME = "notifyhub_delayed_exchange";
    public static final String QUEUE_NAME = "notifications_queue";
    public static final String ROUTING_KEY = "notifications.routing.key";
    public static final String FAILURES_EXCHANGE_NAME = "notifications_failures_exchange";
    public static final String FAILURES_QUEUE_NAME = "notifications_failures_queue";
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    @Bean
    public CustomExchange delayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "topic");

        return new CustomExchange(
                DELAYED_EXCHANGE_NAME,
                "x-delayed-message",
                true,
                false,
                args
        );
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    public Binding binding(Queue notificationQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(notificationQueue)
                             .to(delayedExchange)
                             .with(ROUTING_KEY)
                             .noargs();
    }

    @Bean
    public Queue failuresQueue() {
        return QueueBuilder.durable(FAILURES_QUEUE_NAME).build();
    }

    @Bean
    public FanoutExchange failuresExchange() {
        return new FanoutExchange(FAILURES_EXCHANGE_NAME);
    }

    @Bean
    public Binding failuresBinding(Queue failuresQueue, FanoutExchange failuresExchange) {
        return BindingBuilder.bind(failuresQueue).to(failuresExchange);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            @Value("${spring.rabbitmq.listener.simple.concurrency:4}") int concurrentConsumers,
            @Value("${spring.rabbitmq.listener.simple.max-concurrency:10}") int maxConcurrentConsumers,
            @Value("${spring.rabbitmq.listener.simple.prefetchCount:50}") int prefetchCount
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);

        factory.setConcurrentConsumers(concurrentConsumers);
        factory.setMaxConcurrentConsumers(maxConcurrentConsumers);
        factory.setPrefetchCount(prefetchCount);

        // Enable auto-scaling based on load
        factory.setConsecutiveActiveTrigger(5);
        factory.setConsecutiveIdleTrigger(10);
        factory.setStartConsumerMinInterval(3000L);
        return factory;
    }

    @Bean
    public Executor rabbitmqPublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("rabbitmq-pub-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(30);
        executor.initialize();
        logger.info("🚀 RabbitMQ Publisher Executor created with core={}, max={}",
                    executor.getCorePoolSize(),
                    executor.getMaxPoolSize()
        );
        return executor;
    }

    @Bean
    public Executor emailSendingExecutor(
            @Value("${notifyhub.email.executor.core-pool-size:8}") int corePoolSize,
            @Value("${notifyhub.email.executor.max-pool-size:16}") int maxPoolSize,
            @Value("${notifyhub.email.executor.queue-capacity:10000}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("email-sender-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        logger.info("🚀 Email Sending Executor created with core={}, max={}, queue={}",
                    corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
