package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_PENSION;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.ONE_OFF_WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.R16TypeForecast.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CashSectionTest {

  private static final CashSourceReport REPORT_CASH =
      new CashSourceReport(
          CashSource.POSITION_REPORT,
          new GatewayCash.NotTodaysRun(),
          new BigDecimal("1000000.00"),
          List.of());

  @Test
  void showsEachR16TypeWithItsBookingStatusBesideTheReservesAndTheAmountReserved() {
    var section =
        new CashSection(
            REPORT_CASH,
            List.of(),
            new R16Reserve(
                R16Reserve.Basis.FORECAST_NOT_YET_BOOKED,
                List.of(
                    new R16TypeForecast(
                        FUND_PENSION,
                        new BigDecimal("300000"),
                        new BigDecimal("600000"),
                        Status.BOOKED),
                    new R16TypeForecast(
                        ONE_OFF_WITHDRAWAL,
                        new BigDecimal("100000"),
                        new BigDecimal("200000"),
                        Status.NOT_BOOKED)),
                new BigDecimal("1000000"),
                new BigDecimal("500000"),
                new BigDecimal("0")));

    assertThat(section.payload().get("r16"))
        .isEqualTo(
            Map.of(
                "basis",
                "FORECAST_NOT_YET_BOOKED",
                "types",
                List.of(
                    Map.of(
                        "type", "FUND_PENSION",
                        "units", "300000",
                        "eur", "600000",
                        "status", "BOOKED"),
                    Map.of(
                        "type", "ONE_OFF_WITHDRAWAL",
                        "units", "100000",
                        "eur", "200000",
                        "status", "NOT_BOOKED")),
                "reserveSoft",
                "1000000",
                "reserveHard",
                "500000",
                "reserved",
                "0"));
  }

  @Test
  void aFundWithoutAnR16ThisMonthShowsNoR16AndAForecastThatIsZeroBothSidesIsLeftOut() {
    var section =
        new CashSection(
            REPORT_CASH,
            List.of(NettedForecast.notNetted("incomingPaymentsClearing", BigDecimal.ZERO)),
            R16Reserve.NONE);

    assertThat(section.payload()).doesNotContainKeys("r16", "forecasts");
  }
}
