package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund;
import static ee.tuleva.onboarding.fund.FundFixture.lhv2ndPillarFund;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarBondFund;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundManagerUnitsTest {

  private static final String REGISTRY_CODE = "10000000";
  private static final LocalDate END_OF_SEPTEMBER = LocalDate.parse("2026-09-30");
  private static final Instant START_OF_OCTOBER_IN_TALLINN = Instant.parse("2026-09-30T21:00:00Z");
  private static final Clock OCTOBER_6 = clockAt("2026-10-06T10:00:00Z");

  @Mock private SavingsFundUnitStats savingsFundUnitStats;
  @Mock private FundManagerUnitsInRegister unitsInRegister;

  @Test
  void theSavingsFundsUnitsAreTheManagersLedgerHoldingAtTheStartOfTheNextMonthInTallinn() {
    given(savingsFundUnitStats.unitsHeldAt(REGISTRY_CODE, START_OF_OCTOBER_IN_TALLINN))
        .willReturn(Optional.of(new BigDecimal("202901.49")));

    var responses = fundManagerUnits(OCTOBER_6).applyTo(responsesFor(additionalSavingsFund()));

    assertThat(responses)
        .containsExactly(responseWithUnits(additionalSavingsFund(), new BigDecimal("202901.49")));
  }

  @Test
  void theSavingsFundShowsNoUnitsWhenTheManagerHasNoUnitAccounts() {
    given(savingsFundUnitStats.unitsHeldAt(REGISTRY_CODE, START_OF_OCTOBER_IN_TALLINN))
        .willReturn(Optional.empty());

    var responses = fundManagerUnits(OCTOBER_6).applyTo(responsesFor(additionalSavingsFund()));

    assertThat(responses).containsExactly(responseFor(additionalSavingsFund()));
  }

  @Test
  void aPensionFundsUnitsAreTheRegistersFundManagerCountOnTheMonthEnd() {
    given(
            unitsInRegister.fundManagerUnitsOn(
                tuleva2ndPillarStockFund().getIsin(), END_OF_SEPTEMBER))
        .willReturn(Optional.of(new BigDecimal("5747351")));

    var responses = fundManagerUnits(OCTOBER_6).applyTo(responsesFor(tuleva2ndPillarStockFund()));

    assertThat(responses)
        .containsExactly(responseWithUnits(tuleva2ndPillarStockFund(), new BigDecimal("5747351")));
  }

  @Test
  void theLastMonthEndTurnsAtMidnightInTallinnNotInUtc() {
    var halfPastMidnightOnOctober1InTallinn = clockAt("2026-09-30T21:30:00Z");
    given(
            unitsInRegister.fundManagerUnitsOn(
                tuleva2ndPillarStockFund().getIsin(), END_OF_SEPTEMBER))
        .willReturn(Optional.of(new BigDecimal("5747351")));

    var responses =
        fundManagerUnits(halfPastMidnightOnOctober1InTallinn)
            .applyTo(responsesFor(tuleva2ndPillarStockFund()));

    assertThat(responses)
        .containsExactly(responseWithUnits(tuleva2ndPillarStockFund(), new BigDecimal("5747351")));
  }

  @Test
  void aPensionFundWithoutARegisterCountOnTheMonthEndHasNoUnitsToShow() {
    given(
            unitsInRegister.fundManagerUnitsOn(
                tuleva2ndPillarStockFund().getIsin(), END_OF_SEPTEMBER))
        .willReturn(Optional.empty());

    var responses = fundManagerUnits(OCTOBER_6).applyTo(responsesFor(tuleva2ndPillarStockFund()));

    assertThat(responses).containsExactly(responseFor(tuleva2ndPillarStockFund()));
  }

  @Test
  void oneFundsFailedLookupLeavesItsUnitsOutAndTheOtherFundsStillGetTheirs() {
    given(
            unitsInRegister.fundManagerUnitsOn(
                tuleva2ndPillarStockFund().getIsin(), END_OF_SEPTEMBER))
        .willThrow(new IllegalStateException("database unavailable"));
    given(unitsInRegister.fundManagerUnitsOn(tuleva2ndPillarBondFund().getIsin(), END_OF_SEPTEMBER))
        .willReturn(Optional.of(new BigDecimal("306250")));

    var responses =
        fundManagerUnits(OCTOBER_6)
            .applyTo(responsesFor(tuleva2ndPillarStockFund(), tuleva2ndPillarBondFund()));

    assertThat(responses)
        .containsExactly(
            responseFor(tuleva2ndPillarStockFund()),
            responseWithUnits(tuleva2ndPillarBondFund(), new BigDecimal("306250")));
  }

  @Test
  void anotherManagersFundHasNoFundManagerUnitsToShow() {
    var responses = fundManagerUnits(OCTOBER_6).applyTo(responsesFor(lhv2ndPillarFund()));

    assertThat(responses).containsExactly(responseFor(lhv2ndPillarFund()));
  }

  private FundManagerUnits fundManagerUnits(Clock clock) {
    return new FundManagerUnits(savingsFundUnitStats, unitsInRegister, clock, REGISTRY_CODE);
  }

  private static List<ExtendedApiFundResponse> responsesFor(Fund... funds) {
    return Arrays.stream(funds).map(FundManagerUnitsTest::responseFor).toList();
  }

  private static ExtendedApiFundResponse responseFor(Fund fund) {
    return new ExtendedApiFundResponse(fund, PensionFundStatistics.getNull(), Locale.ENGLISH);
  }

  private static ExtendedApiFundResponse responseWithUnits(Fund fund, BigDecimal units) {
    var response = responseFor(fund);
    response.setFundManagerUnits(units);
    response.setFundManagerUnitsDate(END_OF_SEPTEMBER);
    return response;
  }

  private static Clock clockAt(String instant) {
    return Clock.fixed(Instant.parse(instant), UTC);
  }
}
