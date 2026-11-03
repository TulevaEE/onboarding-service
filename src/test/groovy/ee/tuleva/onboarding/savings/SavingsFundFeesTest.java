package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.OngoingChargesFigures;
import ee.tuleva.onboarding.fund.PublishedFigure;
import ee.tuleva.onboarding.fund.PublishedOngoingChargesFigure;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundFeesTest {

  private static final String SAVINGS_FUND_ISIN = "EE0000003283";
  private static final Clock NOVEMBER_2_MIDDAY =
      Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), UTC);
  private static final OngoingChargesFigures NO_FIGURE_IN_FORCE = (fund, date) -> Optional.empty();

  @Mock private FundRepository fundRepository;

  @Test
  void exposesThePercentItsKidInForcePrintsForTheNudgeDecision() {
    givenTheFundTableFigure("0.0052");

    assertThat(feesWith(kidInForce("0.0046", 2)).ongoingChargesPercent())
        .isEqualTo(new BigDecimal("0.46"));
  }

  @Test
  void aTwoDecimalKidPrintingAWholeTenthOfAPercentKeepsItsSecondDecimalInEstonian() {
    givenTheFundTableFigure("0.0052");

    assertThat(feesWith(kidInForce("0.0050", 2)).ongoingChargesPercent(Locale.of("et")))
        .isEqualTo("0,50");
  }

  @Test
  void aTwoDecimalKidPrintingAWholeTenthOfAPercentKeepsItsSecondDecimalInEnglish() {
    givenTheFundTableFigure("0.0052");

    assertThat(feesWith(kidInForce("0.0050", 2)).ongoingChargesPercent(Locale.ENGLISH))
        .isEqualTo("0.50");
  }

  @Test
  void aOneDecimalKidPrintsThePercentToOneDecimal() {
    givenTheFundTableFigure("0.0052");

    assertThat(feesWith(kidInForce("0.005", 1)).ongoingChargesPercent(Locale.of("et")))
        .isEqualTo("0,5");
  }

  @Test
  void formatsThePercentWithADecimalCommaInEstonian() {
    givenTheFundTableFigure("0.0052");

    assertThat(feesWith(kidInForce("0.0046", 2)).ongoingChargesPercent(Locale.of("et")))
        .isEqualTo("0,46");
  }

  @Test
  void formatsThePercentWithADecimalPointInEnglish() {
    givenTheFundTableFigure("0.0052");

    assertThat(feesWith(kidInForce("0.0046", 2)).ongoingChargesPercent(Locale.ENGLISH))
        .isEqualTo("0.46");
  }

  @Test
  void fallsBackToTheFundTableFigureWhenNoKidIsInForce() {
    givenTheFundTableFigure("0.0052");

    assertThat(feesWith(NO_FIGURE_IN_FORCE).ongoingChargesPercent(Locale.ENGLISH))
        .isEqualTo("0.52");
  }

  @Test
  void theFundTableFigureLosesItsTrailingZerosWhenNoKidIsInForce() {
    givenTheFundTableFigure("0.00500");

    assertThat(feesWith(NO_FIGURE_IN_FORCE).ongoingChargesPercent(Locale.of("et")))
        .isEqualTo("0,5");
  }

  @Test
  void aSavingsFundMissingFromTheFundTableIsAnError() {
    var fees = feesWith(kidInForce("0.0046", 2));

    assertThatThrownBy(fees::ongoingChargesPercent).isInstanceOf(IllegalStateException.class);
  }

  private void givenTheFundTableFigure(String ongoingChargesFigure) {
    given(fundRepository.findByIsin(SAVINGS_FUND_ISIN))
        .willReturn(
            Fund.builder()
                .isin(SAVINGS_FUND_ISIN)
                .ongoingChargesFigure(new BigDecimal(ongoingChargesFigure))
                .build());
  }

  private SavingsFundFees feesWith(OngoingChargesFigures figures) {
    return new SavingsFundFees(
        fundRepository,
        new SavingsFundConfiguration(),
        new PublishedOngoingChargesFigure(figures, NOVEMBER_2_MIDDAY));
  }

  private static OngoingChargesFigures kidInForce(String figure, int decimals) {
    return (fund, date) ->
        fund == TKF100
            ? Optional.of(new PublishedFigure(new BigDecimal(figure), decimals))
            : Optional.empty();
  }
}
