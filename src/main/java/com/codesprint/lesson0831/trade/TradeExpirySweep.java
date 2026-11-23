package com.codesprint.lesson0831.trade;

import com.codesprint.lesson0831.trade.core.JdbcTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"prod", "trade-demo"})
@ConditionalOnProperty(name = "shopguard.trade.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class TradeExpirySweep {
    private static final Logger LOG = LoggerFactory.getLogger(TradeExpirySweep.class);
    private final JdbcTradeService trades;

    public TradeExpirySweep(JdbcTradeService trades) { this.trades = trades; }

    @Scheduled(fixedDelayString = "${shopguard.trade.expiry-poll-ms:5000}",
            initialDelayString = "${shopguard.trade.expiry-initial-delay-ms:10000}")
    public void expire() {
        try {
            int closed = trades.expireDue(100);
            if (closed > 0) LOG.info("trade_expiry_closed count={}", closed);
        } catch (RuntimeException exception) {
            // 保留故障信号，不输出 SQL、连接字符串或请求数据；下个周期重试数据库中的待处理订单。
            LOG.error("trade_expiry_failed type={}", exception.getClass().getSimpleName());
        }
    }
}
