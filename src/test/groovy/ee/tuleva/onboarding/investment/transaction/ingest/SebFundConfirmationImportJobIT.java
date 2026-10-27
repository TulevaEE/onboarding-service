package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.ALERTED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.MISSING_ALERTED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.VERIFIED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.buyFields;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.confirmation;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.liberationSansTable;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@DataJpaTest
@Import(SebFundConfirmationSlice.class)
class SebFundConfirmationImportJobIT {

  private static final String ISIN = "IE00BKPTWY98";
  private static final String NAV_STORAGE_KEY = ISIN + ".BLACKROCK";
  private static final Instant NOW = Instant.parse("2026-01-08T10:00:00Z");
  private static final SebFundConfirmationProperties PROPERTIES =
      new SebFundConfirmationProperties(new BigDecimal("0.5"), 2, 2, LocalDate.of(2026, 1, 5));

  @Autowired private InvestmentReportService reportService;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private SebPendingTransactionExtractor extractor;
  @Autowired private PositionPriceResolver priceResolver;
  @Autowired private InstrumentReferenceService instrumentReferenceService;
  @Autowired private SebClientNameToFundResolver fundResolver;
  @Autowired private Target2Calendar calendar;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private JdbcClient jdbcClient;

  private final S3Client s3Client = mock(S3Client.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);

  private SebFundConfirmationImportJob job;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    SebFundConfirmationAuditRecorder auditRecorder =
        new SebFundConfirmationAuditRecorder(auditEventRepository, clock);
    job =
        new SebFundConfirmationImportJob(
            new SebFundConfirmationS3Source(s3Client, clock),
            new SebFundConfirmationPdfParser(fundResolver, liberationSansTable()),
            new SebFundConfirmationVerificationService(
                reportService, extractor, priceResolver, clock, PROPERTIES),
            new SebFundConfirmationMissingFinder(
                reportService,
                extractor,
                instrumentReferenceService,
                fundResolver,
                calendar,
                clock,
                PROPERTIES),
            auditRecorder,
            new SebFundConfirmationDigest(notificationService, auditRecorder, PROPERTIES));

    jdbcClient
        .sql("DELETE FROM index_values WHERE key = :key")
        .param("key", NAV_STORAGE_KEY)
        .update();
    s3Holds("seb-confirmations/DLA0000001-001.pdf", confirmation(fieldsForFirstTrade()));
  }

  @Test
  void run_alertsOnAPriceFarFromTheNavAndOnAFundTradeWithoutAConfirmation() {
    seedTwoTradesAndANavBelowTheConfirmedPrice();

    job.run();

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            TUK75 IE00BKPTWY98 ost 06.01.2026: hind 12.4 erineb fondi NAV-ist 12.3 +0.81% (piir 0.5%)
            Tehing DLA0000001, SEB sai korralduse 05.01.2026 11:48
            Kontrolli hinda SEB-lt ja fondivalitseja lehelt

            TUK75 IE00BKPTWY98 ost 05.01.2026: SEB tehingukinnitust pole 2 tööpäeva järel
            Tehing DLA0000002 on SEB ootel tehingute raportis, kogus 1000
            Kontrolli funds@ postkasti ja küsi kinnitust SEB-lt""",
            INVESTMENT);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(VERIFIED, "DLA0000001"))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getPayload())
                    .containsEntry("pricingDate", "2026-01-06")
                    .containsEntry("orderReceivedAt", "2026-01-05T09:48:17Z")
                    .containsEntry("nav", "12.30000")
                    .containsEntry("deviationPercent", "0.8130"));
  }

  @Test
  void run_doesNotRepeatAlertsItHasAlreadySent() {
    seedTwoTradesAndANavBelowTheConfirmedPrice();

    job.run();
    job.run();

    then(notificationService).should(times(1)).sendMessage(any(), any());
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(VERIFIED, "DLA0000001")).hasSize(1);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(ALERTED, "DLA0000001")).hasSize(1);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(MISSING_ALERTED, "DLA0000002"))
        .hasSize(1);
  }

  @Test
  void run_doesNotRepeatAPriceAlertWhenTheReportRowArrivesLater() {
    insertNav(LocalDate.of(2026, 1, 6), "12.3000");

    job.run();
    saveReport(LocalDate.of(2026, 1, 7), List.of(pendingRow("DLA0000001", "12.40000000")));
    job.run();

    then(notificationService).should(times(1)).sendMessage(any(), any());
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(VERIFIED, "DLA0000001"))
        .extracting(event -> event.getPayload().get("status"))
        .containsExactlyInAnyOrder(
            "[PENDING, FAR_FROM_NAV, ON_TIME]", "[MATCH, FAR_FROM_NAV, ON_TIME]");
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(ALERTED, "DLA0000001"))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getPayload()).containsEntry("findings", List.of("FAR_FROM_NAV")));
  }

  @Test
  void run_recordsAChangedResultEvenWhenItsStatusStaysTheSame() {
    seedTwoTradesAndANavBelowTheConfirmedPrice();

    job.run();
    jdbcClient
        .sql("UPDATE index_values SET value = :value WHERE key = :key AND date = :date")
        .param("value", new BigDecimal("12.2000"))
        .param("key", NAV_STORAGE_KEY)
        .param("date", LocalDate.of(2026, 1, 6))
        .update();
    job.run();

    assertThat(auditEventRepository.findByEventTypeAndDedupKey(VERIFIED, "DLA0000001"))
        .extracting(event -> event.getPayload().get("nav"))
        .containsExactlyInAnyOrder("12.30000", "12.20000");
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(ALERTED, "DLA0000001")).hasSize(1);
  }

  @Test
  void run_reportsAFundTradeWithoutAConfirmationEvenWhenNoPdfArrivesAtAll() {
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(ListObjectsV2Response.builder().isTruncated(false).build());
    saveReport(LocalDate.of(2026, 1, 7), List.of(pendingRow("DLA0000002", "12.30000000")));

    job.run();

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            TUK75 IE00BKPTWY98 ost 05.01.2026: SEB tehingukinnitust pole 2 tööpäeva järel
            Tehing DLA0000002 on SEB ootel tehingute raportis, kogus 1000
            Kontrolli funds@ postkasti ja küsi kinnitust SEB-lt""",
            INVESTMENT);
  }

  @Test
  void run_staysQuietWhenTheConfirmationAgreesWithTheReportAndTheNav() {
    insertNav(LocalDate.of(2026, 1, 6), "12.4000");
    saveReport(LocalDate.of(2026, 1, 7), List.of(pendingRow("DLA0000001", "12.40000000")));

    job.run();

    then(notificationService).shouldHaveNoInteractions();
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(VERIFIED, "DLA0000001"))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getPayload())
                    .containsEntry("status", "[MATCH, WITHIN_TOLERANCE, ON_TIME]"));
  }

  private void seedTwoTradesAndANavBelowTheConfirmedPrice() {
    insertNav(LocalDate.of(2026, 1, 6), "12.3000");
    saveReport(
        LocalDate.of(2026, 1, 7),
        List.of(pendingRow("DLA0000001", "12.40000000"), pendingRow("DLA0000002", "12.30000000")));
  }

  private static Map<String, List<String>> fieldsForFirstTrade() {
    Map<String, List<String>> fields = buyFields();
    fields.put("Tehingu nr", List.of("DLA0000001"));
    fields.put("Hind", List.of("12.4", "EUR"));
    fields.put("Kokkulepitud tehingusumma", List.of("12,400.00", "EUR"));
    fields.put("Kogumaksumus", List.of("12,400.00", "EUR"));
    return fields;
  }

  private void s3Holds(String key, byte[] pdf) {
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key(key).lastModified(NOW.minusSeconds(3600)).build())
                .isTruncated(false)
                .build());
    given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), pdf));
  }

  private void insertNav(LocalDate date, String value) {
    jdbcClient
        .sql(
            """
            INSERT INTO index_values (key, date, value, provider, updated_at)
            VALUES (:key, :date, :value, 'BLACKROCK', :updatedAt)
            """)
        .param("key", NAV_STORAGE_KEY)
        .param("date", date)
        .param("value", new BigDecimal(value))
        .param("updatedAt", NOW.minusSeconds(86_400))
        .update();
  }

  private void saveReport(LocalDate reportDate, List<Map<String, Object>> rows) {
    reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(reportDate)
            .rawData(rows)
            .metadata(Map.of("source", "fixture"))
            .createdAt(NOW)
            .build());
  }

  private static Map<String, Object> pendingRow(String reference, String price) {
    Map<String, Object> row = new HashMap<>();
    row.put("Our ref", reference);
    row.put("ISIN", ISIN);
    row.put("Quantity", "1000");
    row.put("Price", price);
    row.put("Total", "12400.00");
    row.put("Buy/Sell", "BUY");
    row.put("Trade date", "2026-01-05T12:48:00Z");
    row.put("Settlement date", "2026-01-09");
    row.put("Client name", "Tuleva Maailma Aktsiate Pensionifond");
    return row;
  }
}
