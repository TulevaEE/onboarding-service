package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtConfirmationAuditRecorder.FT_CONFIRMATION_VERIFIED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "transaction-registry.ft-confirmation.alerts-from=2026-01-15",
      "transaction-registry.ft-confirmation.registry-authoritative=true",
      "transaction-registry.ft-confirmation.unconfirmed-lookback-days=36500"
    })
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
  private static final LocalDate ALERTS_FROM = LocalDate.of(2026, 1, 15);

  @MockitoBean private S3Client s3Client;
  @MockitoBean private OperationsNotificationService notificationService;

  @Autowired private FtConfirmationPdfParser parser;
  @Autowired private FtConfirmationVerificationService verificationService;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private FtUnconfirmedTradeCheck unconfirmedTradeCheck;
  @Autowired private FtConfirmationDigest digest;

  private FtConfirmationImportJob job;

  @BeforeEach
  void setUp() {
    FtConfirmationS3Source s3Source = new FtConfirmationS3Source(s3Client);
    job =
        new FtConfirmationImportJob(
            s3Source,
            parser,
            verificationService,
            unconfirmedTradeCheck,
            new FtUnreadableConfirmationWarning(s3Source, digest, ALERTS_FROM),
            FIXED_CLOCK);
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
  void run_twoConfirmationsOfOneOrder_doNotReplaceACancellationThatCouldBeEitherOrder() {
    registerOrder("2500", SENT);
    registerOrder("2502", SENT);
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
            "ft-confirmations/TEST0007-00_once.pdf",
            confirmationPdf("Trade Confirmation", "TEST0007-00", ACCOUNT, "2,500", "12.3456 EUR"),
            "ft-confirmations/TEST0008-00_again.pdf",
            confirmationPdf("Trade Confirmation", "TEST0008-00", ACCOUNT, "2,500", "12.3457 EUR")));

    job.run();

    assertThat(latestStatuses(TKF100.name() + "|IE00BFG1TM61|2026-01-15|2501|12|CANCELLATION"))
        .containsExactly("AMBIGUOUS", "AMBIGUOUS");
  }

  @Test
  void run_cancelledQuantityCollidingWithAnotherOrder_isNotReplacedByThatOrdersConfirmation() {
    registerOrder("1000", SENT);
    registerOrder("2500", SENT);
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0009-00_other-order.pdf",
            confirmationPdf("Trade Confirmation", "TEST0009-00", "12.3456 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");
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

  @Test
  void run_noClosingPriceForTheTradeDateByTheEndOfTheNextBusinessDay_priceCannotBeChecked() {
    registerOrderExecutedInFull();
    stubOneConfirmationPdfUnderPrefix();

    job.run();

    assertThat(latestStatuses(DEDUP_KEY)).containsExactly("OK", "PRICE_MISSING");
  }

  @Test
  void run_priceThatCouldNotBeChecked_clearsOnceTheClosingPriceOfTheTradeDateArrives() {
    registerOrderExecutedInFull();
    stubOneConfirmationPdfUnderPrefix();
    job.run();

    closingPriceArrives("12.3456");
    job.run();

    assertThat(latestStatuses(DEDUP_KEY)).containsExactly("OK", "OK");
  }

  @Test
  void run_sebExecutedAnFtTradeFtNeverConfirmed_tradeIsUnconfirmedWithItsSebPriceChecked() {
    TransactionOrder order = registerOrderExecutedInFull();
    closingPriceArrives("12.3456");
    s3Holds(Map.of());

    job.run();

    assertThat(latestStatuses(tradeKey(order))).containsExactly("UNCONFIRMED", "OK");
  }

  @Test
  void run_unconfirmedTrade_clearsOnceItsFtConfirmationArrives() {
    TransactionOrder order = registerOrderExecutedInFull();
    s3Holds(Map.of());
    job.run();
    assertThat(latestStatuses(tradeKey(order))).first().isEqualTo("UNCONFIRMED");

    stubOneConfirmationPdfUnderPrefix();
    job.run();

    assertThat(latestStatuses(tradeKey(order))).containsExactly("CONFIRMED", "CONFIRMED");
  }

  @Test
  void run_twoTradesThatDay_onlyTheOneWithoutAnFtConfirmationOfItsQuantityIsUnconfirmed() {
    TransactionOrder confirmed = registerOrderExecuted("2500");
    TransactionOrder unconfirmed = registerOrderExecuted("250");
    stubOneConfirmationPdfUnderPrefix();

    job.run();

    assertThat(latestStatuses(tradeKey(confirmed))).containsExactly("CONFIRMED", "CONFIRMED");
    assertThat(latestStatuses(tradeKey(unconfirmed))).first().isEqualTo("UNCONFIRMED");
  }

  @Test
  void run_confirmationOfAnOrderCancelledInTheRegistry_doesNotConfirmTheDaysOtherTrade() {
    registerOrder("2500", CANCELLED);
    TransactionOrder replacement = registerOrderExecuted("250");
    stubOneConfirmationPdfUnderPrefix();

    job.run();

    assertThat(latestStatuses(DEDUP_KEY)).containsExactly("ORPHAN", "ORPHAN");
    assertThat(latestStatuses(tradeKey(replacement))).first().isEqualTo("UNCONFIRMED");
  }

  @Test
  void run_ftCancellationSeenToday_leavesTheTradeConfirmedWhileFtMaySendItsReplacement() {
    TransactionOrder order = registerOrderExecutedInFull();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY))
        .containsExactly("PENDING_REPLACEMENT", "PENDING_REPLACEMENT");
    assertThat(latestStatuses(tradeKey(order))).containsExactly("CONFIRMED", "CONFIRMED");
  }

  @Test
  void run_onlyFtsCancellationIsReadable_leavesTheTradeConfirmedWhileFtMaySendItsReplacement() {
    TransactionOrder order = registerOrderExecutedInFull();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY))
        .containsExactly("PENDING_REPLACEMENT", "PENDING_REPLACEMENT");
    assertThat(latestStatuses(tradeKey(order))).containsExactly("CONFIRMED", "CONFIRMED");
  }

  @Test
  void run_onlyFtsCancellationIsReadableAndItsWindowHasPassed_leavesTheTradeUnconfirmed() {
    TransactionOrder order = registerOrderExecutedInFull();
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");
    assertThat(latestStatuses(tradeKey(order))).first().isEqualTo("UNCONFIRMED");
  }

  @Test
  void run_ftCancellationWithoutAReplacementPastItsWindow_leavesTheTradeUnconfirmed() {
    TransactionOrder order = registerOrderExecutedInFull();
    cancellationFirstSeenOnTheTradeDay();
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_first.pdf",
            confirmationPdf("Trade Confirmation", "TEST0002-00", "12.000000 EUR"),
            "ft-confirmations/TEST0002-00_cancellation.pdf",
            confirmationPdf("Trade Cancellation", "TEST0002-00", "12.000000 EUR")));

    job.run();

    assertThat(latestStatuses(CANCELLATION_KEY)).containsExactly("CANCELLED", "CANCELLED");
    assertThat(latestStatuses(tradeKey(order))).first().isEqualTo("UNCONFIRMED");
  }

  @Test
  void run_confirmationPdfFromAnAccountTheParserDoesNotKnow_isWarnedAboutOnceOverTwoRuns() {
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_1.pdf",
            confirmationPdf(
                "Trade Confirmation", "TEST0002-00", "Unknown Account", "2,500", "12.345600 EUR")));
    pdfsUploadedAt(Instant.parse("2026-06-08T17:00:00Z"));

    job.run();
    job.run();

    then(notificationService)
        .should()
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT confirmation PDF could not be read: key=ft-confirmations/TEST0002-00_1.pdf,"
                + " error=FT confirmation PDF has unrecognized Account: account=Unknown Account,"
                + " allocationId=TEST0002-00 — check the trade in this PDF against its order by"
                + " hand",
            INVESTMENT);
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void run_unreadableConfirmationPdfUploadedBeforeAlertsFrom_isNotWarnedAbout() {
    s3Holds(
        Map.of(
            "ft-confirmations/TEST0002-00_1.pdf",
            confirmationPdf(
                "Trade Confirmation", "TEST0002-00", "Unknown Account", "2,500", "12.345600 EUR")));
    pdfsUploadedAt(Instant.parse("2026-01-14T21:59:59Z"));

    job.run();

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void run_tradeSentToSebRatherThanFt_isNotLookedAtForAnFtConfirmation() {
    TransactionOrder order = registerOrderExecuted("2500");
    order.setOrderVenue(OrderVenue.SEB);
    orderRepository.save(order);
    s3Holds(Map.of());

    job.run();

    assertThat(
            auditEventRepository.findByEventTypeAndDedupKey(
                FT_CONFIRMATION_VERIFIED, tradeKey(order)))
        .isEmpty();
  }

  private static String tradeKey(TransactionOrder order) {
    return "FT_TRADE|" + order.getOrderUuid();
  }

  private void closingPriceArrives(String closingPrice) {
    jdbcClient
        .sql(
            """
            INSERT INTO index_values (key, date, value, provider, updated_at)
            VALUES (:key, :date, :value, 'BLACKROCK', :updatedAt)
            """)
        .param("key", "IE00BFG1TM61.BLACKROCK")
        .param("date", LocalDate.of(2026, 1, 15))
        .param("value", new BigDecimal(closingPrice))
        .param("updatedAt", Timestamp.from(Instant.parse("2026-01-16T08:00:00Z")))
        .update();
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

  private TransactionOrder registerOrderExecutedInFull() {
    return registerOrderExecuted("2500");
  }

  private TransactionOrder registerOrderExecuted(String quantity) {
    TransactionOrder order = registerOrder(quantity, EXECUTED);
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA" + quantity)
            .executionTimestamp(Instant.parse("2026-01-15T15:30:00Z"))
            .executedQuantity(new BigDecimal(quantity))
            .unitPrice(new BigDecimal("12.3456"))
            .source("SEB_OOTEL")
            .build());
    return order;
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

  private void pdfsUploadedAt(Instant uploadedAt) {
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(HeadObjectResponse.builder().lastModified(uploadedAt).build());
  }

  private void stubOneConfirmationPdfUnderPrefix() {
    s3Holds(Map.of(KEY, confirmationPdf()));
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
