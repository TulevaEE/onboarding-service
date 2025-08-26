package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import java.math.BigDecimal;

public record CapitalTotalDto(BigDecimal unitAmount, BigDecimal totalValue, BigDecimal unitPrice) {

  public static CapitalTotalDto from(AggregatedCapitalEvent aggregatedCapitalEvent) {
    return new CapitalTotalDto(
        aggregatedCapitalEvent.getTotalOwnershipUnitAmount(),
        aggregatedCapitalEvent.getTotalFiatValue(),
        aggregatedCapitalEvent.getOwnershipUnitPrice());
  }
}
