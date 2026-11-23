package com.codesprint.lesson0831.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.LongFunction;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisException;

/** Cache for public catalogue facts returned by get_product_detail. Checkout reads the database. */
public final class RedisProductDetailCache implements AutoCloseable {
    private static final String MISSING = "missing";
    private static final System.Logger LOG = System.getLogger(RedisProductDetailCache.class.getName());
    private static final String CLAIM = """
            local value = redis.call('GET', KEYS[1])
            if value then return value end
            redis.call('PSETEX', KEYS[1], 3000, ARGV[1])
            return false
            """;
    private static final String FILL = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            end
            return false
            """;
    private final JedisPooled redis;
    private final ObjectMapper json;

    public RedisProductDetailCache(URI uri, ObjectMapper json) {
        var pool = new GenericObjectPoolConfig<Connection>();
        pool.setMaxWait(Duration.ofMillis(150));
        this.redis = new JedisPooled(pool, uri, 250);
        this.json = json;
    }

    public ProductDetail get(long id, LongFunction<ProductDetail> load) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return load.apply(id);
        String key = key(id);
        String ticket = "loading:" + UUID.randomUUID();
        String cached;
        try {
            cached = (String) redis.eval(CLAIM, List.of(key), List.of(ticket));
            if (MISSING.equals(cached)) throw new ProductNotFoundException("商品不存在");
            if (cached != null && !cached.startsWith("loading:")) {
                return json.readValue(cached, ProductDetail.class);
            }
        } catch (JedisException | JsonProcessingException unavailable) {
            return load.apply(id);
        }
        // Another reader owns the fill. This request can still read SQL without overwriting it.
        if (cached != null) return load.apply(id);
        ProductDetail product;
        try {
            product = load.apply(id);
        } catch (ProductNotFoundException missing) {
            fill(key, ticket, MISSING, "5");
            throw missing;
        }
        try {
            fill(key, ticket, json.writeValueAsString(product), "60");
        } catch (JsonProcessingException ignored) {
            // The caller can use the database result; the short-lived ticket expires.
        }
        return product;
    }

    @TransactionalEventListener
    public void afterCommit(ProductCatalogChanged change) {
        try {
            redis.del(key(change.id()));
        } catch (JedisException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "商品 {0} 缓存失效失败，等待 TTL 过期", change.id());
        }
    }

    private static String key(long id) {
        return "shopguard:catalog:detail:v1:" + id;
    }

    private void fill(String key, String ticket, String value, String ttlSeconds) {
        try {
            redis.eval(FILL, List.of(key), List.of(ticket, value, ttlSeconds));
        } catch (JedisException ignored) {
            // Redis is optional; never turn a successful database read into a failed tool call.
        }
    }

    @Override
    public void close() { redis.close(); }
}
