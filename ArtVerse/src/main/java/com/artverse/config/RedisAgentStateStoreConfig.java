package com.artverse.config;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RedisAgentStateStoreConfig {

    @Bean("agentScopeRedisClient")
    public RedisClient agentScopeRedisClient(RedisProperties redisProperties) {
        String host = redisProperties.getHost();
        int port = redisProperties.getPort();
        int database = redisProperties.getDatabase();
        String url = "redis://" + host + ":" + port + "/" + database;
        log.info("Creating AgentScope RedisClient for state store: {}", url);
        return RedisClient.create(url);
    }

    @Bean
    public AgentStateStore redisAgentStateStore(@org.springframework.beans.factory.annotation.Qualifier("agentScopeRedisClient") RedisClient agentScopeRedisClient) {
        return RedisAgentStateStore.builder()
                .lettuceClient(agentScopeRedisClient)
                .keyPrefix("artverse:agent:")
                .build();
    }
}
