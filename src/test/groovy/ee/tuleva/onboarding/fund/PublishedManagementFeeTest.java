package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund;
import static ee.tuleva.onboarding.fund.FundFixture.lhv2ndPillarFund;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublishedManagementFeeTest {

  private static final Clock OCTOBER_6_MIDDAY = clockAt("2026-10-06T10:00:00Z");

  @Test
  void aTulevaFundShowsTheRateInForceTodayWithoutTheTablesTrailingZeros() {
    var rates = ratesOn(Map.of(LocalDate.parse("2026-10-06"), new BigDecimal("0.00205000")), TUK75);

    var response =
        new PublishedManagementFee(rates, OCTOBER_6_MIDDAY)
            .applyTo(responseFor(tuleva2ndPillarStockFund()));

    assertThat(response)
        .isEqualTo(responseWithRate(tuleva2ndPillarStockFund(), new BigDecimal("0.00205")));
  }

  @Test
  void theRateInForceChangesAtMidnightInTallinnNotInUtc() {
    var rates =
        ratesOn(
            Map.of(
                LocalDate.parse("2026-10-28"), new BigDecimal("0.00152000"),
                LocalDate.parse("2026-10-29"), new BigDecimal("0.00178000")),
            TKF100);
    var halfPastMidnightInTallinnOnOctober29 = clockAt("2026-10-28T22:30:00Z");

    var response =
        new PublishedManagementFee(rates, halfPastMidnightInTallinnOnOctober29)
            .applyTo(responseFor(additionalSavingsFund()));

    assertThat(response)
        .isEqualTo(responseWithRate(additionalSavingsFund(), new BigDecimal("0.00178")));
  }

  @Test
  void anotherManagersFundKeepsTheFundTableRateEvenWhenEveryFundHasARate() {
    ManagementFeeRates aRateForEveryFund = (fund, date) -> Optional.of(new BigDecimal("0.001"));

    var response =
        new PublishedManagementFee(aRateForEveryFund, OCTOBER_6_MIDDAY)
            .applyTo(responseFor(lhv2ndPillarFund()));

    assertThat(response).isEqualTo(responseFor(lhv2ndPillarFund()));
  }

  @Test
  void aTulevaFundWithNoRateInForceKeepsTheFundTableRate() {
    ManagementFeeRates noRateInForce = (fund, date) -> Optional.empty();

    var response =
        new PublishedManagementFee(noRateInForce, OCTOBER_6_MIDDAY)
            .applyTo(responseFor(tuleva2ndPillarStockFund()));

    assertThat(response).isEqualTo(responseFor(tuleva2ndPillarStockFund()));
  }

  private static ApiFundResponse responseFor(Fund fund) {
    return new ApiFundResponse(fund, Locale.ENGLISH);
  }

  private static ApiFundResponse responseWithRate(Fund fund, BigDecimal managementFeeRate) {
    var response = responseFor(fund);
    response.setManagementFeeRate(managementFeeRate);
    return response;
  }

  private static ManagementFeeRates ratesOn(
      Map<LocalDate, BigDecimal> ratesByDate, TulevaFund forFund) {
    return (fund, date) ->
        fund == forFund ? Optional.ofNullable(ratesByDate.get(date)) : Optional.empty();
  }

  private static Clock clockAt(String instant) {
    return Clock.fixed(Instant.parse(instant), UTC);
  }
}
