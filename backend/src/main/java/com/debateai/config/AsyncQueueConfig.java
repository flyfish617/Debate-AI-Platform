package com.debateai.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 异步任务队列配置
 */
@Configuration
public class AsyncQueueConfig {

    public static final String DEBATE_EXCHANGE = "debateai.debate.exchange";
    public static final String DEBATE_CLOSING_QUEUE = "debateai.debate.closing.queue";
    public static final String DEBATE_CLOSING_ROUTING_KEY = "debate.closing.generate";

    public static final String LEADERBOARD_EXCHANGE = "debateai.leaderboard.exchange";
    public static final String LEADERBOARD_REFRESH_QUEUE = "debateai.leaderboard.refresh.queue";
    public static final String LEADERBOARD_REFRESH_ROUTING_KEY = "leaderboard.refresh";

    /**
     * 声明辩论异步任务交换机
     * @return 辩论交换机
     */
    @Bean
    public DirectExchange debateExchange() {
        return new DirectExchange(DEBATE_EXCHANGE, true, false);
    }

    /**
     * 声明AI结辩队列
     * @return AI结辩队列
     */
    @Bean
    public Queue debateClosingQueue() {
        return QueueBuilder.durable(DEBATE_CLOSING_QUEUE).build();
    }

    /**
     * 绑定AI结辩队列
     * @return 队列绑定
     */
    @Bean
    public Binding debateClosingBinding() {
        return BindingBuilder.bind(debateClosingQueue())
                .to(debateExchange())
                .with(DEBATE_CLOSING_ROUTING_KEY);
    }

    /**
     * 声明排行榜异步任务交换机
     * @return 排行榜交换机
     */
    @Bean
    public DirectExchange leaderboardExchange() {
        return new DirectExchange(LEADERBOARD_EXCHANGE, true, false);
    }

    /**
     * 声明排行榜刷新队列
     * @return 排行榜刷新队列
     */
    @Bean
    public Queue leaderboardRefreshQueue() {
        return QueueBuilder.durable(LEADERBOARD_REFRESH_QUEUE).build();
    }

    /**
     * 绑定排行榜刷新队列
     * @return 队列绑定
     */
    @Bean
    public Binding leaderboardRefreshBinding() {
        return BindingBuilder.bind(leaderboardRefreshQueue())
                .to(leaderboardExchange())
                .with(LEADERBOARD_REFRESH_ROUTING_KEY);
    }

    /**
     * 注册JSON消息转换器
     * @return 消息转换器
     */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置RabbitTemplate使用JSON消息
     * @param connectionFactory RabbitMQ连接工厂
     * @param messageConverter 消息转换器
     * @return RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    /**
     * 配置监听容器使用JSON消息
     * @param connectionFactory RabbitMQ连接工厂
     * @param messageConverter 消息转换器
     * @return 监听容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
