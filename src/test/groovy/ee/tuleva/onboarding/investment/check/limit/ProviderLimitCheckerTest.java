package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static ee.tuleva.onboarding.investment.portfolio.Provider.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.investment.portfolio.Provider;
import ee.tuleva.onboarding.investment.portfolio.ProviderLimit;
import ee.tuleva.onboarding.investment.position.FundPosition;
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
  void twoIssuersSharingOneManagerAreCheckedSeparately() {
    var positions =
        List.of(
            position("IE00BJZ2DC62", new BigDecimal("1900000")),
            position("LU0476289540", new BigDecimal("200000")));
    var isinToProvider = Map.of("IE00BJZ2DC62", XTRACKERS_IE, "LU0476289540", XTRACKERS_LU);
    var limits =
        List.of(providerLimit(XTRACKERS_IE, 19.65, 20), providerLimit(XTRACKERS_LU, 19.65, 20));
    var totalNav = new BigDecimal("10000000");

    var breaches = checker.check(TKF100, positions, totalNav, isinToProvider, limits);

    assertThat(breaches)
        .extracting(
            ProviderBreach::provider, ProviderBreach::actualPercent, ProviderBreach::severity)
        .containsExactlyInAnyOrder(
            tuple(XTRACKERS_IE, new BigDecimal("19.0000"), OK),
            tuple(XTRACKERS_LU, new BigDecimal("2.0000"), OK));
  }

  @Test
  void oneIssuerCarryingBothSleevesBreachesTheSameLimit() {
    var positions =
        List.of(
            position("IE00BJZ2DC62", new BigDecimal("1900000")),
            position("LU0476289540", new BigDecimal("200000")));
    var isinToProvider = Map.of("IE00BJZ2DC62", XTRACKERS, "LU0476289540", XTRACKERS);
    var limits = List.of(providerLimit(XTRACKERS, 19.65, 20));
    var totalNav = new BigDecimal("10000000");

    var breaches = checker.check(TKF100, positions, totalNav, isinToProvider, limits);

    assertThat(breaches)
        .singleElement()
        .satisfies(
            b -> {
              assertThat(b.actualPercent()).isEqualByComparingTo(new BigDecimal("21"));
              assertThat(b.severity()).isEqualTo(HARD);
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

  private FundPosition position(String isin, BigDecimal marketValue) {
    return FundPosition.builder().accountId(isin).fund(TUK75).marketValue(marketValue).build();
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
