package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisSnapshot;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.Compared;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.HoldingDifference;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.RegistryHasNoHoldings;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportHasNoSecurities;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportRowsWithoutQuantity;
import ee.tuleva.onboarding.investment.transaction.ingest.PortfolioReconciliationMismatchEvent.MismatchEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private static final String ISIN_C = "IE000QWCYQT0";
  private static final String ISIN_D = "IE00BFNM3G45";
  private static final String ISIN_E = "LU1291106356";

  @Mock private PortfolioCostBasisService costBasisService;
  @Mock private NavReportPositionLookup navReportLookup;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private FundPositionRepository fundPositionRepository;

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
  void emptyCostBasisLedger_publishesSkippedInsteadOfEveryIsinMismatching() {
    Map<String, BigDecimal> reported =
        Map.of(ISIN_A, new BigDecimal("10000"), ISIN_B, new BigDecimal("2000"));
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF)).willReturn(List.of());
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF)).willReturn(reported);

    service.reconcile(TUK75, AS_OF);

    verify(eventPublisher)
        .publishEvent(new PortfolioReconciliationSkippedEvent(TUK75, AS_OF, Map.of(), reported));
  }

  @Test
  void emptyNavReport_publishesSkippedInsteadOfEveryIsinMismatching() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "10000.0000"), costBasis(ISIN_B, "2000.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF)).willReturn(Map.of());

    service.reconcile(TUK75, AS_OF);

    verify(eventPublisher)
        .publishEvent(
            new PortfolioReconciliationSkippedEvent(
                TUK75,
                AS_OF,
                Map.of(ISIN_A, new BigDecimal("10000.0000"), ISIN_B, new BigDecimal("2000.0000")),
                Map.of()));
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
        .willReturn(List.of(costBasis(ISIN_A, "10005.0000"), costBasis(ISIN_B, "100.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_B, new BigDecimal("100.0000")));

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
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_B, "100.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_A, new BigDecimal("250.0000"), ISIN_B, new BigDecimal("100.0000")));

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

  @Test
  void aZeroRegistryRowWithNoNavReportRow_sendsNoMismatch() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "10000.0000"), costBasis(ISIN_B, "0.0000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_A, new BigDecimal("10000.0000"), ISIN_C, BigDecimal.ZERO));

    service.reconcile(TUK75, AS_OF);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void aHoldingWithinToleranceThatOneSideDoesNotList_stillSendsAMismatch() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "10000.0000"), costBasis(ISIN_B, "0.5000")));
    given(navReportLookup.findSecurityQuantities(TUK75, AS_OF))
        .willReturn(Map.of(ISIN_A, new BigDecimal("10000.0000"), ISIN_C, new BigDecimal("0.500")));

    service.reconcile(TUK75, AS_OF);

    verify(eventPublisher)
        .publishEvent(
            new PortfolioReconciliationMismatchEvent(
                TUK75,
                AS_OF,
                List.of(
                    new MismatchEntry(
                        ISIN_B, new BigDecimal("0.5000"), null, new BigDecimal("0.5000")),
                    new MismatchEntry(
                        ISIN_C, null, new BigDecimal("0.500"), new BigDecimal("-0.500")))));
  }

  @Test
  void againstThePositionReport_listsEachHoldingOutsideTheToleranceWithBothQuantities() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(
            List.of(
                costBasis(ISIN_A, "1000.0000"),
                costBasis(ISIN_B, "500.0000"),
                costBasis(ISIN_C, "300.0000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(
            List.of(
                reportRow(ISIN_A, "990.000000"),
                reportRow(ISIN_B, "300.000000"),
                reportRow(ISIN_B, "199.500000"),
                reportRow(ISIN_D, "40.000000")));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new Compared(
                List.of(
                    new HoldingDifference(ISIN_C, new BigDecimal("300"), null),
                    new HoldingDifference(ISIN_A, new BigDecimal("1000"), new BigDecimal("990")),
                    new HoldingDifference(ISIN_D, null, new BigDecimal("40")))));
    verifyNoInteractions(eventPublisher, navReportLookup);
  }

  @Test
  void againstThePositionReport_anEmptyRegistryIsNotAMismatchOnEveryHolding() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF)).willReturn(List.of());
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(List.of(reportRow(ISIN_A, "990.000000")));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .isEqualTo(
            new RegistryHasNoHoldings(
                List.of(new HoldingDifference(ISIN_A, null, new BigDecimal("990.000000")))));
  }

  @Test
  void againstThePositionReport_aReportWithoutSecuritiesIsNotAMismatchOnEveryHolding() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "1000.0000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(List.of());

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .isEqualTo(new ReportHasNoSecurities());
  }

  @Test
  void againstThePositionReport_excludingTheOnlyInstrumentSebListsLeavesTheReportNotEmpty() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "1000.0000"), costBasis(ISIN_B, "500.0000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(List.of(reportRow(ISIN_A, "1000.000000")));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of(ISIN_A)))
        .isEqualTo(
            new Compared(List.of(new HoldingDifference(ISIN_B, new BigDecimal("500.0000"), null))));
  }

  @Test
  void againstThePositionReport_aReportWithNoSecuritiesStaysEmptyWhateverIsExcluded() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "1000.0000"), costBasis(ISIN_B, "500.0000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(List.of());

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of(ISIN_A)))
        .isEqualTo(new ReportHasNoSecurities());
  }

  @Test
  void againstThePositionReport_aFundWithoutSecuritiesOnEitherSideHasNothingToDiffer() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF)).willReturn(List.of());
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(List.of());

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .isEqualTo(new Compared(List.of()));
  }

  @Test
  void againstThePositionReport_aSecurityWithoutAQuantityIsNamedInsteadOfLeftOut() {
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(
            List.of(
                reportRow(ISIN_A, "1000.000000"),
                FundPosition.builder()
                    .navDate(AS_OF)
                    .fund(TUK75)
                    .accountType(SECURITY)
                    .accountName(ISIN_B)
                    .accountId(ISIN_B)
                    .marketValue(BigDecimal.ONE)
                    .build(),
                FundPosition.builder()
                    .navDate(AS_OF)
                    .fund(TUK75)
                    .accountType(SECURITY)
                    .accountName("A line that is not an instrument")
                    .marketValue(BigDecimal.ONE)
                    .build()));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .isEqualTo(new ReportRowsWithoutQuantity(List.of(ISIN_B)));
  }

  @Test
  void againstThePositionReport_anInstrumentAtZeroOnOneSideAndAbsentOnTheOtherIsNoDifference() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "1000.0000"), costBasis(ISIN_B, "0.0000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(List.of(reportRow(ISIN_A, "1000.000000"), reportRow(ISIN_C, "0.000000")));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .isEqualTo(new Compared(List.of()));
  }

  @Test
  void againstThePositionReport_aHoldingWithinToleranceThatOneSideDoesNotListIsADifference() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(
            List.of(
                costBasis(ISIN_A, "100.0000"),
                costBasis(ISIN_B, "1.0000"),
                costBasis(ISIN_C, "0.0000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(
            List.of(
                reportRow(ISIN_A, "100.000000"),
                reportRow(ISIN_D, "0.500000"),
                reportRow(ISIN_E, "0.000000")));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .isEqualTo(
            new Compared(
                List.of(
                    new HoldingDifference(ISIN_B, new BigDecimal("1.0000"), null),
                    new HoldingDifference(ISIN_D, null, new BigDecimal("0.500000")))));
  }

  @Test
  void againstThePositionReport_soldOutRowsOnOneSideAndNothingOnTheOtherAreNoDifference() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "0.0000")));
    given(costBasisService.snapshotForFundAndDate(TUK00, AS_OF)).willReturn(List.of());
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(List.of());
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK00, SECURITY))
        .willReturn(List.of(reportRow(ISIN_B, "0.000000")));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .isEqualTo(new Compared(List.of()));
    assertThat(service.againstPositionReport(TUK00, AS_OF, Set.of()))
        .isEqualTo(new Compared(List.of()));
  }

  @Test
  void againstThePositionReport_aZeroRowAgainstAQuantityWithinToleranceIsNoDifference() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "0.0000")));
    given(costBasisService.snapshotForFundAndDate(TUK00, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "0.5000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(List.of(reportRow(ISIN_A, "0.500000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK00, SECURITY))
        .willReturn(List.of(reportRow(ISIN_A, "0.000000")));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .isEqualTo(new Compared(List.of()));
    assertThat(service.againstPositionReport(TUK00, AS_OF, Set.of()))
        .isEqualTo(new Compared(List.of()));
  }

  @Test
  void againstThePositionReport_leavesExcludedInstrumentsOutOfBothSides() {
    given(costBasisService.snapshotForFundAndDate(TUK75, AS_OF))
        .willReturn(List.of(costBasis(ISIN_A, "1000.0000"), costBasis(ISIN_B, "500.0000")));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF, TUK75, SECURITY))
        .willReturn(
            List.of(
                reportRow(ISIN_A, "1000.000000"),
                FundPosition.builder()
                    .navDate(AS_OF)
                    .fund(TUK75)
                    .accountType(SECURITY)
                    .accountName(ISIN_B)
                    .accountId(ISIN_B)
                    .marketValue(BigDecimal.ONE)
                    .build()));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of(ISIN_B)))
        .isEqualTo(new Compared(List.of()));
  }

  private static FundPosition reportRow(String isin, String quantity) {
    return FundPosition.builder()
        .navDate(AS_OF)
        .fund(TUK75)
        .accountType(SECURITY)
        .accountName(isin)
        .accountId(isin)
        .quantity(new BigDecimal(quantity))
        .marketValue(BigDecimal.ONE)
        .build();
  }

  private PortfolioCostBasisSnapshot costBasis(String isin, String quantity) {
    return new PortfolioCostBasisSnapshot(
        isin, new BigDecimal(quantity), BigDecimal.ZERO, BigDecimal.ZERO, AS_OF);
  }
}
