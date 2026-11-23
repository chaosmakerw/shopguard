package com.codesprint.lesson0831.trade.demo;

import com.codesprint.lesson0831.trade.core.JdbcTradeService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import java.util.Set;

/** 本地交易使用单独 H2 文件；不连接既有生产库，不重置已有演示订单或库存。 */
@Configuration(proxyBeanMethods = false)
@Profile("trade-demo")
public class TradeDemoDatabaseConfiguration {
    @Bean(destroyMethod = "close")
    public HikariDataSource tradeDemoDataSource(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("dev")) || environment.acceptsProfiles(Profiles.of("prod", "test"))) {
            throw new IllegalStateException("trade-demo 只允许与 dev 同时使用");
        }
        if (!Set.of("127.0.0.1", "localhost", "::1").contains(environment.getProperty("server.address", ""))) {
            throw new IllegalStateException("本地交易演示必须绑定回环地址");
        }
        String url = environment.getRequiredProperty("spring.datasource.url");
        if (!url.startsWith("jdbc:h2:file:")) throw new IllegalStateException("交易演示只允许独立 H2 文件数据库");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword("");
        // H2 默认延迟写盘；提交成功后立即停止进程也必须保留刚创建的订单。
        config.setConnectionInitSql("SET WRITE_DELAY 0");
        config.setMaximumPoolSize(8);
        config.setConnectionTimeout(3000);
        HikariDataSource source = new HikariDataSource(config);
        try {
            ResourceDatabasePopulator init = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
            init.setSqlScriptEncoding("UTF-8");
            init.execute(source);
            return source;
        } catch (RuntimeException exception) {
            source.close();
            throw exception;
        }
    }

    @Bean
    public InitializingBean tradeDemoFixtures(JdbcTradeService trades, DataSource dataSource) {
        return () -> {
            ResourceDatabasePopulator init = new ResourceDatabasePopulator(new ClassPathResource("db/trade-demo-data.sql"));
            init.setSqlScriptEncoding("UTF-8");
            init.execute(dataSource);
        };
    }
}
