package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SavingsFundLedgerStackConfiguration;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  SavingsFundLedgerStackConfiguration.class,
  FundBankLedger.class,
  NavFeeAccrualLedger.class,
  NavLedgerRepository.class,
  InvestmentParameterRepository.class,
  PublicHolidays.class,
  BlackrockAdjustmentAfterRebateChecker.class
})
class BlackrockAdjustmentAfterRebateCheckerIT {

  private static final LocalDate FRIDAY_BEFORE = LocalDate.of(2026, 5, 29);
  private static final LocalDate MONDAY = LocalDate.of(2026, 6, 1);
  private static final LocalDate TUESDAY = LocalDate.of(2026, 6, 2);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 6, 3);
  private static final LocalDate LAST_DAY_REPORTED = LocalDate.of(2026, 6, 9);
  private static final LocalDate FIRST_DAY_AGED_OUT = LocalDate.of(2026, 6, 10);
  private static final LocalDate THURSDAY = LocalDate.of(2026, 6, 4);
  private static final LocalDate SATURDAY_SIX_WORKING_DAYS_AFTER_THURSDAY =
      LocalDate.of(2026, 6, 13);
  private static final LocalDate MONDAY_SEVEN_WORKING_DAYS_AFTER_THURSDAY =
      LocalDate.of(2026, 6, 15);
  private static final BigDecimal RECEIVABLE = new BigDecimal("1500.00");
  private static final BigDecimal REBATE = new BigDecimal("1234.56");
  private static final LocalDateTime FIRST_RECORDING = LocalDateTime.of(2100, 1, 1, 0, 0);

  @Autowired BlackrockAdjustmentAfterRebateChecker checker;
  @Autowired FundBankLedger fundBankLedger;
  @Autowired NavFeeAccrualLedger navFeeAccrualLedger;
  @Autowired EntityManager entityManager;

  private LocalDateTime nextRecording = FIRST_RECORDING;

  @Test
  void aRebateTheReceivableHasNotMovedSinceWarnsOnceTheSeededWorkingDayOfGraceHasPassed() {
    receivableSetOn(FRIDAY_BEFORE, RECEIVABLE);
    rebateLandsOn(TUK75, MONDAY);

    assertThat(severities(TUESDAY)).containsExactly(PASS);
    assertThat(checker.check(TUK75, WEDNESDAY))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.severity()).isEqualTo(WARNING);
              assertThat(finding.deviationAmount()).isEqualByComparingTo(REBATE);
            });
  }

  @Test
  void settingTheReceivableAfterTheRebateLandsClearsTheWarning() {
    receivableSetOn(FRIDAY_BEFORE, RECEIVABLE);
    rebateLandsOn(TUK75, MONDAY);
    assertThat(severities(WEDNESDAY)).containsExactly(WARNING);

    receivableSetOn(WEDNESDAY, RECEIVABLE.subtract(REBATE));

    assertThat(severities(WEDNESDAY)).containsExactly(PASS);
  }

  @Test
  void settingTheReceivableForTheRebatesOwnBookingDayClearsTheWarningThoughItIsStampedEarlier() {
    receivableSetOn(FRIDAY_BEFORE, RECEIVABLE);
    rebateLandsOn(TUK75, MONDAY);

    receivableSetOn(MONDAY, RECEIVABLE.subtract(REBATE));

    assertThat(severities(WEDNESDAY)).containsExactly(PASS);
  }

  @Test
  void aReceivableSetForTheRebatesBookingDayBeforeTheRebateLandedDoesNotClearIt() {
    receivableSetOn(MONDAY, RECEIVABLE);
    rebateLandsOn(TUK75, MONDAY);

    assertThat(severities(WEDNESDAY)).containsExactly(WARNING);
  }

  @Test
  void aReceivableSetAfterTheRebateLandedButForAnEarlierDayDoesNotClearIt() {
    rebateLandsOn(TUK75, MONDAY);
    receivableSetOn(FRIDAY_BEFORE, RECEIVABLE);

    assertThat(severities(WEDNESDAY)).containsExactly(WARNING);
  }

  @Test
  void aReceivableAlreadyAtItsTargetWritesNothingAndTheWarningClearsOnceTheRebateAgesOut() {
    rebateLandsOn(TUK75, MONDAY);

    var unchanged = navFeeAccrualLedger.recordBlackrockAdjustment(TUK75, TUESDAY, ZERO);

    assertThat(unchanged.transactionCreated()).isFalse();
    assertThat(severities(LAST_DAY_REPORTED)).containsExactly(WARNING);
    assertThat(severities(FIRST_DAY_AGED_OUT)).containsExactly(PASS);
  }

  @Test
  void aCheckRunOnASaturdayStillWarnsOnTheLastWorkingDayOfTheRebatesReportingWindow() {
    rebateLandsOn(TUK75, THURSDAY);

    assertThat(severities(SATURDAY_SIX_WORKING_DAYS_AFTER_THURSDAY)).containsExactly(WARNING);
    assertThat(severities(MONDAY_SEVEN_WORKING_DAYS_AFTER_THURSDAY)).containsExactly(PASS);
  }

  @Test
  void anotherFundsRebateDoesNotWarnForThisFund() {
    rebateLandsOn(TUV100, MONDAY);

    assertThat(severities(WEDNESDAY)).containsExactly(PASS);
  }

  private List<FeeCheckSeverity> severities(LocalDate checkDate) {
    return checker.check(TUK75, checkDate).stream().map(FeeCheckFinding::severity).toList();
  }

  private void receivableSetOn(LocalDate date, BigDecimal targetBalance) {
    navFeeAccrualLedger.recordBlackrockAdjustment(TUK75, date, targetBalance);
    stampRecordingOrder();
  }

  private void rebateLandsOn(TulevaFund fund, LocalDate bookingDate) {
    fundBankLedger.recordManagementFeeRebate(
        fund, REBATE, UUID.randomUUID(), FUND_INVESTMENT_CASH_CLEARING, bookingDate, "Rebate");
    stampRecordingOrder();
  }

  private void stampRecordingOrder() {
    entityManager.flush();
    entityManager
        .createNativeQuery(
            "UPDATE ledger.transaction SET created_at = :recordedAt"
                + " WHERE created_at < :firstRecording")
        .setParameter("recordedAt", nextRecording)
        .setParameter("firstRecording", FIRST_RECORDING)
        .executeUpdate();
    nextRecording = nextRecording.plusMinutes(1);
  }
}
