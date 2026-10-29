package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmation.SEB_TIME_ZONE;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingTransactionExtractor.ExtractionResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class SebFundConfirmationMissingFinder {

  private static final int REPORT_LOOKBACK_DAYS = 14;

  private final InvestmentReportService reportService;
  private final SebPendingTransactionExtractor extractor;
  private final InstrumentReferenceService instrumentReferenceService;
  private final SebClientNameToFundResolver fundResolver;
  private final Target2Calendar calendar;
  private final Clock clock;
  private final SebFundConfirmationProperties properties;

  SebFundConfirmationMissingFinder(
      InvestmentReportService reportService,
      SebPendingTransactionExtractor extractor,
      InstrumentReferenceService instrumentReferenceService,
      SebClientNameToFundResolver fundResolver,
      Target2Calendar calendar,
      Clock clock,
      SebFundConfirmationProperties properties) {
    this.reportService = reportService;
    this.extractor = extractor;
    this.instrumentReferenceService = instrumentReferenceService;
    this.fundResolver = fundResolver;
    this.calendar = calendar;
    this.clock = clock;
    this.properties = properties;
  }

  SebFundConfirmationMissingScan find(Set<String> confirmedReferences) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), SEB_TIME_ZONE);
    LocalDate lastTradeDateDue =
        calendar.subtractBusinessDays(today, properties.businessDaysToWait());
    Map<String, MissingSebFundConfirmation> missing = new LinkedHashMap<>();
    List<LocalDate> incompleteReportDates = new ArrayList<>();
    for (LocalDate date = today.minusDays(REPORT_LOOKBACK_DAYS);
        !date.isAfter(today);
        date = date.plusDays(1)) {
      Optional<InvestmentReport> report = reportService.getReport(SEB, PENDING_TRANSACTIONS, date);
      if (report.isEmpty()) {
        continue;
      }
      ExtractionResult extraction = extractor.extractWithDiagnostics(report.get());
      if (!extraction.isComplete()) {
        incompleteReportDates.add(date);
      }
      extraction.rows().stream()
          .map(this::missingConfirmation)
          .flatMap(Optional::stream)
          .filter(trade -> !confirmedReferences.contains(trade.tradeReference()))
          .filter(trade -> !trade.tradeDate().isBefore(properties.missingConfirmationsFrom()))
          .filter(trade -> !trade.tradeDate().isAfter(lastTradeDateDue))
          .forEach(trade -> missing.put(trade.tradeReference(), trade));
    }
    return new SebFundConfirmationMissingScan(
        missing.values().stream().sorted(comparing(MissingSebFundConfirmation::tradeDate)).toList(),
        List.copyOf(incompleteReportDates));
  }

  private Optional<MissingSebFundConfirmation> missingConfirmation(SebPendingTransactionRow row) {
    String reference = row.ourRef();
    String isin = row.isin();
    TransactionType side = row.side();
    Instant tradedAt = row.tradeDate();
    BigDecimal quantity = row.quantity();
    if (reference == null
        || isin == null
        || side == null
        || tradedAt == null
        || quantity == null
        || !isMutualFund(isin)) {
      return Optional.empty();
    }
    return fundResolver
        .resolve(row.clientName())
        .map(
            fund ->
                new MissingSebFundConfirmation(
                    reference,
                    fund,
                    isin,
                    side,
                    tradedAt.atZone(SEB_TIME_ZONE).toLocalDate(),
                    quantity));
  }

  private boolean isMutualFund(String isin) {
    return instrumentReferenceService
        .findByIsin(isin)
        .map(InstrumentReference::getInstrumentType)
        .filter(InstrumentType.FUND.name()::equals)
        .isPresent();
  }
}
