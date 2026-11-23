package com.codesprint.lesson0831.trade.api;

import com.codesprint.lesson0831.security.CurrentUserContext;
import com.codesprint.lesson0831.trade.core.JdbcTradeService;
import com.codesprint.lesson0831.trade.core.TradeException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

/** 仅用于明确启用的交易模拟模式；没有真实资金或支付回调。 */
@RestController
@Profile("(trade-demo & !prod) | (platform & platform-simulation)")
@RequestMapping("/api/trade/orders")
public class SimulatedPaymentController {
    private final JdbcTradeService trades;
    private final CurrentUserContext identity;
    public SimulatedPaymentController(JdbcTradeService trades, CurrentUserContext identity) { this.trades = trades; this.identity = identity; }
    @PostMapping("/{id}/simulate-payment")
    public TradeViews.Order simulate(@PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody(required = false) JsonNode body) {
        Long user = identity.getUserId();
        if (user == null || user <= 0) throw new TradeException("UNAUTHORIZED", 401, "请先登录");
        TradeInputs.object(body, Set.of(), false);
        return TradeViews.Order.of(trades.simulatePayment(user, TradeInputs.id(id), key));
    }
}
