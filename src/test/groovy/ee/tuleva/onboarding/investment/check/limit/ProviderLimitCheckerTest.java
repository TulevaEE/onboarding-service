package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static ee.tuleva.onboarding.investment.portfolio.Provider.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.calculation.InvestmentPositionCalculation;
import ee.tuleva.onboarding.investment.portfolio.Provider;
import ee.tuleva.onboarding.investment.portfolio.ProviderLimit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderLimitCheckerTest {

  private final ProviderLimitChecker checker = new ProviderLimitChecker();

  @Test
  void providerWithinLimits() {
    var positions = List.of(position("IE001", new BigDecimal("100000")));
    var isinToProvider = Map.of("IE001", ISHARES);
    var limits = List.of(providerLimit(ISHARES, 15, 20));
    var totalNav = new BigDecimal("1000000");

    var breaches = checker.check(TUK75, positions, totalNav, isinToProvider, limits);

    assertThat(breaches).singleElement().satisfies(b -> assertThat(b.severity()).isEqualTo(OK));
  }

  @Test
  void providerExceedsSoftLimit() {
    var positions = List.of(position("IE001", new BigDecimal("160000")));
    var isinToProvider = Map.of("IE001", ISHARES);
    var limits = List.of(providerLimit(ISHARES, 15, 20));
    var totalNav = new BigDecimal("1000000");

    var breaches = checker.check(TUK75, positions, totalNav, isinToProvider, limits);

    assertThat(breaches)
        .singleElement()
        .satisfies(
            b -> {
              assertThat(b.severity()).isEqualTo(SOFT);
              assertThat(b.provider()).isEqualTo(ISHARES);
              assertThat(b.actualPercent()).isEqualByComparingTo(new BigDecimal("16"));
            });
  }

  @Test
  void providerExceedsHardLimit() {
    var positions = List.of(position("IE001", new BigDecimal("210000")));
    var isinToProvider = Map.of("IE001", ISHARES);
    var limits = List.of(providerLimit(ISHARES, 15, 20));
    var totalNav = new BigDecimal("1000000");

    var breaches = checker.check(TUK75, positions, totalNav, isinToProvider, limits);

    assertThat(breaches).singleElement().satisfies(b -> assertThat(b.severity()).isEqualTo(HARD));
  }

  @Test
  void aggregatesMultipleIsinsByProvider() {
    var positions =
        List.of(
            position("IE001", new BigDecimal("80000")), position("IE002", new BigDecimal("90000")));
    var isinToProvider = Map.of("IE001", ISHARES, "IE002", ISHARES);
    var limits = List.of(providerLimit(ISHARES, 15, 20));
    var totalNav = new BigDecimal("1000000");

    var breaches = checker.check(TUK75, positions, totalNav, isinToProvider, limits);

    assertThat(breaches)
        .singleElement()
        .satisfies(
            b -> {
              assertThat(b.severity()).isEqualTo(SOFT);
              assertThat(b.actualPercent()).isEqualByComparingTo(new BigDecimal("17"));
            });
  }

  @Test
  void zeroTotalNavReturnsEmpty() {
    var positions = List.of(position("IE001", new BigDecimal("100000")));
    var isinToProvider = Map.of("IE001", ISHARES);
    var limits = List.of(providerLimit(ISHARES, 15, 20));

    var breaches = checker.check(TUK75, positions, BigDecimal.ZERO, isinToProvider, limits);

    assertThat(breaches).isEmpty();
  }

  private InvestmentPositionCalculation position(String isin, BigDecimal marketValue) {
    return InvestmentPositionCalculation.builder()
        .isin(isin)
        .fund(TUK75)
        .calculatedMarketValue(marketValue)
        .build();
  }

  private ProviderLimit providerLimit(Provider provider, double softPercent, double hardPercent) {
    return ProviderLimit.builder()
        .provider(provider)
        .fund(TUK75)
        .softLimitPercent(BigDecimal.valueOf(softPercent))
        .hardLimitPercent(BigDecimal.valueOf(hardPercent))
        .build();
  }
}
