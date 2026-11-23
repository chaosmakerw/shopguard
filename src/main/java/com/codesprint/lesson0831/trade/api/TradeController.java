package com.codesprint.lesson0831.trade.api;

import com.codesprint.lesson0831.security.CurrentUserContext;
import com.codesprint.lesson0831.trade.core.JdbcTradeService;
import com.codesprint.lesson0831.trade.core.TradeException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

@RestController
@RequestMapping("/api/trade")
@Profile({"prod", "trade-demo"})
public class TradeController {
    private final JdbcTradeService trades;
    private final CurrentUserContext identity;
    private final Environment environment;
    public TradeController(JdbcTradeService trades, CurrentUserContext identity, Environment environment) {
        this.trades = trades; this.identity = identity; this.environment = environment;
    }
    private long user() {
        Long id = identity.getUserId();
        if (id == null || id <= 0) throw new TradeException("UNAUTHORIZED", 401, "请先登录");
        return id;
    }
    public record Context(String mode, String userId, boolean simulatePaymentEnabled, long paymentWindowSeconds) { }
    @GetMapping("/context")
    public Context context() {
        boolean demo = environment.acceptsProfiles(Profiles.of("trade-demo"));
        boolean platform = environment.acceptsProfiles(Profiles.of("platform"));
        boolean simulation = platform && environment.acceptsProfiles(Profiles.of("platform-simulation"));
        return new Context(demo ? "LOCAL_DEMO" : platform ? "SESSION" : "JWT", Long.toString(user()), demo || simulation,
                environment.getProperty("shopguard.trade.payment-window-seconds", Long.class, 900L));
    }
    @GetMapping("/products")
    public TradeViews.Page<TradeViews.Product> products(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        user();
        return new TradeViews.Page<>(trades.products(keyword, page, size).stream().map(TradeViews.Product::of).toList(), page, size);
    }
    @GetMapping("/products/{id}")
    public TradeViews.Product product(@PathVariable String id) { user(); return TradeViews.Product.of(trades.product(TradeInputs.id(id))); }
    @PostMapping("/orders")
    public TradeViews.Order create(@RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody JsonNode body) {
        long buyer = user();
        TradeInputs.object(body, Set.of("productId", "quantity"), true);
        return TradeViews.Order.of(trades.create(buyer, TradeInputs.productId(body.get("productId")), TradeInputs.quantity(body.get("quantity")), key));
    }
    @GetMapping("/orders")
    public TradeViews.Page<TradeViews.Order> orders(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return new TradeViews.Page<>(trades.orders(user(), page, size).stream().map(TradeViews.Order::of).toList(), page, size);
    }
    @GetMapping("/orders/{id}")
    public TradeViews.Order order(@PathVariable String id) { return TradeViews.Order.of(trades.order(user(), TradeInputs.id(id))); }
    @PostMapping("/orders/{id}/cancel")
    public TradeViews.Order cancel(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        long buyer = user(); TradeInputs.object(body, Set.of(), false);
        return TradeViews.Order.of(trades.cancel(buyer, TradeInputs.id(id)));
    }
}
