package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.position.AccountType.FEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
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

  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private FundPositionImportService fundPositionImportService;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private Clock clock;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private FeeAccrualPositionSyncJob syncJob;

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
}
