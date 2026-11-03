package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;

public record TradeSettlementDetails(
    String brokerReference, String isin, String ticker, String displayName, BigDecimal price) {}
