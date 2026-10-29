package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.SEB_FUND_CONFIRMATION_NAV_TOLERANCE;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmation.SEB_TIME_ZONE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.FAR_FROM_NAV;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.NOT_PUBLISHED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.NOT_PUBLISHED_YET;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.WITHIN_TOLERANCE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.MATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.NOT_FOUND;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.PENDING;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.LATE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.ON_TIME;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus;
import ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class SebFundConfirmationVerificationService {

  private static final int DAYS_A_SETTLED_TRADE_CAN_STILL_REACH_THE_REPORT = 5;
  private static final int DAYS_A_PUBLISHED_NAV_CAN_STILL_REACH_THE_PRICE_DATA = 5;
  private static final int PERCENT_SCALE = 4;
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final InvestmentReportService reportService;
  private final SebPendingTransactionExtractor extractor;
  private final PositionPriceResolver priceResolver;
  private final InvestmentParameterRepository parameterRepository;
  private final SebFundConfirmationRegistryCheck registryCheck;
  private final Clock clock;
  private final SebFundConfirmationProperties properties;

  SebFundConfirmationVerificationService(
      InvestmentReportService reportService,
      SebPendingTransactionExtractor extractor,
      PositionPriceResolver priceResolver,
      InvestmentParameterRepository parameterRepository,
      SebFundConfirmationRegistryCheck registryCheck,
      Clock clock,
      SebFundConfirmationProperties properties) {
    this.reportService = reportService;
    this.extractor = extractor;
    this.priceResolver = priceResolver;
    this.parameterRepository = parameterRepository;
    this.registryCheck = registryCheck;
    this.clock = clock;
    this.properties = properties;
  }

  SebFundConfirmationResult verify(SebFundConfirmation confirmation) {
    LocalDate receivedDate = confirmation.orderReceivedOn();
    Optional<SebPendingTransactionRow> row = latestReportRow(confirmation, receivedDate);
    List<String> differences = row.map(found -> differences(confirmation, found)).orElse(List.of());
    Optional<BigDecimal> nav = navOn(confirmation.isin(), confirmation.pricingDate());
    @Nullable BigDecimal deviation =
        nav.map(value -> deviationPercent(confirmation.price(), value)).orElse(null);
    int navDatesAfterReceipt =
        navDatesBetween(confirmation.isin(), receivedDate, confirmation.pricingDate());
    return new SebFundConfirmationResult(
        reportStatus(confirmation, row, differences),
        differences,
        registryCheck.compare(confirmation),
        navStatus(deviation, confirmation.pricingDate()),
        nav.orElse(null),
        deviation,
        navDatesAfterReceipt > properties.maxNavDatesAfterReceipt() ? LATE : ON_TIME,
        navDatesAfterReceipt);
  }

  private Optional<SebPendingTransactionRow> latestReportRow(
      SebFundConfirmation confirmation, LocalDate receivedDate) {
    LocalDate lastReportDate = lastReportDate(confirmation);
    Optional<SebPendingTransactionRow> latest = Optional.empty();
    for (LocalDate date = receivedDate; !date.isAfter(lastReportDate); date = date.plusDays(1)) {
      Optional<SebPendingTransactionRow> found =
          reportService
              .getReport(SEB, PENDING_TRANSACTIONS, date)
              .flatMap(
                  report ->
                      extractor.extract(report).stream()
                          .filter(
                              candidate -> confirmation.tradeReference().equals(candidate.ourRef()))
                          .findFirst());
      if (found.isPresent()) {
        latest = found;
      }
    }
    return latest;
  }

  private LocalDate lastReportDate(SebFundConfirmation confirmation) {
    LocalDate settledTradeHorizon =
        confirmation.valueDate().plusDays(DAYS_A_SETTLED_TRADE_CAN_STILL_REACH_THE_REPORT);
    LocalDate today = today();
    return today.isBefore(settledTradeHorizon) ? today : settledTradeHorizon;
  }

  private ReportStatus reportStatus(
      SebFundConfirmation confirmation,
      Optional<SebPendingTransactionRow> row,
      List<String> differences) {
    if (row.isPresent()) {
      return differences.isEmpty() ? MATCH : MISMATCH;
    }
    return today()
            .isAfter(
                confirmation.valueDate().plusDays(DAYS_A_SETTLED_TRADE_CAN_STILL_REACH_THE_REPORT))
        ? NOT_FOUND
        : PENDING;
  }

  private static List<String> differences(
      SebFundConfirmation confirmation, SebPendingTransactionRow row) {
    return new SebFundConfirmationDifferences()
        .field("ISIN", confirmation.isin(), row.isin())
        .field("suund", confirmation.side(), row.side())
        .amount("kogus", confirmation.quantity(), row.quantity())
        .amount("hind", confirmation.price(), row.price())
        .amount("kogumaksumus", confirmation.totalAmount(), row.total())
        .field("väärtuspäev", confirmation.valueDate(), row.settlementDate())
        .list();
  }

  private Optional<BigDecimal> navOn(String isin, LocalDate date) {
    return priceResolver
        .resolve(isin, date)
        .filter(price -> price.validationStatus() == OK && date.equals(price.priceDate()))
        .map(price -> price.usedPrice());
  }

  private int navDatesBetween(String isin, LocalDate receivedDate, LocalDate pricingDate) {
    int count = 0;
    for (LocalDate date = receivedDate.plusDays(1);
        !date.isAfter(pricingDate);
        date = date.plusDays(1)) {
      if (navOn(isin, date).isPresent()) {
        count++;
      }
    }
    return count;
  }

  private static BigDecimal deviationPercent(BigDecimal price, BigDecimal nav) {
    return price
        .divide(nav, MathContext.DECIMAL64)
        .subtract(BigDecimal.ONE)
        .multiply(HUNDRED)
        .setScale(PERCENT_SCALE, HALF_UP);
  }

  private NavStatus navStatus(@Nullable BigDecimal deviation, LocalDate pricingDate) {
    if (deviation == null) {
      return today()
              .isAfter(pricingDate.plusDays(DAYS_A_PUBLISHED_NAV_CAN_STILL_REACH_THE_PRICE_DATA))
          ? NOT_PUBLISHED
          : NOT_PUBLISHED_YET;
    }
    return deviation.abs().compareTo(navTolerancePercent(pricingDate)) > 0
        ? FAR_FROM_NAV
        : WITHIN_TOLERANCE;
  }

  private BigDecimal navTolerancePercent(LocalDate pricingDate) {
    return parameterRepository
        .findLatestValue(SEB_FUND_CONFIRMATION_NAV_TOLERANCE, pricingDate)
        .multiply(HUNDRED);
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), SEB_TIME_ZONE);
  }
}
