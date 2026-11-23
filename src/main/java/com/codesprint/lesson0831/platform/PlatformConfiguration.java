package com.codesprint.lesson0831.platform;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods=false)
@Profile("platform")
public class PlatformConfiguration {
    @Bean public ApplicationRunner initializePlatform(PlatformRepository repository,PasswordEncoder encoder,Environment environment,DataSource dataSource) {
        return args -> {
            if(repository.account("admin").isEmpty()) repository.seedAccount(1001,"admin","管理员","ADMIN",
                encoder.encode(password(environment,"platform.admin-password")));
            if(repository.account("student").isEmpty()) repository.seedAccount(1002,"student","体验用户","USER",
                encoder.encode(password(environment,"platform.student-password")));
            new ResourceDatabasePopulator(new ClassPathResource("db/trade-demo-data.sql")).execute(dataSource);
            new org.springframework.jdbc.core.JdbcTemplate(dataSource).update("""
                INSERT INTO commerce_product_profiles (product_id,category,version,updated_at_ms)
                SELECT p.id,CASE WHEN p.title LIKE '%灯%' THEN '生活' WHEN p.title LIKE '%Java%' OR p.title LIKE '%数据库%' THEN '书籍' ELSE '其他' END,0,?
                FROM products p WHERE NOT EXISTS (SELECT 1 FROM commerce_product_profiles c WHERE c.product_id=p.id)
                """,System.currentTimeMillis());
        };
    }
    private String password(Environment environment,String property) {
        String value=environment.getProperty(property,"");
        if(value.length()<12 || value.length()>72) throw new IllegalStateException(property+" 必须为 12 至 72 个字符；首次启动由本地启动器生成");
        return value;
    }
}
