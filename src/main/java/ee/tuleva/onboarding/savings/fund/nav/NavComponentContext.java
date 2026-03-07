package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
public class NavComponentContext {
  private final TulevaFund fund;
  private final LocalDate calculationDate;
  private final LocalDate positionReportDate;
  private final LocalDate priceDate;
  private final Instant cutoff;

  @Setter private BigDecimal unitsOutstanding;
  @Setter private Map<String, ResolvedPrice> securityPrices;
}
