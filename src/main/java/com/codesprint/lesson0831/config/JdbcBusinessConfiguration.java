package com.codesprint.lesson0831.config;

import com.codesprint.lesson0831.agent.ConversationMemory;
import com.codesprint.lesson0831.agent.JdbcConversationMemory;
import com.codesprint.lesson0831.order.JdbcOrderService;
import com.codesprint.lesson0831.order.OrderService;
import com.codesprint.lesson0831.product.JdbcProductService;
import com.codesprint.lesson0831.product.ProductService;
import com.codesprint.lesson0831.product.RedisProductDetailCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/** 生产与交易演示共享数据库商品和订单；普通教学环境保留原来的内存实现。 */
@Configuration
@Profile({"prod", "trade-demo"})
public class JdbcBusinessConfiguration {
    @Bean
    public ProductService productService(DataSource dataSource, ObjectProvider<RedisProductDetailCache> cache) {
        return new JdbcProductService(dataSource, cache.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(name = "shopguard.catalog.redis.url")
    public RedisProductDetailCache productDetailCache(@Value("${shopguard.catalog.redis.url}") URI url, ObjectMapper json) {
        return new RedisProductDetailCache(url, json);
    }

    @Bean
    public OrderService orderService(DataSource dataSource) {
        return new JdbcOrderService(dataSource);
    }

    @Bean
    @Profile("prod")
    public ConversationMemory conversationMemory(DataSource dataSource) {
        return new JdbcConversationMemory(dataSource);
    }
}
