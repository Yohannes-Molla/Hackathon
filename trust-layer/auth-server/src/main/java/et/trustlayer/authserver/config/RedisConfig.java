package et.trustlayer.authserver.config;

import et.trustlayer.authserver.redis.RedisSessionEventBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    /**
     * Subscribes to trust-layer event channels and forwards messages to WebSocket clients
     * through {@link et.trustlayer.authserver.websocket.SessionEventPublisher}.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisSessionEventBridge sessionEventBridge) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(sessionEventBridge, new ChannelTopic(RedisSessionEventBridge.CHAN_EKYC_COMPLETE));
        container.addMessageListener(sessionEventBridge, new ChannelTopic(RedisSessionEventBridge.CHAN_CREDENTIAL_BOUND));
        container.addMessageListener(sessionEventBridge, new ChannelTopic(RedisSessionEventBridge.CHAN_TX_APPROVED));
        container.addMessageListener(sessionEventBridge, new ChannelTopic(RedisSessionEventBridge.CHAN_TX_REJECTED));
        return container;
    }
}
