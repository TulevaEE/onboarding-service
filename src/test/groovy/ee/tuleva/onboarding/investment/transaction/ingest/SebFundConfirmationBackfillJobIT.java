package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.NAV_DATE_SET;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.REPLACED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.buyFields;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.confirmation;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.liberationSansTable;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationBackfillIncompleteException.LeftOut;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@DataJpaTest
class SebFundConfirmationBackfillJobIT {

  private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
  private static final String ISIN = "IE00BKPTWY98";
  private static final String FIRST_UPLOAD = "seb-confirmations/DLA0000001-001.pdf";
  private static final String RESENT_UPLOAD = "seb-confirmations/DLA0000001-002.pdf";
  private static final String OTHER_TRADE = "seb-confirmations/DLA0000002-001.pdf";
  private static final String UNREADABLE = "seb-confirmations/DLA0000003-001.pdf";

  @Autowired private JdbcClient jdbcClient;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private EntityManager entityManager;

  private final S3Client s3Client = mock(S3Client.class);
  private final LockProvider lockProvider = mock(LockProvider.class);
  private final SimpleLock importJobLock = mock(SimpleLock.class);

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private SebFundConfirmationRepository confirmationRepository;
  private SebFundConfirmationAuditRecorder auditRecorder;
  private SebFundConfirmationNavDateWriter navDateWriter;
  private SebFundConfirmationBackfillJob job;

  @BeforeEach
  void setUp() {
    confirmationRepository = new SebFundConfirmationRepository(jdbcClient);
    auditRecorder = new SebFundConfirmationAuditRecorder(auditEventRepository, clock);
    navDateWriter =
        new SebFundConfirmationNavDateWriter(
            confirmationRepository, executionRepository, orderRepository, auditRecorder);
    job = jobDatingWith(navDateWriter);
    given(lockProvider.lock(argThat(SebFundConfirmationBackfillJobIT::isTheImportJobsLock)))
        .willReturn(Optional.of(importJobLock));
  }

  private SebFundConfirmationBackfillJob jobDatingWith(SebFundConfirmationNavDateWriter writer) {
    return new SebFundConfirmationBackfillJob(
        new SebFundConfirmationS3Source(s3Client, clock),
        new SebFundConfirmationPdfParser(new SebClientNameToFundResolver(), liberationSansTable()),
        new SebFundConfirmationRecorder(confirmationRepository, auditRecorder, clock),
        writer,
        lockProvider,
        clock);
  }

  @Test
  void backfill_recordsTheLatestConfirmationOfEveryTradeInS3AndDatesTheExecutionsAlreadyThere() {
    s3Holds(
        Map.of(
            FIRST_UPLOAD, pdf("DLA0000001", "06/01/26"),
            RESENT_UPLOAD, pdf("DLA0000001", "07/01/26"),
            OTHER_TRADE, pdf("DLA0000002", "06/01/26")),
        FIRST_UPLOAD,
        RESENT_UPLOAD,
        OTHER_TRADE);
    TransactionExecution execution = registerExecution("DLA0000001");

    job.backfill();

    assertThat(recorded("DLA0000001"))
        .hasValueSatisfying(
            confirmation -> {
              assertThat(confirmation.pricingDate()).isEqualTo(LocalDate.of(2026, 1, 7));
              assertThat(confirmation.sourceFileKey()).isEqualTo(RESENT_UPLOAD);
            });
    assertThat(recorded("DLA0000002"))
        .hasValueSatisfying(
            confirmation -> {
              assertThat(confirmation.pricingDate()).isEqualTo(LocalDate.of(2026, 1, 6));
              assertThat(confirmation.sourceFileKey()).isEqualTo(OTHER_TRADE);
            });
    assertThat(reloaded(execution).getNavDate()).isEqualTo(LocalDate.of(2026, 1, 7));
    then(importJobLock).should().unlock();
  }

  @Test
  void backfill_runAgainChangesNothingAndSetsNoPricingDateTwice() {
    s3Holds(Map.of(FIRST_UPLOAD, pdf("DLA0000001", "06/01/26")), FIRST_UPLOAD);
    registerExecution("DLA0000001");

    job.backfill();
    job.backfill();

    assertThat(auditEventRepository.findByEventTypeAndDedupKey(NAV_DATE_SET, "DLA0000001"))
        .hasSize(1);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(REPLACED, "DLA0000001")).isEmpty();
  }

  @Test
  void backfill_recordsEveryReadablePdfAndThenFailsTheTriggerNamingThePdfItCouldNotRead() {
    s3Holds(
        Map.of(UNREADABLE, new byte[] {1, 2, 3}, OTHER_TRADE, pdf("DLA0000002", "06/01/26")),
        UNREADABLE,
        OTHER_TRADE);

    assertThatThrownBy(job::backfill)
        .isInstanceOfSatisfying(
            SebFundConfirmationBackfillIncompleteException.class,
            failure ->
                assertThat(failure.leftOut())
                    .isEqualTo(new LeftOut(List.of(UNREADABLE), List.of())));

    assertThat(recorded("DLA0000002")).isPresent();
    assertThat(recorded("DLA0000003")).isEmpty();
    then(importJobLock).should().unlock();
  }

  @Test
  void backfill_recordsTheOtherPdfsAndThenFailsTheTriggerNamingAPdfWhoseDownloadFailed() {
    s3Holds(Map.of(OTHER_TRADE, pdf("DLA0000002", "06/01/26")), FIRST_UPLOAD, OTHER_TRADE);
    given(s3Client.getObjectAsBytes(requestFor(FIRST_UPLOAD)))
        .willThrow(SdkClientException.create("S3 unavailable"));

    assertThatThrownBy(job::backfill)
        .isInstanceOfSatisfying(
            SebFundConfirmationBackfillIncompleteException.class,
            failure ->
                assertThat(failure.leftOut())
                    .isEqualTo(new LeftOut(List.of(), List.of(FIRST_UPLOAD))));

    assertThat(recorded("DLA0000002")).isPresent();
    assertThat(recorded("DLA0000001")).isEmpty();
  }

  @Test
  void backfill_recordsTheOtherPdfsAndThenFailsTheTriggerNamingAPdfThatVanishedBeforeItsDownload() {
    s3Holds(Map.of(OTHER_TRADE, pdf("DLA0000002", "06/01/26")), FIRST_UPLOAD, OTHER_TRADE);
    given(s3Client.getObjectAsBytes(requestFor(FIRST_UPLOAD)))
        .willThrow(NoSuchKeyException.builder().build());

    assertThatThrownBy(job::backfill)
        .isInstanceOfSatisfying(
            SebFundConfirmationBackfillIncompleteException.class,
            failure ->
                assertThat(failure.leftOut())
                    .isEqualTo(new LeftOut(List.of(), List.of(FIRST_UPLOAD))));

    assertThat(recorded("DLA0000002")).isPresent();
    assertThat(recorded("DLA0000001")).isEmpty();
  }

  @Test
  void backfill_datesTheOtherTradesAndThenFailsTheTriggerNamingAPdfWhoseTradeCouldNotBeDated() {
    s3Holds(
        Map.of(
            FIRST_UPLOAD, pdf("DLA0000001", "06/01/26"),
            OTHER_TRADE, pdf("DLA0000002", "06/01/26")),
        FIRST_UPLOAD,
        OTHER_TRADE);
    TransactionExecution otherExecution = registerExecution("DLA0000002");
    SebFundConfirmationNavDateWriter writerFailingForTheFirstTrade = spy(navDateWriter);
    willThrow(new IllegalStateException("registry down"))
        .given(writerFailingForTheFirstTrade)
        .write("DLA0000001");

    assertThatThrownBy(jobDatingWith(writerFailingForTheFirstTrade)::backfill)
        .isInstanceOfSatisfying(
            SebFundConfirmationBackfillIncompleteException.class,
            failure ->
                assertThat(failure.leftOut())
                    .isEqualTo(new LeftOut(List.of(), List.of(FIRST_UPLOAD))));

    assertThat(recorded("DLA0000001")).isPresent();
    assertThat(recorded("DLA0000002")).isPresent();
    assertThat(reloaded(otherExecution).getNavDate()).isEqualTo(LocalDate.of(2026, 1, 6));
  }

  @Test
  void backfill_waitsWhileTheImportJobHoldsItsLock() {
    willReturn(Optional.empty())
        .given(lockProvider)
        .lock(argThat(SebFundConfirmationBackfillJobIT::isTheImportJobsLock));

    assertThatThrownBy(job::backfill).isInstanceOf(JobBusyException.class);

    then(s3Client).shouldHaveNoInteractions();
  }

  private static boolean isTheImportJobsLock(LockConfiguration configuration) {
    return configuration.getName().equals("SebFundConfirmationImportJob");
  }

  private Optional<RecordedSebFundConfirmation> recorded(String reference) {
    return new SebFundConfirmationRepository(jdbcClient).findByTradeReference(reference);
  }

  private static byte[] pdf(String reference, String pricingDate) {
    Map<String, List<String>> fields = buyFields();
    fields.put("Tehingu nr", List.of(reference));
    fields.put("Hinna arvutamise kuupäev", List.of(pricingDate));
    return confirmation(fields);
  }

  private void s3Holds(Map<String, byte[]> pdfs, String... keysOldestFirst) {
    List<S3Object> objects =
        IntStream.range(0, keysOldestFirst.length)
            .mapToObj(
                index ->
                    S3Object.builder()
                        .key(keysOldestFirst[index])
                        .lastModified(Instant.parse("2026-01-08T10:00:00Z").plusSeconds(index))
                        .build())
            .toList();
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(ListObjectsV2Response.builder().contents(objects).isTruncated(false).build());
    pdfs.forEach(
        (key, bytes) ->
            given(s3Client.getObjectAsBytes(requestFor(key)))
                .willReturn(
                    ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes)));
  }

  private static GetObjectRequest requestFor(String key) {
    return GetObjectRequest.builder().bucket("tuleva-investment-reports").key(key).build();
  }

  private TransactionExecution registerExecution(String reference) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test-user").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin(ISIN)
                .transactionType(BUY)
                .instrumentType(FUND)
                .orderQuantity(new BigDecimal("1000"))
                .orderVenue(SEB)
                .orderUuid(UUID.randomUUID())
                .build());
    TransactionExecution execution =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId(reference)
                .executionTimestamp(Instant.parse("2026-01-05T12:48:00Z"))
                .source("HISTORICAL_IMPORT")
                .build());
    entityManager.flush();
    entityManager.clear();
    return execution;
  }

  private TransactionExecution reloaded(TransactionExecution execution) {
    entityManager.flush();
    entityManager.clear();
    return executionRepository.findById(execution.getId()).orElseThrow();
  }
}
