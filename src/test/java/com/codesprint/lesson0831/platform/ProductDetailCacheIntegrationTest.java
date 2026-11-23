package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.platform.commerce.CommerceService;
import com.codesprint.lesson0831.platform.commerce.CommerceService.ProductInput;
import com.codesprint.lesson0831.product.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "SHOPGUARD_DB_URL=jdbc:h2:mem:catalog-cache;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "SHOPGUARD_ADMIN_PASSWORD=catalog-fixture-admin", "SHOPGUARD_STUDENT_PASSWORD=catalog-fixture-user",
        "GATEWAY_API_KEY=catalog-test-key", "MODEL_MODE=stub", "platform.vector-store=local"})
@ActiveProfiles({"prod","platform","gateway","platform-simulation"})
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "SHOPGUARD_TEST_REDIS_URL", matches = ".+")
class ProductDetailCacheIntegrationTest {
    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry properties) {
        properties.add("shopguard.catalog.redis.url", () -> System.getenv("SHOPGUARD_TEST_REDIS_URL"));
    }

    @Autowired DataSource database;
    @Autowired ObjectMapper json;
    @Autowired RedisProductDetailCache cache;
    @Autowired CommerceService catalog;
    @Autowired ProductTools tools;
    @Autowired TransactionTemplate transactions;
    private final PlatformIdentity admin = new PlatformIdentity(1001, "admin", "管理员", "ADMIN");

    private CommerceService.Product create() {
        return catalog.createProduct(admin, input("初版商品", true, 0), 3, UUID.randomUUID().toString());
    }

    private ProductInput input(String title, boolean listed, int version) {
        return new ProductInput(title, "公开商品资料", new BigDecimal("10.00"), "其他", listed, version);
    }

    @Test void comparesOneThousandReadsWithAndWithoutCacheForTwoClients() {
        long id = Long.parseLong(create().productId());
        AtomicInteger selects = new AtomicInteger();
        var counted = new DelegatingDataSource(database) {
            @Override public Connection getConnection() throws SQLException {
                selects.incrementAndGet();
                return super.getConnection();
            }
        };
        var direct = new ProductTools(new JdbcProductService(counted));
        for (int i = 0; i < 1000; i++) assertEquals(id, direct.getProductDetail(id).data().id());
        assertEquals(1000, selects.getAndSet(0));
        try (var second = new RedisProductDetailCache(URI.create(System.getenv("SHOPGUARD_TEST_REDIS_URL")), json)) {
            var clients = new ProductTools[] {new ProductTools(new JdbcProductService(counted, cache)),
                    new ProductTools(new JdbcProductService(counted, second))};
            for (int i = 0; i < 1000; i++) {
                assertEquals(id, clients[i % 2].getProductDetail(id).data().id());
            }
            assertEquals(1, selects.getAndSet(0));
            catalog.updateProduct(admin, id, input("已下架", false, 0));
            for (int i = 0; i < 1000; i++) {
                assertEquals("NOT_FOUND", clients[i % 2].getProductDetail(id).code());
            }
            assertEquals(1, selects.get());
            System.out.println("catalog read comparison: 1000 visible reads, JDBC selects 1000 -> 1; 1000 hidden reads, JDBC selects 1");
        }
    }

    @Test void rollbackAndRelistRespectTheTransactionBoundary() {
        long id = Long.parseLong(create().productId());
        assertEquals("初版商品", tools.getProductDetail(id).data().title());
        transactions.executeWithoutResult(status -> {
            catalog.updateProduct(admin, id, input("已下架", false, 0));
            assertEquals("NOT_FOUND", tools.getProductDetail(id).code());
            status.setRollbackOnly();
        });
        assertEquals("初版商品", tools.getProductDetail(id).data().title());
        var hidden = catalog.updateProduct(admin, id, input("已下架", false, 0));
        assertEquals("NOT_FOUND", tools.getProductDetail(id).code());
        catalog.updateProduct(admin, id, input("重新上架", true, hidden.version()));
        assertEquals("重新上架", tools.getProductDetail(id).data().title());
    }

    @Test void slowReaderCannotRestoreProductAfterDelisting() throws Exception {
        long id = Long.parseLong(create().productId());
        var loaded = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var oldRead = worker.submit(() -> cache.get(id, key -> {
                ProductDetail previous = new JdbcProductService(database).getDetail(key);
                loaded.countDown();
                try {
                    if (!resume.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("reader timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return previous;
            }));
            assertTrue(loaded.await(5, TimeUnit.SECONDS));
            catalog.updateProduct(admin, id, input("已下架", false, 0));
            resume.countDown();
            assertEquals("初版商品", oldRead.get(5, TimeUnit.SECONDS).title());
            assertEquals("NOT_FOUND", tools.getProductDetail(id).code());
        } finally {
            resume.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void redisTimeoutStillReturnsJdbcResult() throws Exception {
        long id = Long.parseLong(create().productId());
        try (var unresponsive = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
             var failedCache = new RedisProductDetailCache(URI.create("redis://127.0.0.1:" + unresponsive.getLocalPort()), json)) {
            assertEquals(id, new JdbcProductService(database, failedCache).getDetail(id).id());
        }
    }
}
