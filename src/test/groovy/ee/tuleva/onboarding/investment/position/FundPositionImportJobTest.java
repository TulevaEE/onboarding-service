package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.time.Clock;
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
  @Mock private FundPositionLedgerService fundPositionLedgerService;

  private SwedbankFundPositionParser swedbankParser;
  private SebFundPositionParser sebParser;
  private FundPositionImportService importService;
  private FundPositionImportJob job;

  @BeforeEach
  void setUp() {
    swedbankParser = new SwedbankFundPositionParser(Clock.systemUTC());
    sebParser = new SebFundPositionParser(Clock.systemUTC());
    importService = new FundPositionImportService(repository);
    job =
        new FundPositionImportJob(
            swedbankParser, sebParser, importService, reportService, fundPositionLedgerService);
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
    when(repository.existsByNavDateAndFundAndAccountName(any(), any(), any())).thenReturn(false);

    job.importForProviderAndDate(SWEDBANK, date);

    verify(repository, times(3)).save(any(FundPosition.class));
  }

  @Test
  void importForProviderAndDate_skipsExistingPositions() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(reportService.getReport(SWEDBANK, POSITIONS, date))
        .thenReturn(Optional.of(createSwedbankReport(date)));
    when(repository.existsByNavDateAndFundAndAccountName(
            LocalDate.of(2026, 1, 5), TUK75, "ISHARES DEV WLD ESG"))
        .thenReturn(true);
    when(repository.existsByNavDateAndFundAndAccountName(
            LocalDate.of(2026, 1, 5), TUK75, "Overnight Deposit"))
        .thenReturn(false);
    when(repository.existsByNavDateAndFundAndAccountName(
            LocalDate.of(2026, 1, 5), TUV100, "ISHARES USA ESG"))
        .thenReturn(false);

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

    verify(reportService, times(60)).getReport(any(), any(), any());
  }

  @Test
  void runImport_continuesOnError() {
    when(reportService.getReport(any(), any(), any())).thenThrow(new RuntimeException("DB error"));

    job.runImport();

    verify(reportService, times(60)).getReport(any(), any(), any());
  }

  @Test
  void runImport_processesBothProviders() {
    when(reportService.getReport(any(), any(), any())).thenReturn(Optional.empty());

    job.runImport();

    verify(reportService, times(30)).getReport(eq(SWEDBANK), eq(POSITIONS), any());
    verify(reportService, times(30)).getReport(eq(SEB), eq(POSITIONS), any());
  }

  @Test
  void importForProviderAndDate_onlyRecordsToLedgerForNavCalculationFunds() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(reportService.getReport(SWEDBANK, POSITIONS, date))
        .thenReturn(Optional.of(createSwedbankReport(date)));
    when(repository.existsByNavDateAndFundAndAccountName(any(), any(), any())).thenReturn(false);

    job.importForProviderAndDate(SWEDBANK, date);

    verify(fundPositionLedgerService, never())
        .recordPositionsToLedger(any(TulevaFund.class), any());
  }

  @Test
  void importForProviderAndDate_doesNotRecordToLedger_whenNoReportFound() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(reportService.getReport(SWEDBANK, POSITIONS, date)).thenReturn(Optional.empty());

    job.importForProviderAndDate(SWEDBANK, date);

    verify(fundPositionLedgerService, never())
        .recordPositionsToLedger(any(TulevaFund.class), any());
  }
}
