package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;

record UnsettledTrades(BigDecimal payables, BigDecimal receivables) {}
