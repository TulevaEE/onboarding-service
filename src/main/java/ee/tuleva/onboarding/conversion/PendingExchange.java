package ee.tuleva.onboarding.conversion;

import java.math.BigDecimal;

public interface PendingExchange {

  Integer getPillar();

  boolean isFromOwnFund();

  boolean isToOwnFund();

  boolean isFullAmount();

  boolean isFullAmount(BigDecimal fundBalanceUnits);

  String getSourceIsin();

  String getTargetIsin();

  BigDecimal getSourceFundFees();

  BigDecimal getTargetFundFees();

  BigDecimal getValue(BigDecimal totalValue, BigDecimal totalUnits);

  boolean isToPik();
}
