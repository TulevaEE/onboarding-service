package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.NavPositionLedger;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPositionImportJobTest {

  @Mock private FundPositionRepository repository;
  @Mock private InvestmentReportService reportService;
  @Mock private NavPositionLedger navPositionLedger;
  @Mock private NavLedgerRepository navLedgerRepository;

  private SwedbankFundPositionParser swedbankParser;
  private SebFundPositionParser sebParser;
  private FundPositionImportService importService;
  private FundPositionImportJob job;

  @BeforeEach
  void setUp() {
    swedbankParser = new SwedbankFundPositionParser();
    sebParser = new SebFundPositionParser();
    importService = new FundPositionImportService(repository);
    job =
        new FundPositionImportJob(
            swedbankParser,
            sebParser,
            importService,
            reportService,
            repository,
            navPositionLedger,
            navLedgerRepository);

    lenient().when(navLedgerRepository.getSystemAccountBalance(any())).thenReturn(ZERO);
  }

  private static final List<Map<String, Object>> SAMPLE_RAW_DATA =
      List.of(
          Map.ofEntries(
              Map.entry("ReportDate", "06.01.2026"),
              Map.entry("NAVDate", "05.01.2026"),
              Map.entry("Portfolio", "Tuleva Maailma Aktsiate Pensionifond"),
              Map.entry("AssetType", "Equities"),
              Map.entry("FundCurr", "EUR"),
              Map.entry("ISIN", "IE00BFG1TM61"),
              Map.entry("AssetName", "ISHARES DEV WLD ESG"),
              Map.entry("Quantity", "1000000"),
              Map.entry("AssetCurr", "EUR"),
              Map.entry("MarketValuePC", "33500000")),
          Map.ofEntries(
              Map.entry("ReportDate", "06.01.2026"),
              Map.entry("NAVDate", "05.01.2026"),
              Map.entry("Portfolio", "Tuleva Maailma Aktsiate Pensionifond"),
              Map.entry("AssetType", "Cash & Cash Equiv"),
              Map.entry("FundCurr", "EUR"),
              Map.entry("ISIN", ""),
              Map.entry("AssetName", "Overnight Deposit"),
              Map.entry("Quantity", "5000000"),
              Map.entry("AssetCurr", "EUR"),
              Map.entry("MarketValuePC", "5000000")),
          Map.ofEntries(
              Map.entry("ReportDate", "06.01.2026"),
              Map.entry("NAVDate", "05.01.2026"),
              Map.entry("Portfolio", "Tuleva Vabatahtlik Pensionifon"),
              Map.entry("AssetType", "Equities"),
              Map.entry("FundCurr", "EUR"),
              Map.entry("ISIN", "IE00BFNM3G45"),
              Map.entry("AssetName", "ISHARES USA ESG"),
              Map.entry("Quantity", "500000"),
              Map.entry("AssetCurr", "EUR"),
              Map.entry("MarketValuePC", "6000000")));

  private InvestmentReport createSwedbankReport(LocalDate date) {
    return InvestmentReport.builder()
        .provider(SWEDBANK)
        .reportType(POSITIONS)
        .reportDate(date)
        .rawData(SAMPLE_RAW_DATA)
        .metadata(Map.of())
        .createdAt(Instant.now())
        .build();
  }

  @Test
  void importForProviderAndDate_parsesAndSavesPositions() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(reportService.getReport(SWEDBANK, POSITIONS, date))
        .thenReturn(Optional.of(createSwedbankReport(date)));
    when(repository.existsByReportingDateAndFundAndAccountName(any(), any(), any()))
        .thenReturn(false);
    when(repository.findByReportingDateAndFundAndAccountType(any(), any(), any()))
        .thenReturn(List.of());

    job.importForProviderAndDate(SWEDBANK, date);

    verify(repository, times(3)).save(any(FundPosition.class));
  }

  @Test
  void importForProviderAndDate_skipsExistingPositions() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(reportService.getReport(SWEDBANK, POSITIONS, date))
        .thenReturn(Optional.of(createSwedbankReport(date)));
    when(repository.existsByReportingDateAndFundAndAccountName(
            LocalDate.of(2026, 1, 5), TUK75, "ISHARES DEV WLD ESG"))
        .thenReturn(true);
    when(repository.existsByReportingDateAndFundAndAccountName(
            LocalDate.of(2026, 1, 5), TUK75, "Overnight Deposit"))
        .thenReturn(false);
    when(repository.existsByReportingDateAndFundAndAccountName(
            LocalDate.of(2026, 1, 5), TUV100, "ISHARES USA ESG"))
        .thenReturn(false);
    when(repository.findByReportingDateAndFundAndAccountType(any(), any(), any()))
        .thenReturn(List.of());

    job.importForProviderAndDate(SWEDBANK, date);

    verify(repository, times(2)).save(any(FundPosition.class));
  }

  @Test
  void importForProviderAndDate_handlesNoReportInDatabase() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(reportService.getReport(SWEDBANK, POSITIONS, date)).thenReturn(Optional.empty());

    job.importForProviderAndDate(SWEDBANK, date);

    verify(repository, never()).save(any());
  }

  @Test
  void runImport_processesMultipleDaysForBothProviders() {
    when(reportService.getReport(any(), any(), any())).thenReturn(Optional.empty());

    job.runImport();

    verify(reportService, times(14)).getReport(any(), any(), any());
  }

  @Test
  void runImport_continuesOnError() {
    when(reportService.getReport(any(), any(), any())).thenThrow(new RuntimeException("DB error"));

    job.runImport();

    verify(reportService, times(14)).getReport(any(), any(), any());
  }

  @Test
  void runImport_processesBothProviders() {
    when(reportService.getReport(any(), any(), any())).thenReturn(Optional.empty());

    job.runImport();

    verify(reportService, times(7)).getReport(eq(SWEDBANK), eq(POSITIONS), any());
    verify(reportService, times(7)).getReport(eq(SEB), eq(POSITIONS), any());
  }

  @Test
  void importForProviderAndDate_recordsPositionsToLedgerForEachFund() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(reportService.getReport(SWEDBANK, POSITIONS, date))
        .thenReturn(Optional.of(createSwedbankReport(date)));
    when(repository.existsByReportingDateAndFundAndAccountName(any(), any(), any()))
        .thenReturn(false);
    when(repository.findByReportingDateAndFundAndAccountType(any(), any(), any()))
        .thenReturn(List.of());

    job.importForProviderAndDate(SWEDBANK, date);

    verify(navPositionLedger).recordPositions(eq("TUK75"), eq(date), anyMap(), any(), any(), any());
    verify(navPositionLedger)
        .recordPositions(eq("TUV100"), eq(date), anyMap(), any(), any(), any());
  }

  @Test
  void importForProviderAndDate_doesNotRecordToLedger_whenNoReportFound() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(reportService.getReport(SWEDBANK, POSITIONS, date)).thenReturn(Optional.empty());

    job.importForProviderAndDate(SWEDBANK, date);

    verify(navPositionLedger, never()).recordPositions(any(), any(), anyMap(), any(), any(), any());
  }

  @Test
  void importForProviderAndDate_recordsDeltaToLedger_notAbsoluteBalance() {
    LocalDate date = LocalDate.of(2026, 2, 1);
    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SWEDBANK)
            .reportType(POSITIONS)
            .reportDate(date)
            .rawData(
                List.of(
                    Map.ofEntries(
                        Map.entry("ReportDate", "01.02.2026"),
                        Map.entry("NAVDate", "01.02.2026"),
                        Map.entry("Portfolio", "Tuleva Maailma Aktsiate Pensionifond"),
                        Map.entry("AssetType", "Equities"),
                        Map.entry("FundCurr", "EUR"),
                        Map.entry("ISIN", "IE00BFG1TM61"),
                        Map.entry("AssetName", "ISHARES DEV WLD ESG"),
                        Map.entry("Quantity", "1000"),
                        Map.entry("AssetCurr", "EUR"),
                        Map.entry("MarketValuePC", "100000")),
                    Map.ofEntries(
                        Map.entry("ReportDate", "01.02.2026"),
                        Map.entry("NAVDate", "01.02.2026"),
                        Map.entry("Portfolio", "Tuleva Maailma Aktsiate Pensionifond"),
                        Map.entry("AssetType", "Cash & Cash Equiv"),
                        Map.entry("FundCurr", "EUR"),
                        Map.entry("ISIN", ""),
                        Map.entry("AssetName", "Overnight Deposit"),
                        Map.entry("Quantity", "50000"),
                        Map.entry("AssetCurr", "EUR"),
                        Map.entry("MarketValuePC", "50000"))))
            .metadata(Map.of())
            .createdAt(Instant.now())
            .build();

    when(reportService.getReport(SWEDBANK, POSITIONS, date)).thenReturn(Optional.of(report));
    when(repository.existsByReportingDateAndFundAndAccountName(any(), any(), any()))
        .thenReturn(false);

    when(repository.findByReportingDateAndFundAndAccountType(date, TUK75, AccountType.SECURITY))
        .thenReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUK75)
                    .reportingDate(date)
                    .accountType(AccountType.SECURITY)
                    .accountId("IE00BFG1TM61")
                    .quantity(new BigDecimal("1000"))
                    .marketValue(new BigDecimal("100000"))
                    .build()));

    when(repository.findByReportingDateAndFundAndAccountType(date, TUK75, CASH))
        .thenReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUK75)
                    .reportingDate(date)
                    .accountType(CASH)
                    .marketValue(new BigDecimal("50000"))
                    .build()));
    when(repository.findByReportingDateAndFundAndAccountType(date, TUK75, AccountType.RECEIVABLES))
        .thenReturn(List.of());
    when(repository.findByReportingDateAndFundAndAccountType(date, TUK75, AccountType.LIABILITY))
        .thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance(
            SECURITIES_UNITS.getAccountName("IE00BFG1TM61")))
        .thenReturn(new BigDecimal("900"));
    when(navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName()))
        .thenReturn(new BigDecimal("40000"));
    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName()))
        .thenReturn(ZERO);
    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName()))
        .thenReturn(ZERO);

    job.importForProviderAndDate(SWEDBANK, date);

    verify(navPositionLedger)
        .recordPositions(
            eq("TUK75"),
            eq(date),
            eq(Map.of("IE00BFG1TM61", new BigDecimal("100"))),
            eq(new BigDecimal("10000")),
            eq(ZERO),
            eq(ZERO));
  }
}
