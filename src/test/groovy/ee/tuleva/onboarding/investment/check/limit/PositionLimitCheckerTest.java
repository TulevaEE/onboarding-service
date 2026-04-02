package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.portfolio.PositionLimit;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionLimitCheckerTest {

  private final PositionLimitChecker checker = new PositionLimitChecker();

  @Test
  void positionWithinLimits() {
    var position = position("IE00B4L5Y983", new BigDecimal("100000"));
    var limit = positionLimit("IE00B4L5Y983", "iShares MSCI World", 15, 20);
    var totalNav = new BigDecimal("1000000");

    var breaches = checker.check(TUK75, List.of(position), totalNav, List.of(limit));

    assertThat(breaches).singleElement().satisfies(b -> assertThat(b.severity()).isEqualTo(OK));
  }

  @Test
  void positionExceedsSoftLimit() {
    var position = position("IE00B4L5Y983", new BigDecimal("160000"));
    var limit = positionLimit("IE00B4L5Y983", "iShares MSCI World", 15, 20);
    var totalNav = new BigDecimal("1000000");

    var breaches = checker.check(TUK75, List.of(position), totalNav, List.of(limit));

    assertThat(breaches)
        .singleElement()
        .satisfies(
            b -> {
              assertThat(b.severity()).isEqualTo(SOFT);
              assertThat(b.actualPercent()).isEqualByComparingTo(new BigDecimal("16"));
              assertThat(b.softLimitPercent()).isEqualByComparingTo(new BigDecimal("15"));
              assertThat(b.hardLimitPercent()).isEqualByComparingTo(new BigDecimal("20"));
              assertThat(b.isin()).isEqualTo("IE00B4L5Y983");
              assertThat(b.label()).isEqualTo("iShares MSCI World");
              assertThat(b.fund()).isEqualTo(TUK75);
            });
  }

  @Test
  void positionExceedsHardLimit() {
    var position = position("IE00B4L5Y983", new BigDecimal("210000"));
    var limit = positionLimit("IE00B4L5Y983", "iShares MSCI World", 15, 20);
    var totalNav = new BigDecimal("1000000");

    var breaches = checker.check(TUK75, List.of(position), totalNav, List.of(limit));

    assertThat(breaches).singleElement().satisfies(b -> assertThat(b.severity()).isEqualTo(HARD));
  }

  @Test
  void noMatchingLimitSkipsPosition() {
    var position = position("IE00UNKNOWN", new BigDecimal("500000"));
    var limit = positionLimit("IE00B4L5Y983", "iShares MSCI World", 15, 20);
    var totalNav = new BigDecimal("1000000");

    var breaches = checker.check(TUK75, List.of(position), totalNav, List.of(limit));

    assertThat(breaches).isEmpty();
  }

  @Test
  void zeroTotalNavReturnsEmpty() {
    var position = position("IE00B4L5Y983", new BigDecimal("100000"));
    var limit = positionLimit("IE00B4L5Y983", "iShares MSCI World", 15, 20);

    var breaches = checker.check(TUK75, List.of(position), BigDecimal.ZERO, List.of(limit));

    assertThat(breaches).isEmpty();
  }

  @Test
  void indexGroupAggregateWithinLimits() {
    var position1 = position("LU0826455353", new BigDecimal("50000"));
    var position2 = position("IE0031080751", new BigDecimal("40000"));
    var totalNav = new BigDecimal("1000000");

    var isinLimit1 =
        PositionLimit.builder()
            .isin("LU0826455353")
            .label("Euro Agg 1")
            .indexGroup("Euro Aggregate indeks")
            .fund(TUK75)
            .softLimitPercent(BigDecimal.valueOf(5))
            .hardLimitPercent(BigDecimal.valueOf(8))
            .build();
    var isinLimit2 =
        PositionLimit.builder()
            .isin("IE0031080751")
            .label("Euro Agg 2")
            .indexGroup("Euro Aggregate indeks")
            .fund(TUK75)
            .softLimitPercent(BigDecimal.valueOf(5))
            .hardLimitPercent(BigDecimal.valueOf(8))
            .build();
    var indexLimit =
        PositionLimit.builder()
            .isin(null)
            .label("Euro Aggregate indeks")
            .indexGroup("Euro Aggregate indeks")
            .fund(TUK75)
            .softLimitPercent(BigDecimal.valueOf(10))
            .hardLimitPercent(BigDecimal.valueOf(15))
            .build();

    var breaches =
        checker.check(
            TUK75,
            List.of(position1, position2),
            totalNav,
            List.of(isinLimit1, isinLimit2, indexLimit));

    var indexBreach =
        breaches.stream().filter(b -> b.isin().equals("Euro Aggregate indeks")).findFirst();
    assertThat(indexBreach).isPresent();
    assertThat(indexBreach.get().severity()).isEqualTo(OK);
    assertThat(indexBreach.get().actualPercent()).isEqualByComparingTo(new BigDecimal("9"));
  }

  @Test
  void indexGroupAggregateExceedsSoftLimit() {
    var position1 = position("LU0826455353", new BigDecimal("60000"));
    var position2 = position("IE0031080751", new BigDecimal("50000"));
    var totalNav = new BigDecimal("1000000");

    var isinLimit1 =
        PositionLimit.builder()
            .isin("LU0826455353")
            .label("Euro Agg 1")
            .indexGroup("Euro Aggregate indeks")
            .fund(TUK75)
            .softLimitPercent(BigDecimal.valueOf(15))
            .hardLimitPercent(BigDecimal.valueOf(20))
            .build();
    var isinLimit2 =
        PositionLimit.builder()
            .isin("IE0031080751")
            .label("Euro Agg 2")
            .indexGroup("Euro Aggregate indeks")
            .fund(TUK75)
            .softLimitPercent(BigDecimal.valueOf(15))
            .hardLimitPercent(BigDecimal.valueOf(20))
            .build();
    var indexLimit =
        PositionLimit.builder()
            .isin(null)
            .label("Euro Aggregate indeks")
            .indexGroup("Euro Aggregate indeks")
            .fund(TUK75)
            .softLimitPercent(BigDecimal.valueOf(10))
            .hardLimitPercent(BigDecimal.valueOf(15))
            .build();

    var breaches =
        checker.check(
            TUK75,
            List.of(position1, position2),
            totalNav,
            List.of(isinLimit1, isinLimit2, indexLimit));

    var indexBreach =
        breaches.stream().filter(b -> b.isin().equals("Euro Aggregate indeks")).findFirst();
    assertThat(indexBreach).isPresent();
    assertThat(indexBreach.get().severity()).isEqualTo(SOFT);
    assertThat(indexBreach.get().actualPercent()).isEqualByComparingTo(new BigDecimal("11"));
  }

  private FundPosition position(String isin, BigDecimal marketValue) {
    return FundPosition.builder().accountId(isin).fund(TUK75).marketValue(marketValue).build();
  }

  private PositionLimit positionLimit(
      String isin, String label, double softPercent, double hardPercent) {
    return PositionLimit.builder()
        .isin(isin)
        .label(label)
        .fund(TUK75)
        .softLimitPercent(BigDecimal.valueOf(softPercent))
        .hardLimitPercent(BigDecimal.valueOf(hardPercent))
        .build();
  }
}
