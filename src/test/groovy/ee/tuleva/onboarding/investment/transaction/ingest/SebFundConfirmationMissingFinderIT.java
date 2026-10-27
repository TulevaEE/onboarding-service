package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SebFundConfirmationSlice.class)
class SebFundConfirmationMissingFinderIT {

  private static final String FUND_ISIN = "IE00BKPTWY98";
  private static final String ETF_ISIN = "IE000F60HVH9";
  private static final LocalDate TODAY = LocalDate.of(2026, 1, 14);
  private static final LocalDate MISSING_CONFIRMATIONS_FROM = LocalDate.of(2026, 1, 5);

  @Autowired private InvestmentReportService reportService;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private SebPendingTransactionExtractor extractor;
  @Autowired private InstrumentReferenceService instrumentReferenceService;
  @Autowired private SebClientNameToFundResolver fundResolver;
  @Autowired private Target2Calendar calendar;

  private SebFundConfirmationMissingFinder finder;

  @BeforeEach
  void setUp() {
    finder =
        new SebFundConfirmationMissingFinder(
            reportService,
            extractor,
            instrumentReferenceService,
            fundResolver,
            calendar,
            Clock.fixed(TODAY.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
            new SebFundConfirmationProperties(
                new BigDecimal("0.5"), 2, 2, MISSING_CONFIRMATIONS_FROM));
  }

  @Test
  void find_reportsAFundTradeStillWithoutAConfirmationTwoBusinessDaysLater() {
    saveReport(LocalDate.of(2026, 1, 12), row("DLA0000001", FUND_ISIN, LocalDate.of(2026, 1, 9)));

    List<MissingSebFundConfirmation> missing = finder.find(Set.of()).missing();

    assertThat(missing)
        .containsExactly(
            new MissingSebFundConfirmation(
                "DLA0000001",
                TUK75,
                FUND_ISIN,
                BUY,
                LocalDate.of(2026, 1, 9),
                new BigDecimal("1000")));
  }

  @Test
  void find_clearsOnceTheConfirmationHasArrived() {
    saveReport(LocalDate.of(2026, 1, 12), row("DLA0000001", FUND_ISIN, LocalDate.of(2026, 1, 9)));

    assertThat(finder.find(Set.of("DLA0000001")).missing()).isEmpty();
  }

  @Test
  void find_waitsTwoBusinessDaysBeforeReportingATrade() {
    saveReport(TODAY, row("DLA0000002", FUND_ISIN, LocalDate.of(2026, 1, 13)));

    assertThat(finder.find(Set.of()).missing()).isEmpty();
  }

  @Test
  void find_ignoresEtfTradesBecauseOnlyMutualFundsGetSebConfirmations() {
    saveReport(LocalDate.of(2026, 1, 12), row("DLA0000003", ETF_ISIN, LocalDate.of(2026, 1, 9)));

    assertThat(finder.find(Set.of()).missing()).isEmpty();
  }

  @Test
  void find_ignoresTradesFromBeforeTheConfiguredStartOfTheCheck() {
    saveReport(LocalDate.of(2026, 1, 12), row("DLA0000004", FUND_ISIN, LocalDate.of(2026, 1, 2)));

    assertThat(finder.find(Set.of()).missing()).isEmpty();
  }

  @Test
  void find_reportsATradeOnceAlthoughEveryDailyReportRepeatsIt() {
    saveReport(LocalDate.of(2026, 1, 12), row("DLA0000001", FUND_ISIN, LocalDate.of(2026, 1, 9)));
    saveReport(LocalDate.of(2026, 1, 13), row("DLA0000001", FUND_ISIN, LocalDate.of(2026, 1, 9)));

    assertThat(finder.find(Set.of()).missing()).hasSize(1);
  }

  @Test
  void find_namesTheReportsWithRowsItCannotReadUntilTheyCanBeRead() {
    Map<String, Object> unreadable = row("DLA0000005", FUND_ISIN, LocalDate.of(2026, 1, 9));
    unreadable.put("Client ref", "not-a-uuid");
    saveReport(LocalDate.of(2026, 1, 12), unreadable);

    assertThat(finder.find(Set.of()))
        .isEqualTo(
            new SebFundConfirmationMissingScan(List.of(), List.of(LocalDate.of(2026, 1, 12))));
  }

  private void saveReport(LocalDate reportDate, Map<String, Object> row) {
    reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(reportDate)
            .rawData(List.of(row))
            .metadata(Map.of("source", "fixture"))
            .createdAt(Instant.parse("2026-01-14T08:00:00Z"))
            .build());
  }

  private static Map<String, Object> row(String reference, String isin, LocalDate tradeDate) {
    Map<String, Object> row = new HashMap<>();
    row.put("Our ref", reference);
    row.put("ISIN", isin);
    row.put("Quantity", "1000");
    row.put("Price", "12.3456");
    row.put("Total", "12345.60");
    row.put("Buy/Sell", "BUY");
    row.put("Trade date", tradeDate.atTime(12, 0).toInstant(ZoneOffset.UTC).toString());
    row.put("Settlement date", tradeDate.plusDays(4).toString());
    row.put("Client name", "Tuleva Maailma Aktsiate Pensionifond");
    return row;
  }
}
