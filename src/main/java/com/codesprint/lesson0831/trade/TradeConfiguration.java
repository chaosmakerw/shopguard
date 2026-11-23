package com.codesprint.lesson0831.trade;

import com.codesprint.lesson0831.trade.core.JdbcTradeService;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@Profile({"prod", "trade-demo"})
@EnableScheduling
public class TradeConfiguration {
    @Bean
    @DependsOnDatabaseInitialization
    public JdbcTradeService jdbcTradeService(DataSource dataSource, Environment environment) throws SQLException {
        String database;
        try (Connection connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName();
        }
        String schema = "MySQL".equalsIgnoreCase(database)
                ? "db/trade-schema-mysql.sql" : "db/trade-schema.sql";
        // 只新增交易扩展表；不重建原表、不导入生产样例、不改变已有订单。
        new ResourceDatabasePopulator(new ClassPathResource(schema)).execute(dataSource);
        long seconds = environment.getProperty("shopguard.trade.payment-window-seconds", Long.class, 900L);
        if (seconds < 1 || seconds > 3600) {
            throw new IllegalStateException("交易付款期限必须为 1 至 3600 秒");
        }
        return new JdbcTradeService(dataSource, Clock.systemUTC(), Duration.ofSeconds(seconds));
    }
}
