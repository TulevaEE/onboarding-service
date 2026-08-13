package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.event.PipelineStep.FEE_ACCRUAL_SYNC;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.position.AccountType.FEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.FeeAccrualPositionsSynced;
import ee.tuleva.onboarding.investment.event.FundPositionsImported;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.RunFeeAccrualPositionSyncRequested;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private FundPositionImportService fundPositionImportService;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private Clock clock;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PipelineTracker pipelineTracker;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private FeeChargedToFundPolicy feeChargedToFundPolicy;

  @InjectMocks private FeeAccrualPositionSyncJob syncJob;

  @org.junit.jupiter.api.BeforeEach
  void defaultFeesChargedToFund() {
    when(feeChargedToFundPolicy.resolverFor(any(), any()))
        .thenAnswer(
            invocation ->
                resolver(invocation.getArgument(0), invocation.getArgument(1), CHARGED_TO_FUND));
  }

  private static FeeChargedToFundPolicy.Resolver resolver(
      TulevaFund fund, FeeType feeType, boolean chargedToFund) {
    return new FeeChargedToFundPolicy.Resolver(
        fund,
        feeType,
        List.of(new FeeChargedToFundPolicy.Policy(chargedToFund, LocalDate.of(2020, 1, 1), null)));
  }

  @Test
  void sync_writesFeeAccrualLiabilityPositions() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZONE);

    var navDate = LocalDate.of(2025, 3, 10);
    when(fundPositionRepository.findDistinctNavDatesByFund(any())).thenReturn(List.of());
    when(fundPositionRepository.findDistinctNavDatesByFund(TKF100)).thenReturn(List.of(navDate));

    when(feeAccrualRepository.getUnsettledAccrual(TKF100, MANAGEMENT, navDate))
        .thenReturn(new BigDecimal("52.08"));
    when(feeAccrualRepository.getUnsettledAccrual(TKF100, DEPOT, navDate))
        .thenReturn(new BigDecimal("6.85"));

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
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZONE);

    var recentDate = LocalDate.of(2025, 3, 10);
    var oldDate = LocalDate.of(2025, 2, 1);
    when(fundPositionRepository.findDistinctNavDatesByFund(any()))
        .thenReturn(List.of(oldDate, recentDate));

    when(feeAccrualRepository.getUnsettledAccrual(any(), any(), eq(recentDate)))
        .thenReturn(BigDecimal.ZERO);

    syncJob.sync(7);

    verify(feeAccrualRepository).getUnsettledAccrual(TKF100, MANAGEMENT, recentDate);
    verify(feeAccrualRepository, never()).getUnsettledAccrual(any(), any(), eq(oldDate));
  }

  @Test
  void sync_reportsZeroForAFeeTheFundIsNotChargedAndDoesNotReadTheAccrual() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZONE);
    when(feeChargedToFundPolicy.resolverFor(TKF100, DEPOT))
        .thenReturn(resolver(TKF100, DEPOT, BORNE_BY_TULEVA));

    var navDate = LocalDate.of(2025, 3, 10);
    when(fundPositionRepository.findDistinctNavDatesByFund(any())).thenReturn(List.of());
    when(fundPositionRepository.findDistinctNavDatesByFund(TKF100)).thenReturn(List.of(navDate));
    when(feeAccrualRepository.getUnsettledAccrual(TKF100, MANAGEMENT, navDate))
        .thenReturn(new BigDecimal("52.08"));

    syncJob.sync(7);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<FundPosition>> captor = ArgumentCaptor.forClass(List.class);
    verify(fundPositionImportService).upsertPositions(captor.capture());

    var positions = captor.getValue();
    assertThat(positions.get(0).getMarketValue()).isEqualByComparingTo("-52.08");
    assertThat(positions.get(1).getAccountName()).isEqualTo("Depot Fee Accrual");
    assertThat(positions.get(1).getMarketValue()).isEqualByComparingTo("0");
    verify(feeAccrualRepository, never()).getUnsettledAccrual(TKF100, DEPOT, navDate);
  }

  @Test
  void sync_readsThePolicyOncePerFeeTypeRatherThanOncePerDate() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZONE);

    when(fundPositionRepository.findDistinctNavDatesByFund(any())).thenReturn(List.of());
    when(fundPositionRepository.findDistinctNavDatesByFund(TKF100))
        .thenReturn(
            List.of(
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 11), LocalDate.of(2025, 3, 12)));
    when(feeAccrualRepository.getUnsettledAccrual(any(), any(), any())).thenReturn(BigDecimal.ZERO);

    syncJob.sync(7);

    verify(feeChargedToFundPolicy, times(1)).resolverFor(TKF100, MANAGEMENT);
    verify(feeChargedToFundPolicy, times(1)).resolverFor(TKF100, DEPOT);
    verify(feeChargedToFundPolicy, never()).chargedToFund(any(), any(), any());
  }

  @Test
  void positionsImportedEventTriggersASyncAndAnnouncesTheResult() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZONE);
    when(fundPositionRepository.findDistinctNavDatesByFund(any())).thenReturn(List.of());

    syncJob.onFundPositionsImported(new FundPositionsImported());

    verify(pipelineTracker).stepStarted(FEE_ACCRUAL_SYNC);
    verify(pipelineTracker).stepCompleted(FEE_ACCRUAL_SYNC);
    verify(eventPublisher).publishEvent(any(FeeAccrualPositionsSynced.class));
  }

  @Test
  void anAdHocRequestTriggersASyncWithoutAnnouncingTheResult() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZONE);
    when(fundPositionRepository.findDistinctNavDatesByFund(any())).thenReturn(List.of());

    syncJob.onFeeAccrualPositionSyncRequested(new RunFeeAccrualPositionSyncRequested());

    verify(pipelineTracker).stepStarted(FEE_ACCRUAL_SYNC);
    verify(pipelineTracker).stepCompleted(FEE_ACCRUAL_SYNC);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void theBackfillJobSyncsTheSameWindowWithoutTouchingThePipelineTracker() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZONE);
    when(fundPositionRepository.findDistinctNavDatesByFund(any())).thenReturn(List.of());

    syncJob.backfill();

    verify(fundPositionRepository, times(TulevaFund.values().length))
        .findDistinctNavDatesByFund(any());
    verifyNoInteractions(pipelineTracker);
    verify(eventPublisher, never()).publishEvent(any());
  }
}
