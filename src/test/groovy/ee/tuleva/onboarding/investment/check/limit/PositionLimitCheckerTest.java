package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
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
  void aPositionExactlyAtItsHardLimitIsSoftBecauseOnlyExceedingTheHardLimitIsHard() {
    var position = position("IE00B4L5Y983", new BigDecimal("200000"));
    var limit = positionLimit("IE00B4L5Y983", "iShares MSCI World", 15, 20);

    var breaches =
        checker.check(TUK75, List.of(position), new BigDecimal("1000000"), List.of(limit));

    assertThat(breaches).singleElement().satisfies(b -> assertThat(b.severity()).isEqualTo(SOFT));
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
  void aLimitRowWithoutAnIsinMeasuresNothingAndTheIsinLimitsStillApply() {
    var position = position("IE00B4L5Y983", new BigDecimal("100000"));
    var isinLimit = positionLimit("IE00B4L5Y983", "iShares MSCI World", 15, 20);
    var rowWithoutIsin =
        PositionLimit.builder()
            .label("Aggregate")
            .fund(TUK75)
            .softLimitPercent(BigDecimal.ONE)
            .hardLimitPercent(BigDecimal.ONE)
            .build();

    var breaches =
        checker.check(
            TUK75,
            List.of(position),
            new BigDecimal("1000000"),
            List.of(isinLimit, rowWithoutIsin));

    assertThat(breaches)
        .singleElement()
        .satisfies(b -> assertThat(b.isin()).isEqualTo("IE00B4L5Y983"));
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
