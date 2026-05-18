package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.ingest.PortfolioReconciliationMismatchEvent.MismatchEntry;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PortfolioReconciliationServiceTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 5, 18);
  private static final String ISIN_A = "IE00BFG1TM61";
  private static final String ISIN_B = "IE0009FT4LX4";

  @Mock private PortfolioCostBasisService costBasisService;
  @Mock private NavReportPositionLookup navReportLookup;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PortfolioReconciliationService service;

  @BeforeEach
  void setUp() {
    service.setTolerance(BigDecimal.ONE);
  }

  @Test
  void quantitiesAgree_noEvent() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "10000.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_A, new BigDecimal("10000.0000")));

    service.reconcile(TUK75, AS_OF);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void quantityWithinTolerance_noEvent() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "10000.5000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_A, new BigDecimal("10000.0000")));

    service.reconcile(TUK75, AS_OF);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void quantityOutsideTolerance_emitsEvent() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "10005.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_A, new BigDecimal("10000.0000")));

    service.reconcile(TUK75, AS_OF);

    ArgumentCaptor<PortfolioReconciliationMismatchEvent> captor =
        ArgumentCaptor.forClass(PortfolioReconciliationMismatchEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    PortfolioReconciliationMismatchEvent event = captor.getValue();
    assertThat(event.fund()).isEqualTo(TUK75);
    assertThat(event.asOfDate()).isEqualTo(AS_OF);
    assertThat(event.mismatches()).hasSize(1);
    MismatchEntry entry = event.mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_A);
    assertThat(entry.ourQuantity()).isEqualByComparingTo("10005.0000");
    assertThat(entry.theirQuantity()).isEqualByComparingTo("10000.0000");
    assertThat(entry.delta()).isEqualByComparingTo("5.0000");
  }

  @Test
  void missingFromTheirSide_emitsEventWithNullTheirQuantity() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "10005.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF)).willReturn(Map.of());

    service.reconcile(TUK75, AS_OF);

    ArgumentCaptor<PortfolioReconciliationMismatchEvent> captor =
        ArgumentCaptor.forClass(PortfolioReconciliationMismatchEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    MismatchEntry entry = captor.getValue().mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_A);
    assertThat(entry.ourQuantity()).isEqualByComparingTo("10005.0000");
    assertThat(entry.theirQuantity()).isNull();
    assertThat(entry.delta()).isEqualByComparingTo("10005.0000");
  }

  @Test
  void missingFromOurSide_emitsEventWithNullOurQuantity() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF)).willReturn(List.of());
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_A, new BigDecimal("250.0000")));

    service.reconcile(TUK75, AS_OF);

    ArgumentCaptor<PortfolioReconciliationMismatchEvent> captor =
        ArgumentCaptor.forClass(PortfolioReconciliationMismatchEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    MismatchEntry entry = captor.getValue().mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_A);
    assertThat(entry.ourQuantity()).isNull();
    assertThat(entry.theirQuantity()).isEqualByComparingTo("250.0000");
    assertThat(entry.delta()).isEqualByComparingTo("-250.0000");
  }

  @Test
  void noSnapshotAndNoNavReport_noEvent() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF)).willReturn(List.of());
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF)).willReturn(Map.of());

    service.reconcile(TUK75, AS_OF);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void unionOfIsinsAcrossSides() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "10000.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_B, new BigDecimal("250.0000")));

    service.reconcile(TUK75, AS_OF);

    ArgumentCaptor<PortfolioReconciliationMismatchEvent> captor =
        ArgumentCaptor.forClass(PortfolioReconciliationMismatchEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    PortfolioReconciliationMismatchEvent event = captor.getValue();
    assertThat(event.mismatches()).hasSize(2);
    assertThat(event.mismatches())
        .extracting(MismatchEntry::isin)
        .containsExactlyInAnyOrder(ISIN_A, ISIN_B);
  }

  @Test
  void zeroQuantityZeroNavReport_isMatched() {
    // both sides have an entry for the same ISIN, both zero — no mismatch
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "0.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_A, BigDecimal.ZERO));

    service.reconcile(TUK75, AS_OF);

    verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
  }

  private PortfolioCostBasis costBasis(String isin, String quantity) {
    return PortfolioCostBasis.builder()
        .id(1L)
        .fundIsin(TUK75.getIsin())
        .instrumentIsin(isin)
        .asOfDate(AS_OF)
        .quantity(new BigDecimal(quantity))
        .avgUnitCost(BigDecimal.ZERO)
        .totalCost(BigDecimal.ZERO)
        .source("test")
        .build();
  }
}
