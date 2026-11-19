package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtConfirmationAuditRecorder.FT_CONFIRMATION_VERIFIED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
  private static final String DEDUP_KEY =
      TKF100.name() + "|IE00BFG1TM61|2026-01-15|2500|12.3456|NORMAL";
  private static final String FIRST_CONFIRMATION_KEY =
      TKF100.name() + "|IE00BFG1TM61|2026-01-15|2500|12|NORMAL";
  private static final String CANCELLATION_KEY =
      TKF100.name() + "|IE00BFG1TM61|2026-01-15|2500|12|CANCELLATION";
  private static final String ACCOUNT = "Tuleva Additional Investment Fund";

  @MockitoBean private S3Client s3Client;

  @Autowired private FtConfirmationPdfParser parser;
  @Autowired private FtConfirmationVerificationService verificationService;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;

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
  void run_ftConfirmsATradeWhoseOrderWasCancelledInTheRegistry_confirmationIsAnOrphan() {
    registerOrder("2500", CANCELLED);
    stubOneConfirmationPdfUnderPrefix();

    job.run();

    assertThat(latestStatuses(DEDUP_KEY)).containsExactly("ORPHAN", "ORPHAN");
  }

  @Test
  void run_ftCancelledItsFirstConfirmationAndSentANewOne_noConfirmationIsLeftToActOn() {
    registerOrderExecutedInFull();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0003-00_new.pdf",
            confirmationPdf("Trade Confirmation", "TEST0003-00", "12.345600 EUR")));

    job.run();

    assertThat(latestStatuses(FIRST_CONFIRMATION_KEY)).containsExactly("VOIDED", "VOIDED");
    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("REPLACED", "REPLACED");
    assertThat(latestStatuses(DEDUP_KEY)).first().isEqualTo("OK");
  }

  @Test
  void run_ftCancelledAConfirmationAndSentNoNewOneByTheNextBusinessDay_cancellationNeedsAction() {
    registerOrderExecutedInFull();
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR")));

    job.run();

    assertThat(latestStatuses(FIRST_CONFIRMATION_KEY)).containsExactly("VOIDED", "VOIDED");
    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");
  }

  @Test
  void run_cancellationNeedingAction_clearsOnceTheOrderIsCancelledEvenWithAnotherTradeThatDay() {
    TransactionOrder order = registerOrder("2500", SENT);
    registerOrder("250", SENT);
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0004-00_other-trade.pdf",
            confirmationPdf("Trade Confirmation", "TEST0004-00", ACCOUNT, "250", "12.3456 EUR")));
    job.run();
    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");

    order.setOrderStatus(CANCELLED);
    orderRepository.save(order);
    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY))
        .containsExactly("NOTHING_TO_CANCEL", "NOTHING_TO_CANCEL");
  }

  @Test
  void run_cancellationOfTheQuantityOfACancelledOrder_alertsWhileALiveOrderHasNoConfirmation() {
    registerOrder("2500", CANCELLED);
    registerOrder("2600", SENT);
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");
  }

  @Test
  void run_newConfirmationOfAnotherOrderWithinOneUnit_doesNotReplaceTheCancelledTrade() {
    registerOrder("2500", SENT);
    registerOrder("2503", SENT);
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType(FT_CONFIRMATION_VERIFIED)
            .actor(FtConfirmationImportJob.JOB_ACTOR)
            .dedupKey(TKF100.name() + "|IE00BFG1TM61|2026-01-15|2501|12|CANCELLATION")
            .payload(
                Map.of(
                    "quantityStatus", "PENDING_REPLACEMENT", "priceStatus", "PENDING_REPLACEMENT"))
            .createdAt(Instant.parse("2026-01-15T16:05:00Z"))
            .build());
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", ACCOUNT, "2,501", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", ACCOUNT, "2,501", "12.000000 EUR"),
            "ft-confirmations/TEST0006-00_other-order.pdf",
            confirmationPdf("Trade Confirmation", "TEST0006-00", ACCOUNT, "2,502", "12.3456 EUR")));

    job.run();

    assertThat(latestStatuses(TKF100.name() + "|IE00BFG1TM61|2026-01-15|2501|12|CANCELLATION"))
        .containsExactly("CANCELLED", "CANCELLED");
  }

  @Test
  void run_ftWritesTheCancellationsAccountInCapitals_itStillVoidsTheConfirmationItCancels() {
    registerOrderExecutedInFull();
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf(
                "Trade Cancellation",
                "TEST0002-00",
                ACCOUNT.toUpperCase(Locale.ROOT),
                "2,500",
                "12.000000 EUR")));

    job.run();

    assertThat(latestStatuses(FIRST_CONFIRMATION_KEY)).containsExactly("VOIDED", "VOIDED");
    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");
  }

  @Test
  void run_anotherTradeThatDayInTwoEmailsWithDifferentDecimals_doesNotCoverTheCancelledOrder() {
    registerOrderExecutedInFull();
    registerOrder("250", SENT);
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0004-00_first-email.pdf",
            confirmationPdf("Trade Confirmation", "TEST0004-00", ACCOUNT, "250", "12.31 EUR"),
            "ft-confirmations/TEST0004-00_second-email.pdf",
            confirmationPdf(
                "Trade Confirmation", "TEST0004-00", ACCOUNT, "250.00", "12.310000 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");
  }

  @Test
  void run_ftCancelledAConfirmationToday_waitsForItsNewConfirmationUntilTheNextBusinessDay() {
    registerOrderExecutedInFull();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY))
        .containsExactly("PENDING_REPLACEMENT", "PENDING_REPLACEMENT");
  }

  @Test
  void run_anotherTradeThatDayConfirmedTwiceAtDifferentPrices_doesNotReplaceTheCancelledTrade() {
    registerOrderExecutedInFull();
    registerOrder("250", SENT);
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0004-00_first-email.pdf",
            confirmationPdf("Trade Confirmation", "TEST0004-00", ACCOUNT, "250", "12.3456 EUR"),
            "ft-confirmations/TEST0005-00_second-email.pdf",
            confirmationPdf("Trade Confirmation", "TEST0005-00", ACCOUNT, "250", "12.3457 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");
  }

  private List<String> latestStatuses(String dedupKey) {
    Map<String, Object> payload =
        auditEventRepository.findByEventTypeAndDedupKey(FT_CONFIRMATION_VERIFIED, dedupKey).stream()
            .max(comparing(TransactionAuditEvent::getId))
            .orElseThrow()
            .getPayload();
    return List.of(
        String.valueOf(payload.get("quantityStatus")), String.valueOf(payload.get("priceStatus")));
  }

  private void cancellationFirstSeenOnTheTradeDay() {
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType(FT_CONFIRMATION_VERIFIED)
            .actor(FtConfirmationImportJob.JOB_ACTOR)
            .dedupKey(CANCELLATION_KEY)
            .payload(
                Map.of(
                    "quantityStatus", "PENDING_REPLACEMENT", "priceStatus", "PENDING_REPLACEMENT"))
            .createdAt(Instant.parse("2026-01-15T16:05:00Z"))
            .build());
  }

  private TransactionOrder registerOrder(String quantity, OrderStatus status) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(TKF100)
            .instrumentIsin("IE00BFG1TM61")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal(quantity))
            .orderVenue(OrderVenue.FT)
            .orderStatus(status)
            .orderUuid(UUID.randomUUID())
            .orderTimestamp(Instant.parse("2026-01-15T09:00:00Z"))
            .build());
  }

  private void registerOrderExecutedInFull() {
    TransactionOrder order = registerOrder("2500", EXECUTED);
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA0000009")
            .executionTimestamp(Instant.parse("2026-01-15T15:30:00Z"))
            .executedQuantity(new BigDecimal("2500"))
            .unitPrice(new BigDecimal("12.3456"))
            .source("SEB_OOTEL")
            .build());
  }

  private void s3Holds(Map<String, byte[]> pdfsByKey) {
    Map<String, byte[]> pdfs = new LinkedHashMap<>(pdfsByKey);
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(
            ListObjectsV2Response.builder()
                .contents(
                    pdfs.keySet().stream().map(key -> S3Object.builder().key(key).build()).toList())
                .isTruncated(false)
                .build());
    given(s3Client.getObject(any(GetObjectRequest.class)))
        .willAnswer(
            invocation ->
                new ResponseInputStream<>(
                    GetObjectResponse.builder().build(),
                    AbortableInputStream.create(
                        new ByteArrayInputStream(
                            pdfs.get(invocation.<GetObjectRequest>getArgument(0).key())))));
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
    return confirmationPdf("Trade Confirmation", "TEST0001-00", "12.345600 EUR");
  }

  private static byte[] confirmationPdf(String title, String allocationId, String grossPrice) {
    return confirmationPdf(title, allocationId, ACCOUNT, "2,500", grossPrice);
  }

  private static byte[] confirmationPdf(
      String title, String allocationId, String account, String quantity, String grossPrice) {
    List<String> lines =
        List.of(
            title,
            "Dear Sir/Madam,",
            "We confirm the following trade .",
            "Allocation ID: " + allocationId,
            "Counterparty: TULEVA FONDID AS",
            "Account: " + account,
            "Trade Date Time: 20260115-09:32:44 UTC",
            "Trade Date: 20260115",
            "Settlement Date: 20260115",
            "Security Description: SYNTHETIC TEST SECURITY",
            "Bloomberg Ticker: TEST GY Equity",
            "ISIN: IE00BFG1TM61",
            "Your Direction: Buy",
            "Quantity: " + quantity,
            "Gross Price: " + grossPrice,
            "Net Price: " + grossPrice,
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
