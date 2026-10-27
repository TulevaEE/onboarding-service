package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
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
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SebFundConfirmationVerificationServiceTest {

  private static final String ISIN = "IE00BKPTWY98";
  private static final String REFERENCE = "DLA1234567";
  private static final LocalDate RECEIVED = LocalDate.of(2026, 1, 5);
  private static final LocalDate PRICING = LocalDate.of(2026, 1, 6);
  private static final LocalDate VALUE = LocalDate.of(2026, 1, 9);

  @Mock private InvestmentReportService reportService;
  @Mock private PositionPriceResolver priceResolver;

  private SebFundConfirmationVerificationService service;

  @BeforeEach
  void setUp() {
    service = serviceOn(LocalDate.of(2026, 1, 8));
    given(reportService.getReport(eq(SEB), eq(PENDING_TRANSACTIONS), any()))
        .willReturn(Optional.empty());
    given(priceResolver.resolve(eq(ISIN), any())).willReturn(Optional.of(noPrice()));
  }

  @Test
  void verify_isCleanWhenTheReportRowAgreesAndThePriceIsTheNavOfTheNextNavDate() {
    reportOn(PRICING, pendingRow("1000", "12.34567890", "12345.68", VALUE));
    navOn(PRICING, "12.3456");

    SebFundConfirmationResult result = service.verify(confirmation("12.345678901"));

    assertThat(result)
        .isEqualTo(
            new SebFundConfirmationResult(
                MATCH,
                List.of(),
                WITHIN_TOLERANCE,
                new BigDecimal("12.3456"),
                new BigDecimal("0.0006"),
                ON_TIME,
                1));
    assertThat(result.isActionable()).isFalse();
  }

  @Test
  void verify_listsEveryFieldThatDiffersFromTheReportRow() {
    reportOn(PRICING, pendingRow("999", "12.34567890", "12000.00", VALUE.plusDays(1)));
    navOn(PRICING, "12.3456");

    SebFundConfirmationResult result = service.verify(confirmation("12.345678901"));

    assertThat(result.reportStatus()).isEqualTo(MISMATCH);
    assertThat(result.reportDifferences())
        .containsExactly(
            "kogus 1000.00 vs 999",
            "kogumaksumus 12345.68 vs 12000.00",
            "väärtuspäev 2026-01-09 vs 2026-01-10");
    assertThat(result.isActionable()).isTrue();
  }

  @Test
  void verify_readsTheLatestReportThatStillHoldsTheTrade() {
    reportOn(PRICING, pendingRow("1000", "12.34567890", "12345.68", VALUE.plusDays(1)));
    reportOn(PRICING.plusDays(1), pendingRow("1000", "12.34567890", "12345.68", VALUE));
    navOn(PRICING, "12.3456");

    assertThat(service.verify(confirmation("12.345678901")).reportStatus()).isEqualTo(MATCH);
  }

  @Test
  void verify_waitsForTheReportWhileTheTradeCanStillAppearInIt() {
    navOn(PRICING, "12.3456");

    SebFundConfirmationResult result = service.verify(confirmation("12.345678901"));

    assertThat(result.reportStatus()).isEqualTo(PENDING);
    assertThat(result.isActionable()).isFalse();
  }

  @Test
  void verify_reportsATradeTheReportNeverHeldOnceTheValueDateIsWellPast() {
    service = serviceOn(VALUE.plusDays(6));
    navOn(PRICING, "12.3456");

    SebFundConfirmationResult result = service.verify(confirmation("12.345678901"));

    assertThat(result.reportStatus()).isEqualTo(NOT_FOUND);
    assertThat(result.isActionable()).isTrue();
  }

  @Test
  void verify_flagsAPriceFurtherFromTheNavThanTheTolerance() {
    reportOn(PRICING, pendingRow("1000", "12.40000000", "12345.68", VALUE));
    navOn(PRICING, "12.3000");

    SebFundConfirmationResult result = service.verify(confirmation("12.4"));

    assertThat(result.navStatus()).isEqualTo(FAR_FROM_NAV);
    assertThat(result.deviationPercent()).isEqualByComparingTo("0.8130");
    assertThat(result.isActionable()).isTrue();
  }

  @Test
  void verify_doesNotJudgeThePriceWhileTheNavForThePricingDateCanStillArrive() {
    reportOn(PRICING, pendingRow("1000", "12.34567890", "12345.68", VALUE));
    navOn(PRICING.minusDays(1), "11.0000");

    SebFundConfirmationResult result = service.verify(confirmation("12.345678901"));

    assertThat(result.navStatus()).isEqualTo(NOT_PUBLISHED_YET);
    assertThat(result.nav()).isNull();
    assertThat(result.isActionable()).isFalse();
  }

  @Test
  void verify_saysThePriceCouldNotBeCheckedOnceTheNavIsOverdueUntilItArrives() {
    reportOn(PRICING, pendingRow("1000", "12.34567890", "12345.68", VALUE));
    navOn(PRICING.minusDays(1), "11.0000");

    SebFundConfirmationResult result =
        serviceOn(PRICING.plusDays(6)).verify(confirmation("12.345678901"));

    assertThat(result.navStatus()).isEqualTo(NOT_PUBLISHED);
    assertThat(result.isActionable()).isTrue();
  }

  @Test
  void verify_flagsAPricingDateMoreThanTwoNavDatesAfterTheOrderWasReceived() {
    LocalDate latePricing = LocalDate.of(2026, 1, 12);
    reportOn(latePricing, pendingRow("1000", "12.34567890", "12345.68", VALUE));
    navOn(LocalDate.of(2026, 1, 6), "12.0000");
    navOn(LocalDate.of(2026, 1, 7), "12.1000");
    navOn(LocalDate.of(2026, 1, 8), "12.2000");
    navOn(latePricing, "12.3456");

    SebFundConfirmationResult result = service.verify(confirmation("12.345678901", latePricing));

    assertThat(result.timingStatus()).isEqualTo(LATE);
    assertThat(result.navDatesAfterReceipt()).isEqualTo(4);
    assertThat(result.isActionable()).isTrue();
  }

  private SebFundConfirmationVerificationService serviceOn(LocalDate today) {
    return new SebFundConfirmationVerificationService(
        reportService,
        new SebPendingTransactionExtractor(),
        priceResolver,
        Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        new SebFundConfirmationProperties(new BigDecimal("0.5"), 2, 2, LocalDate.of(2026, 1, 5)));
  }

  private static SebFundConfirmation confirmation(String price) {
    return confirmation(price, PRICING);
  }

  private static SebFundConfirmation confirmation(String price, LocalDate pricingDate) {
    return new SebFundConfirmation(
        TUK75,
        REFERENCE,
        Instant.parse("2026-01-05T09:48:17Z"),
        pricingDate,
        VALUE,
        ISIN,
        BUY,
        new BigDecimal("1000.00"),
        new BigDecimal(price),
        new BigDecimal("12345.68"),
        new BigDecimal("0.00"),
        new BigDecimal("12345.68"));
  }

  private void reportOn(LocalDate reportDate, Map<String, Object> row) {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, reportDate))
        .willReturn(
            Optional.of(
                InvestmentReport.builder()
                    .provider(SEB)
                    .reportType(PENDING_TRANSACTIONS)
                    .reportDate(reportDate)
                    .rawData(List.of(row))
                    .build()));
  }

  private static Map<String, Object> pendingRow(
      String quantity, String price, String total, LocalDate settlementDate) {
    Map<String, Object> row = new HashMap<>();
    row.put("Our ref", REFERENCE);
    row.put("ISIN", ISIN);
    row.put("Quantity", quantity);
    row.put("Price", price);
    row.put("Total", total);
    row.put("Buy/Sell", "BUY");
    row.put("Trade date", RECEIVED.atTime(14, 48).toInstant(ZoneOffset.UTC).toString());
    row.put("Settlement date", settlementDate.toString());
    row.put("Client name", "Tuleva Maailma Aktsiate Pensionifond");
    return row;
  }

  private void navOn(LocalDate date, String nav) {
    given(priceResolver.resolve(ISIN, date))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal(nav))
                    .validationStatus(OK)
                    .priceDate(date)
                    .storageKey(ISIN + ".BLACKROCK")
                    .build()));
  }

  private static ResolvedPrice noPrice() {
    return ResolvedPrice.builder().validationStatus(NO_PRICE_DATA).build();
  }
}
