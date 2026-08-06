package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.BLACKROCK_ADJUSTMENT_FRESHNESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.LEDGER_ACCRUAL_CONSISTENCY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.investment.fees.FeeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCheckServiceTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 6, 4);

  @Mock private LedgerAccrualConsistencyChecker ledgerAccrualConsistencyChecker;
  @Mock private FeeBaseCompletenessChecker feeBaseCompletenessChecker;
  @Mock private BlackrockAdjustmentFreshnessChecker blackrockAdjustmentFreshnessChecker;
  @Mock private FeeCheckEventRepository eventRepository;
  @Mock private FeeCheckNotifier notifier;

  private FeeCheckService service;

  @BeforeEach
  void setUp() {
    service =
        new FeeCheckService(
            ledgerAccrualConsistencyChecker,
            feeBaseCompletenessChecker,
            blackrockAdjustmentFreshnessChecker,
            eventRepository,
            notifier,
            35);
  }

  @Test
  void writesOneEventPerCheckTypeAndScope() {
    givenAllCheckersPass();

    service.runDailyChecks(List.of(TUK75), CHECK_DATE);

    assertThat(savedEvents())
        .extracting(FeeCheckEvent::getCheckType, FeeCheckEvent::getFeeScope)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(
                LEDGER_ACCRUAL_CONSISTENCY, FeeCheckScope.MANAGEMENT),
            org.assertj.core.groups.Tuple.tuple(LEDGER_ACCRUAL_CONSISTENCY, FeeCheckScope.DEPOT),
            org.assertj.core.groups.Tuple.tuple(FEE_BASE_COMPLETENESS, FeeCheckScope.ALL),
            org.assertj.core.groups.Tuple.tuple(BLACKROCK_ADJUSTMENT_FRESHNESS, FeeCheckScope.ALL));
  }

  @Test
  void dailyEventsCarryNoFeeMonthSoTheyDiffAgainstEachOther() {
    givenAllCheckersPass();

    service.runDailyChecks(List.of(TUK75), CHECK_DATE);

    assertThat(savedEvents()).allMatch(event -> event.getFeeMonth() == null);
  }

  @Test
  void aCheckerThatThrowsIsRecordedAsNotRunAndTheOthersStillRun() {
    willThrow(new IllegalStateException("boom"))
        .given(feeBaseCompletenessChecker)
        .check(any(), any(), any());
    givenLedgerCheckerPassesForBothFeeTypes();
    given(blackrockAdjustmentFreshnessChecker.check(any(), any()))
        .willReturn(List.of(passFinding(BLACKROCK_ADJUSTMENT_FRESHNESS, FeeCheckScope.ALL)));

    service.runDailyChecks(List.of(TUK75), CHECK_DATE);

    assertThat(savedEvents())
        .filteredOn(event -> event.getCheckType() == FEE_BASE_COMPLETENESS)
        .singleElement()
        .extracting(FeeCheckEvent::getSeverity)
        .isEqualTo(NOT_RUN);
    assertThat(savedEvents())
        .filteredOn(event -> event.getCheckType() == LEDGER_ACCRUAL_CONSISTENCY)
        .isNotEmpty()
        .allMatch(event -> event.getSeverity() == PASS);
  }

  @Test
  void notRunIsNotCountedAsADeviation() {
    willThrow(new IllegalStateException("boom"))
        .given(feeBaseCompletenessChecker)
        .check(any(), any(), any());
    givenLedgerCheckerPassesForBothFeeTypes();
    given(blackrockAdjustmentFreshnessChecker.check(any(), any()))
        .willReturn(List.of(passFinding(BLACKROCK_ADJUSTMENT_FRESHNESS, FeeCheckScope.ALL)));

    service.runDailyChecks(List.of(TUK75), CHECK_DATE);

    assertThat(savedEvents()).allMatch(event -> !event.isDeviationFound());
  }

  private void givenAllCheckersPass() {
    givenLedgerCheckerPassesForBothFeeTypes();
    given(feeBaseCompletenessChecker.check(any(), any(), any()))
        .willReturn(List.of(passFinding(FEE_BASE_COMPLETENESS, FeeCheckScope.ALL)));
    given(blackrockAdjustmentFreshnessChecker.check(any(), any()))
        .willReturn(List.of(passFinding(BLACKROCK_ADJUSTMENT_FRESHNESS, FeeCheckScope.ALL)));
  }

  private void givenLedgerCheckerPassesForBothFeeTypes() {
    given(ledgerAccrualConsistencyChecker.check(any(), eq(FeeType.MANAGEMENT), any(), any()))
        .willReturn(List.of(passFinding(LEDGER_ACCRUAL_CONSISTENCY, FeeCheckScope.MANAGEMENT)));
    given(ledgerAccrualConsistencyChecker.check(any(), eq(FeeType.DEPOT), any(), any()))
        .willReturn(List.of(passFinding(LEDGER_ACCRUAL_CONSISTENCY, FeeCheckScope.DEPOT)));
  }

  private List<FeeCheckEvent> savedEvents() {
    var captor = ArgumentCaptor.forClass(FeeCheckEvent.class);
    org.mockito.Mockito.verify(eventRepository, org.mockito.Mockito.atLeastOnce())
        .save(captor.capture());
    return captor.getAllValues();
  }

  private FeeCheckFinding passFinding(FeeCheckType checkType, FeeCheckScope scope) {
    return new FeeCheckFinding(TUK75, checkType, scope, PASS, "", null, Map.of());
  }
}
