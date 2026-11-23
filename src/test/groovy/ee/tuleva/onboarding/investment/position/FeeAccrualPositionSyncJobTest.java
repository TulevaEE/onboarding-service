package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.position.AccountType.FEE;
import static ee.tuleva.onboarding.pipeline.PipelineStep.FEE_ACCRUAL_SYNC;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.event.FeeAccrualPositionsSynced;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.fees.OutstandingFees;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class FeeAccrualPositionSyncJobTest {

  private static final Instant NOW = Instant.parse("2025-03-12T10:00:00Z");
  private static final ZoneId ZONE = ZoneId.of("Europe/Tallinn");
  private static final boolean CHARGED_TO_FUND = true;
  private static final boolean BORNE_BY_TULEVA = false;

  @Mock private OutstandingFees outstandingFees;
  @Mock private FundPositionImportService fundPositionImportService;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private Clock clock;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PipelineTracker pipelineTracker;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private FeeChargedToFundPolicy feeChargedToFundPolicy;

  @InjectMocks private FeeAccrualPositionSyncJob syncJob;

  @BeforeEach
  void defaultFeesChargedToFund() {
    given(feeChargedToFundPolicy.resolverFor(any(), any()))
        .willAnswer(
            invocation ->
                resolver(invocation.getArgument(0), invocation.getArgument(1), CHARGED_TO_FUND));
  }

  private static FundPosition feeAccrualPosition(
      LocalDate navDate, String accountName, BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(navDate)
        .fund(TKF100)
        .accountType(FEE)
        .accountName(accountName)
        .quantity(BigDecimal.ONE)
        .marketPrice(marketValue)
        .currency("EUR")
        .marketValue(marketValue)
        .createdAt(NOW)
        .build();
  }

  private void givenOwed(FeeChargedToFundPolicy.Resolver policy, LocalDate navDate, String owed) {
    given(outstandingFees.owedOn(policy, navDate)).willReturn(new BigDecimal(owed));
  }

  private static FeeChargedToFundPolicy.Resolver resolver(
      TulevaFund fund, FeeType feeType, boolean chargedToFund) {
    return new FeeChargedToFundPolicy.Resolver(
        fund,
        feeType,
        List.of(new FeeChargedToFundPolicy.Policy(chargedToFund, LocalDate.of(2020, 1, 1), null)));
  }

  @Test
  void sync_writesTheOtherFundsWhenOneFundHasNoResolvablePolicy() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZONE);

    var navDate = LocalDate.of(2025, 3, 10);
    given(fundPositionRepository.findDistinctNavDatesByFund(any())).willReturn(List.of());
    given(fundPositionRepository.findDistinctNavDatesByFund(TKF100)).willReturn(List.of(navDate));
    givenOwed(resolver(TKF100, MANAGEMENT, CHARGED_TO_FUND), navDate, "52.08");
    givenOwed(resolver(TKF100, DEPOT, CHARGED_TO_FUND), navDate, "6.85");
    given(feeChargedToFundPolicy.resolverFor(TUK75, MANAGEMENT))
        .willThrow(new IllegalStateException("Gap in the fee policy"));

    assertThatThrownBy(() -> syncJob.sync(7)).isInstanceOf(IllegalStateException.class);

    verify(fundPositionImportService).upsertPositions(anyList());
  }

  @Test
  void sync_writesFeeAccrualLiabilityPositions() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZONE);

    var navDate = LocalDate.of(2025, 3, 10);
    given(fundPositionRepository.findDistinctNavDatesByFund(any())).willReturn(List.of());
    given(fundPositionRepository.findDistinctNavDatesByFund(TKF100)).willReturn(List.of(navDate));

    givenOwed(resolver(TKF100, MANAGEMENT, CHARGED_TO_FUND), navDate, "52.08");
    givenOwed(resolver(TKF100, DEPOT, CHARGED_TO_FUND), navDate, "6.85");

    syncJob.sync(7);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<FundPosition>> captor = ArgumentCaptor.forClass(List.class);
    verify(fundPositionImportService).upsertPositions(captor.capture());

    var positions = captor.getValue();
    assertThat(positions).hasSize(2);

    var mgmtFee = positions.get(0);
    assertThat(mgmtFee.getFund()).isEqualTo(TKF100);
    assertThat(mgmtFee.getNavDate()).isEqualTo(navDate);
    assertThat(mgmtFee.getAccountType()).isEqualTo(FEE);
    assertThat(mgmtFee.getAccountName()).isEqualTo("Management Fee Accrual");
    assertThat(mgmtFee.getMarketValue()).isEqualByComparingTo("-52.08");
    assertThat(mgmtFee.getCurrency()).isEqualTo("EUR");

    var depotFee = positions.get(1);
    assertThat(depotFee.getAccountName()).isEqualTo("Depot Fee Accrual");
    assertThat(depotFee.getMarketValue()).isEqualByComparingTo("-6.85");
  }

  @Test
  void sync_filtersNavDatesToLastNDays() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZONE);

    var recentDate = LocalDate.of(2025, 3, 10);
    var oldDate = LocalDate.of(2025, 2, 1);
    given(fundPositionRepository.findDistinctNavDatesByFund(any()))
        .willReturn(List.of(oldDate, recentDate));

    given(outstandingFees.owedOn(any(), eq(recentDate))).willReturn(ZERO);

    syncJob.sync(7);

    verify(outstandingFees).owedOn(resolver(TKF100, MANAGEMENT, CHARGED_TO_FUND), recentDate);
    verify(outstandingFees, never()).owedOn(any(), eq(oldDate));
  }

  @Test
  void sync_asksWhatIsOwedUnderEachFeesOwnPolicy() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZONE);
    given(feeChargedToFundPolicy.resolverFor(TKF100, DEPOT))
        .willReturn(resolver(TKF100, DEPOT, BORNE_BY_TULEVA));

    var navDate = LocalDate.of(2025, 3, 10);
    given(fundPositionRepository.findDistinctNavDatesByFund(any())).willReturn(List.of());
    given(fundPositionRepository.findDistinctNavDatesByFund(TKF100)).willReturn(List.of(navDate));
    givenOwed(resolver(TKF100, MANAGEMENT, CHARGED_TO_FUND), navDate, "52.08");
    givenOwed(resolver(TKF100, DEPOT, BORNE_BY_TULEVA), navDate, "0");

    syncJob.sync(7);

    verify(fundPositionImportService)
        .upsertPositions(
            List.of(
                feeAccrualPosition(navDate, "Management Fee Accrual", new BigDecimal("-52.08")),
                feeAccrualPosition(navDate, "Depot Fee Accrual", BigDecimal.ZERO)));
  }

  @Test
  void sync_readsThePolicyOncePerFeeTypeRatherThanOncePerDate() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZONE);

    given(fundPositionRepository.findDistinctNavDatesByFund(any())).willReturn(List.of());
    given(fundPositionRepository.findDistinctNavDatesByFund(TKF100))
        .willReturn(
            List.of(
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 11), LocalDate.of(2025, 3, 12)));
    given(outstandingFees.owedOn(any(), any())).willReturn(ZERO);

    syncJob.sync(7);

    verify(feeChargedToFundPolicy, times(1)).resolverFor(TKF100, MANAGEMENT);
    verify(feeChargedToFundPolicy, times(1)).resolverFor(TKF100, DEPOT);
    verify(feeChargedToFundPolicy, never()).chargedToFund(any(), any(), any());
  }

  @Test
  void positionsImportedEventTriggersASyncAndAnnouncesTheResult() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZONE);
    given(fundPositionRepository.findDistinctNavDatesByFund(any())).willReturn(List.of());

    syncJob.onFundPositionsImported();

    verify(pipelineTracker).stepStarted(FEE_ACCRUAL_SYNC);
    verify(pipelineTracker).stepCompleted(FEE_ACCRUAL_SYNC);
    verify(eventPublisher).publishEvent(any(FeeAccrualPositionsSynced.class));
  }

  @Test
  void anAdHocRequestTriggersASyncWithoutAnnouncingTheResult() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZONE);
    given(fundPositionRepository.findDistinctNavDatesByFund(any())).willReturn(List.of());

    syncJob.onFeeAccrualPositionSyncRequested();

    verify(pipelineTracker).stepStarted(FEE_ACCRUAL_SYNC);
    verify(pipelineTracker).stepCompleted(FEE_ACCRUAL_SYNC);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void theBackfillJobSyncsTheSameWindowWithoutTouchingThePipelineTracker() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZONE);
    given(fundPositionRepository.findDistinctNavDatesByFund(any())).willReturn(List.of());

    syncJob.backfill();

    verify(fundPositionRepository, times(TulevaFund.values().length))
        .findDistinctNavDatesByFund(any());
    verifyNoInteractions(pipelineTracker);
    verify(eventPublisher, never()).publishEvent(any());
  }
}
