package ee.tuleva.onboarding.investment.epis;

import java.math.BigDecimal;

public record PevaRavaFlows(
    BigDecimal pikEur,
    BigDecimal switchingNetEur,
    BigDecimal ravaEur,
    BigDecimal liquidityRequired,
    BigDecimal grossOut,
    BigDecimal paymentLimit,
    BigDecimal tradeBufferedLiquidity) {}
