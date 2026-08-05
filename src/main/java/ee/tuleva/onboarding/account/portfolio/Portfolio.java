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

  @Builder(toBuilder = true)
  public record GroupSummary(
      PortfolioGroup group,
      @Nullable BigDecimal startValue,
      @Nullable BigDecimal endValue,
      BigDecimal contributions,
      BigDecimal withdrawals,
      @Nullable BigDecimal gain,
      @Nullable BigDecimal gainPercentage,
      @Nullable BigDecimal annualReturnRate) {}

  public record ValuePoint(LocalDate date, Map<PortfolioGroup, @Nullable BigDecimal> values) {}
}
