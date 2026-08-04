package ee.tuleva.onboarding.account.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record Portfolio(
    LocalDate from, LocalDate to, List<GroupSummary> groups, List<ValuePoint> series) {

  @Builder
  public record GroupSummary(
      PortfolioGroup group,
      BigDecimal startValue,
      BigDecimal endValue,
      BigDecimal contributions,
      BigDecimal withdrawals,
      BigDecimal gain,
      BigDecimal gainPercentage,
      @Nullable BigDecimal annualReturnRate) {}

  public record ValuePoint(LocalDate date, Map<PortfolioGroup, @Nullable BigDecimal> values) {}
}
