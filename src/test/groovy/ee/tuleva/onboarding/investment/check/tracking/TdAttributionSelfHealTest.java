package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.investment.fees.rate.InstrumentOcfService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TdAttributionSelfHealTest {

  private static final YearMonth JUNE = YearMonth.of(2026, 6);
  private static final YearMonth JULY = YearMonth.of(2026, 7);

  @Mock TdAttributionStaleness staleness;
  @Mock InstrumentOcfService instrumentOcfService;
  @Mock PeriodicTdAttributionService service;
  @Mock TrackingDifferenceNotifier notifier;

  private TdAttributionSelfHeal selfHeal;

  @BeforeEach
  void setUp() {
    selfHeal =
        new TdAttributionSelfHeal(
            staleness,
            new ResolvedInstrumentRatesGate(instrumentOcfService, notifier),
            service,
            notifier);
  }

  @Test
  void rewritesOnlyTheFundMonthsWhoseChecksChangedSinceTheirAttribution() {
    given(staleness.needsRewrite(TUK75, JUNE)).willReturn(true);
    given(staleness.needsRewrite(TUK00, JULY)).willReturn(true);
    givenRatesResolvedFor(JUNE, JULY);

    selfHeal.rewriteStaleMonths(List.of(JUNE, JULY));

    then(service).should().computeAttribution(TUK75, JUNE.atDay(1), JUNE.atEndOfMonth(), MONTHLY);
    then(service).should().computeAttribution(TUK00, JULY.atDay(1), JULY.atEndOfMonth(), MONTHLY);
    then(service).shouldHaveNoMoreInteractions();
  }

  @Test
  void judgesAMonthOnlyAfterRewritingTheEarlierOnesWhoseRecheckRewritesItsChecks() {
    givenEveryFundStaleIn(JUNE);
    givenRatesResolvedFor(JUNE);

    selfHeal.rewriteStaleMonths(List.of(JUNE, JULY));

    var inOrder = inOrder(service, staleness);
    inOrder
        .verify(service, times(TulevaFund.values().length))
        .computeAttribution(any(), eq(JUNE.atDay(1)), eq(JUNE.atEndOfMonth()), eq(MONTHLY));
    inOrder.verify(staleness).needsRewrite(TulevaFund.values()[0], JULY);
  }

  @Test
  void aMonthWhoseStalenessCannotBeJudgedDoesNotStopTheLaterMonths() {
    given(staleness.needsRewrite(any(), eq(JUNE)))
        .willThrow(new DataIntegrityViolationException("June unreadable"));
    given(staleness.needsRewrite(TUK75, JULY)).willReturn(true);
    givenRatesResolvedFor(JULY);

    selfHeal.rewriteStaleMonths(List.of(JUNE, JULY));

    then(service).should().computeAttribution(TUK75, JULY.atDay(1), JULY.atEndOfMonth(), MONTHLY);
  }

  @Test
  void aFundMonthItCannotRewriteIsReportedAndTheOthersAreStillRewritten() {
    givenEveryFundStaleIn(JUNE);
    givenRatesResolvedFor(JUNE);
    willThrow(new DataIntegrityViolationException("uq_td_attribution"))
        .given(service)
        .computeAttribution(TUK00, JUNE.atDay(1), JUNE.atEndOfMonth(), MONTHLY);

    selfHeal.rewriteStaleMonths(List.of(JUNE));

    then(notifier)
        .should()
        .notifyAttributionFailed(
            TUK00, JUNE.atDay(1), JUNE.atEndOfMonth(), "DataIntegrityViolationException");
    then(notifier).shouldHaveNoMoreInteractions();
    then(service)
        .should(times(TulevaFund.values().length))
        .computeAttribution(any(), eq(JUNE.atDay(1)), eq(JUNE.atEndOfMonth()), eq(MONTHLY));
  }

  @Test
  void aFundMonthWhosePeriodIsStillStaleIsNotReportedAgainAndTheOthersAreStillRewritten() {
    givenEveryFundStaleIn(JUNE);
    givenRatesResolvedFor(JUNE);
    willThrow(new AttributionPeriodStillStaleException("already reported"))
        .given(service)
        .computeAttribution(TUK00, JUNE.atDay(1), JUNE.atEndOfMonth(), MONTHLY);

    selfHeal.rewriteStaleMonths(List.of(JUNE));

    then(notifier).shouldHaveNoInteractions();
    then(service)
        .should(times(TulevaFund.values().length))
        .computeAttribution(any(), eq(JUNE.atDay(1)), eq(JUNE.atEndOfMonth()), eq(MONTHLY));
  }

  @Test
  void leavesAStaleMonthWhoseRatesTheRateJobHasNotResolvedAndSaysSo() {
    given(staleness.needsRewrite(TUK75, JUNE)).willReturn(true);
    given(instrumentOcfService.hasRatesResolvedAfterItClosed(JUNE)).willReturn(false);

    selfHeal.rewriteStaleMonths(List.of(JUNE));

    then(service).shouldHaveNoInteractions();
    then(notifier)
        .should()
        .notifyAttributionRefusedForUnresolvedRates(
            List.of(TulevaFund.values()), JUNE.atDay(1), JUNE.atEndOfMonth(), JUNE);
  }

  @Test
  void saysNothingAboutAMonthWithNoStaleFundEvenWhenItsRatesAreUnresolved() {
    selfHeal.rewriteStaleMonths(List.of(JUNE));

    then(instrumentOcfService).shouldHaveNoInteractions();
    then(service).shouldHaveNoInteractions();
    then(notifier).shouldHaveNoInteractions();
  }

  private void givenEveryFundStaleIn(YearMonth month) {
    for (var fund : TulevaFund.values()) {
      given(staleness.needsRewrite(fund, month)).willReturn(true);
    }
  }

  private void givenRatesResolvedFor(YearMonth... months) {
    for (var month : months) {
      given(instrumentOcfService.hasRatesResolvedAfterItClosed(month)).willReturn(true);
    }
  }
}
