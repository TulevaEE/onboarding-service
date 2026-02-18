package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  @Setter private BigDecimal preliminaryNav;
  @Setter private BigDecimal preliminaryNavPerUnit;
  @Setter private BigDecimal unitsOutstanding;
}
