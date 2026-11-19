package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtConfirmationAuditRecorder.FT_CONFIRMATION_VERIFIED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FtConfirmationImportJobIT {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-09T08:00:00Z"), ZoneOffset.UTC);
  private static final String KEY = "ft-confirmations/allocation-1.pdf";
  private static final String FT_ISIN = "IE000I9HGDZ3";
  private static final BigDecimal FT_QUANTITY = new BigDecimal("2500");
  private static final String DEDUP_KEY =
      TKF100.name() + "|IE00BFG1TM61|2026-01-15|2500|12.3456|NORMAL";

  @MockitoBean private S3Client s3Client;

  @Autowired private FtConfirmationPdfParser parser;
  @Autowired private FtConfirmationVerificationService verificationService;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private SebPendingTransactionReconciliationService reconciliationService;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private PositionPriceResolver positionPriceResolver;
  @Autowired private ReconciliationAuditRecorder reconciliationRecorder;
  @Autowired private FtConfirmationAuditRecorder ftAuditRecorder;
  @Autowired private FtConfirmationDigest digest;

  private FtConfirmationImportJob job;

  @BeforeEach
  void setUp() {
    FtConfirmationS3Source s3Source = new FtConfirmationS3Source(s3Client);
    job = new FtConfirmationImportJob(s3Source, parser, verificationService, FIXED_CLOCK);
  }

  @Test
  void run_recordsVerificationOutcomeForNewPdf_andSkipsDuplicateOnSecondRun() {
    stubOneConfirmationPdfUnderPrefix();

    job.run();

    List<TransactionAuditEvent> events =
        auditEventRepository.findByEventTypeAndDedupKey(FT_CONFIRMATION_VERIFIED, DEDUP_KEY);
    assertThat(events).hasSize(1);

    job.run();

    List<TransactionAuditEvent> eventsAfterSecondRun =
        auditEventRepository.findByEventTypeAndDedupKey(FT_CONFIRMATION_VERIFIED, DEDUP_KEY);
    assertThat(eventsAfterSecondRun).hasSize(1);
  }

  @Test
  void ftTradeSebListsWithoutOurRef_staysPendingUntilSebsNextDayReportIsReconciled_thenErrors() {
    UUID orderUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000001117");
    seedFtOrder(orderUuid);
    reconcileSebReport(LocalDate.of(2026, 11, 17), pendingRow(orderUuid, null));

    FtConfirmationResult beforeNextDayReport =
        verifyAt("2026-11-19T06:00:00Z", tuesdayConfirmation());

    assertThat(beforeNextDayReport.quantityStatus()).isEqualTo(PENDING_EXECUTION);
    assertThat(beforeNextDayReport.details()).containsEntry("awaitingSebReportAsOf", "2026-11-18");

    reconcileSebReport(LocalDate.of(2026, 11, 18), pendingRow(orderUuid, null));

    FtConfirmationResult afterNextDayReport =
        verifyAt("2026-11-19T09:00:00Z", tuesdayConfirmation());

    assertThat(afterNextDayReport.quantityStatus()).isEqualTo(ERROR);
    assertThat(afterNextDayReport.details()).containsEntry("sebReportAsOf", "2026-11-18");
  }

  @Test
  void ftTradeWhoseOurRefArrivesInSebsNextDayReport_getsItsExecution_andPasses() {
    UUID orderUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000002117");
    seedFtOrder(orderUuid);
    reconcileSebReport(LocalDate.of(2026, 11, 17), pendingRow(orderUuid, null));
    reconcileSebReport(LocalDate.of(2026, 11, 18), pendingRow(orderUuid, "DLA0000001"));

    FtConfirmationResult result = verifyAt("2026-11-19T09:00:00Z", tuesdayConfirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
  }

  @Test
  void noSebReportReconciledWithinTwoBusinessDays_errorsAsAnOverdueReport() {
    seedFtOrder(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000003117"));

    FtConfirmationResult result = verifyAt("2026-11-20T06:00:00Z", tuesdayConfirmation());

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
    assertThat(result.details()).containsEntry("sebReportMissingAsOf", "2026-11-18");
  }

  private void seedFtOrder(UUID orderUuid) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(TKF100)
            .instrumentIsin(FT_ISIN)
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(FT_QUANTITY)
            .orderVenue(OrderVenue.FT)
            .orderUuid(orderUuid)
            .orderStatus(SENT)
            .orderTimestamp(Instant.parse("2026-11-17T15:30:00Z"))
            .build());
  }

  private void reconcileSebReport(LocalDate asOfDate, Map<String, Object> row) {
    InvestmentReport report =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(asOfDate)
                .rawData(List.of(row))
                .metadata(Map.of("asOfDate", asOfDate.toString()))
                .createdAt(Instant.now())
                .build());
    reconciliationService.reconcile(report, () -> true);
  }

  private static Map<String, Object> pendingRow(UUID clientRef, String ourRef) {
    Map<String, Object> raw = new HashMap<>();
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Account", "VP68168");
    if (ourRef != null) {
      raw.put("Our ref", ourRef);
    }
    raw.put("ISIN", FT_ISIN);
    raw.put("Instrument name", "SYNTHETIC TEST SECURITY");
    raw.put("Currency", "EUR");
    raw.put("Quantity", FT_QUANTITY);
    raw.put("Price", new BigDecimal("10.00"));
    raw.put("Settlement amount", new BigDecimal("25000.00"));
    raw.put("Broker fee", new BigDecimal("0.00"));
    raw.put("Total", new BigDecimal("25000.00"));
    raw.put("Buy/Sell", "Buy");
    raw.put("Client ref", clientRef.toString());
    raw.put("Trade date", "2026-11-17T15:30:00Z");
    raw.put("Settlement date", "2026-11-19");
    return raw;
  }

  private static FtConfirmation tuesdayConfirmation() {
    return new FtConfirmation(
        TKF100, FT_ISIN, LocalDate.of(2026, 11, 17), FT_QUANTITY, new BigDecimal("10.00"));
  }

  private FtConfirmationResult verifyAt(String instant, FtConfirmation confirmation) {
    Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    return new FtConfirmationVerificationService(
            orderRepository,
            positionPriceResolver,
            new PriceValidator(),
            new FtQuantityCheck(
                executionRepository,
                new FtExecutionWait(reconciliationRecorder, new Target2Calendar(), clock)),
            ftAuditRecorder,
            digest)
        .verify(confirmation);
  }

  private void stubOneConfirmationPdfUnderPrefix() {
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key(KEY).build())
                .isTruncated(false)
                .build());

    byte[] pdf = confirmationPdf();
    var response =
        new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            AbortableInputStream.create(new ByteArrayInputStream(pdf)));
    given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(response);
  }

  private static byte[] confirmationPdf() {
    List<String> lines =
        List.of(
            "Trade Confirmation",
            "Dear Sir/Madam,",
            "We confirm the following trade .",
            "Allocation ID: TEST0001-00",
            "Counterparty: TULEVA FONDID AS",
            "Account: Tuleva Additional Investment Fund",
            "Trade Date Time: 20260115-09:32:44 UTC",
            "Trade Date: 20260115",
            "Settlement Date: 20260115",
            "Security Description: SYNTHETIC TEST SECURITY",
            "Bloomberg Ticker: TEST GY Equity",
            "ISIN: IE00BFG1TM61",
            "Your Direction: Buy",
            "Quantity: 2,500",
            "Gross Price: 12.345600 EUR",
            "Net Price: 12.345600 EUR",
            "Settlement Currency: EUR");
    return renderPdf(lines);
  }

  private static byte[] renderPdf(List<String> lines) {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(font, 10);
        cs.newLineAtOffset(50, 750);
        for (String line : lines) {
          cs.showText(line);
          cs.newLineAtOffset(0, -14);
        }
        cs.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
