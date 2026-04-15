package com.payments.frauddetection.service;

import com.payments.frauddetection.config.FraudRuleProperties;
import com.payments.frauddetection.model.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FraudCheckService {

    private static final Logger log = LoggerFactory.getLogger(FraudCheckService.class);
    private static final BigDecimal ROUND_AMOUNT_FACTOR = BigDecimal.valueOf(10000);

    private final FraudRuleProperties fraudRuleProperties;
    private final Map<String, List<Long>> senderPaymentTimestamps = new ConcurrentHashMap<>();

    public FraudCheckService(FraudRuleProperties fraudRuleProperties) {
        this.fraudRuleProperties = fraudRuleProperties;
    }

    public String checkFraud(PaymentEvent event) {
        long timestamp = resolveTimestamp(event);
        int velocityHits = updateVelocityWindow(event.getSender(), timestamp);

        if (event.getAmount() > fraudRuleProperties.getAmountThreshold()) {
            log.warn("Payment flagged: paymentId={}, rule=HIGH_AMOUNT, details=\"amount={} exceeds threshold {}\"",
                    event.getPaymentId(), event.getAmount(), fraudRuleProperties.getAmountThreshold());
            return "FLAGGED";
        }

        if (velocityHits >= fraudRuleProperties.getVelocityCount()) {
            log.warn("Payment flagged: paymentId={}, rule=VELOCITY, details=\"{} payments from sender '{}' in {}s\"",
                    event.getPaymentId(), velocityHits, event.getSender(), fraudRuleProperties.getVelocityWindowSeconds());
            return "FLAGGED";
        }

        if (fraudRuleProperties.isSelfTransferEnabled() && isSelfTransfer(event)) {
            log.warn("Payment flagged: paymentId={}, rule=SELF_TRANSFER, details=\"sender '{}' matches receiver '{}'\"",
                    event.getPaymentId(), event.getSender(), event.getReceiver());
            return "FLAGGED";
        }

        if (isSuspiciousRoundAmount(event.getAmount())) {
            log.warn("Payment flagged: paymentId={}, rule=ROUND_AMOUNT, details=\"amount={} is a round figure above minimum {}\"",
                    event.getPaymentId(), event.getAmount(), fraudRuleProperties.getRoundAmountMinimum());
            return "FLAGGED";
        }

        log.info("Payment approved: paymentId={}, amount={}, rulesChecked={}",
                event.getPaymentId(), event.getAmount(), countEnabledRules());
        return "APPROVED";
    }

    private long resolveTimestamp(PaymentEvent event) {
        return event.getTimestamp() > 0 ? event.getTimestamp() : System.currentTimeMillis();
    }

    private int updateVelocityWindow(String sender, long timestamp) {
        if (sender == null || sender.isBlank() || fraudRuleProperties.getVelocityCount() <= 0) {
            return 0;
        }

        String senderKey = sender.trim().toLowerCase(Locale.ROOT);
        List<Long> timestamps = senderPaymentTimestamps.computeIfAbsent(
                senderKey, ignored -> Collections.synchronizedList(new ArrayList<>())
        );
        long windowStart = timestamp - (fraudRuleProperties.getVelocityWindowSeconds() * 1000);

        synchronized (timestamps) {
            timestamps.removeIf(entry -> entry < windowStart);
            timestamps.add(timestamp);
            return timestamps.size();
        }
    }

    private boolean isSelfTransfer(PaymentEvent event) {
        return event.getSender() != null
                && event.getReceiver() != null
                && event.getSender().equalsIgnoreCase(event.getReceiver());
    }

    private boolean isSuspiciousRoundAmount(double amount) {
        if (amount <= fraudRuleProperties.getRoundAmountMinimum()) {
            return false;
        }

        return BigDecimal.valueOf(amount).remainder(ROUND_AMOUNT_FACTOR).compareTo(BigDecimal.ZERO) == 0;
    }

    private int countEnabledRules() {
        return fraudRuleProperties.isSelfTransferEnabled() ? 4 : 3;
    }
}
