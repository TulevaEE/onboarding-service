package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RedemptionBatchJobTest {

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private RedemptionStatusService redemptionStatusService;
  @Mock private SavingsFundLedger savingsFundLedger;
  @Mock private UserService userService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private SwedbankAccountConfiguration swedbankAccountConfiguration;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private SavingsFundNavProvider navProvider;
  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;

  @BeforeEach
  void setUp() {
    lenient().when(navProvider.getCurrentNav()).thenReturn(BigDecimal.ONE);
  }

  private RedemptionBatchJob createBatchJob(Instant now) {
    var clock = Clock.fixed(now, UTC);
    return new RedemptionBatchJob(
        clock,
        new PublicHolidays(),
        redemptionRequestRepository,
        redemptionStatusService,
        savingsFundLedger,
        userService,
        eventPublisher,
        swedbankAccountConfiguration,
        transactionTemplate,
        navProvider,
        savingFundPaymentRepository,
        new EndToEndIdConverter());
  }

  @Test
  @DisplayName("runJob does nothing when no verified requests exist")
  void runJob_noRequests() {
    var now = Instant.parse("2025-01-15T15:00:00Z");
    var batchJob = createBatchJob(now);

    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(eq(VERIFIED), any()))
        .thenReturn(List.of());

    batchJob.runJob();

    verify(redemptionRequestRepository).findByStatusAndRequestedAtBefore(eq(VERIFIED), any());
    verify(eventPublisher, never()).publishEvent(any(RequestPaymentEvent.class));
  }

  @Test
  @DisplayName("after 16:00 on working day, processes requests from before last working day cutoff")
  void runJob_afterCutoff_processesRequestsFromLastWorkingDay() {
    var now = Instant.parse("2025-01-15T15:00:00Z"); // 17:00 Tallinn time (after cutoff)
    var batchJob = createBatchJob(now);

    var user = sampleUser().build();
    var requestId = UUID.randomUUID();
    var customerIban = "EE123456789012345678";
    var beneficiaryName = "John Smith";
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(user.getId())
            .status(VERIFIED)
            .customerIban(customerIban)
            .requestedAt(now.minus(1, DAYS)) // yesterday
            .build();

    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(eq(VERIFIED), any()))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE111111111111111111");
    when(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
        .thenReturn("EE222222222222222222");
    when(savingFundPaymentRepository.findRemitterNameByIban(user.getId(), customerIban))
        .thenReturn(Optional.of(beneficiaryName));

    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());

    batchJob.runJob();

    verify(savingsFundLedger)
        .redeemFundUnitsFromReserved(
            eq(user),
            eq(new BigDecimal("10.00000")),
            any(BigDecimal.class),
            eq(BigDecimal.ONE),
            eq(requestId));
    verify(eventPublisher, times(2)).publishEvent(any(RequestPaymentEvent.class));
    verify(redemptionStatusService).changeStatus(requestId, REDEEMED);

    var savedRequestCaptor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(redemptionRequestRepository, atLeast(2)).save(savedRequestCaptor.capture());
    var lastSaved = savedRequestCaptor.getAllValues().getLast();
    assertThat(lastSaved.getProcessedAt()).isNotNull();
  }

  @Test
  @DisplayName(
      "before 16:00 on working day, processes requests from before second-to-last working day cutoff")
  void runJob_beforeCutoff_processesRequestsFromSecondToLastWorkingDay() {
    var now = Instant.parse("2025-01-15T12:00:00Z"); // 14:00 Tallinn time (before cutoff)
    var batchJob = createBatchJob(now);

    var cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(
            eq(VERIFIED), cutoffCaptor.capture()))
        .thenReturn(List.of());

    batchJob.runJob();

    // Before 16:00, cutoff should be second-to-last working day's 16:00
    // January 15 before cutoff means we use January 13 (Monday) 16:00 Tallinn as cutoff
    var capturedCutoff = cutoffCaptor.getValue();
    var cutoffDateTime = capturedCutoff.atZone(ZoneId.of("Europe/Tallinn"));
    assertThat(cutoffDateTime.getHour()).isEqualTo(16);
    assertThat(cutoffDateTime.getMinute()).isEqualTo(0);
  }

  @Test
  @DisplayName("on weekend, processes requests from before second-to-last working day cutoff")
  void runJob_onWeekend_processesOlderRequests() {
    var now = Instant.parse("2025-01-18T14:00:00Z"); // Saturday
    var batchJob = createBatchJob(now);

    var cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(
            eq(VERIFIED), cutoffCaptor.capture()))
        .thenReturn(List.of());

    batchJob.runJob();

    // On weekend, cutoff should be second-to-last working day's 16:00
    // Saturday Jan 18 -> last working day is Friday Jan 17 -> second-to-last is Thursday Jan 16
    var capturedCutoff = cutoffCaptor.getValue();
    var cutoffDate = capturedCutoff.atZone(ZoneId.of("Europe/Tallinn")).toLocalDate();
    assertThat(cutoffDate).isEqualTo(LocalDate.of(2025, 1, 16));
  }

  @Test
  @DisplayName("runJob handles individual payout errors")
  void runJob_handlesPayoutErrors() {
    var now = Instant.parse("2025-01-15T15:00:00Z");
    var batchJob = createBatchJob(now);

    var user = sampleUser().build();
    var requestId = UUID.randomUUID();
    var customerIban = "EE123456789012345678";
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(user.getId())
            .status(VERIFIED)
            .customerIban(customerIban)
            .cashAmount(new BigDecimal("10.00"))
            .build();

    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(eq(VERIFIED), any()))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE111111111111111111");
    when(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
        .thenReturn("EE222222222222222222");
    when(savingFundPaymentRepository.findRemitterNameByIban(user.getId(), customerIban))
        .thenReturn(Optional.of("John Smith"));

    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());

    // First call (batch transfer) succeeds, second call (individual payout) fails
    doAnswer(
            invocation -> {
              return null;
            })
        .doThrow(new RuntimeException("Payout error"))
        .when(eventPublisher)
        .publishEvent(any(RequestPaymentEvent.class));

    batchJob.runJob();

    verify(redemptionStatusService).changeStatus(requestId, FAILED);
  }

  @Test
  @DisplayName("runJob marks request as failed and saves error reason on processing error")
  void runJob_marksRequestAsFailed() {
    var now = Instant.parse("2025-01-15T15:00:00Z");
    var batchJob = createBatchJob(now);

    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();

    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(eq(VERIFIED), any()))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

    doThrow(new RuntimeException("Test error")).when(transactionTemplate).execute(any());

    batchJob.runJob();

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(redemptionRequestRepository).save(captor.capture());
    assertThat(captor.getValue().getErrorReason()).contains("Test error");
    verify(redemptionStatusService).changeStatus(requestId, FAILED);
  }

  @Test
  @DisplayName("runJob skips already priced requests")
  void runJob_skipsAlreadyPricedRequests() {
    var now = Instant.parse("2025-01-15T15:00:00Z");
    var batchJob = createBatchJob(now);

    var user = sampleUser().build();
    var requestId = UUID.randomUUID();
    var customerIban = "EE123456789012345678";
    var alreadyPricedAmount = new BigDecimal("10.00");
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(user.getId())
            .status(VERIFIED)
            .customerIban(customerIban)
            .cashAmount(alreadyPricedAmount)
            .requestedAt(now.minus(1, DAYS))
            .build();

    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(eq(VERIFIED), any()))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE111111111111111111");
    when(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
        .thenReturn("EE222222222222222222");
    when(savingFundPaymentRepository.findRemitterNameByIban(user.getId(), customerIban))
        .thenReturn(Optional.of("John Smith"));

    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());

    batchJob.runJob();

    // Verify pricing was skipped but payments were still sent
    verify(eventPublisher, times(2)).publishEvent(any(RequestPaymentEvent.class));
  }

  @Test
  @DisplayName("runJob uses deterministic payment ID for individual payouts")
  void runJob_usesDeterministicPaymentId() {
    var now = Instant.parse("2025-01-15T15:00:00Z");
    var batchJob = createBatchJob(now);

    var user = sampleUser().build();
    var requestId = UUID.randomUUID();
    var customerIban = "EE123456789012345678";
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(user.getId())
            .status(VERIFIED)
            .customerIban(customerIban)
            .requestedAt(now.minus(1, DAYS))
            .build();

    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(eq(VERIFIED), any()))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE111111111111111111");
    when(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
        .thenReturn("EE222222222222222222");
    when(savingFundPaymentRepository.findRemitterNameByIban(user.getId(), customerIban))
        .thenReturn(Optional.of("John Smith"));

    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());

    batchJob.runJob();

    var eventCaptor = ArgumentCaptor.forClass(RequestPaymentEvent.class);
    verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

    var capturedEvents = eventCaptor.getAllValues();
    // Second payment (individual payout) should use the request ID
    assertThat(capturedEvents.get(1).requestId()).isEqualTo(requestId);
  }

  @Test
  @DisplayName("runJob running twice does not reprocess already redeemed requests")
  void runJob_runningTwice_doesNotReprocessRedeemedRequests() {
    var now = Instant.parse("2025-01-15T15:00:00Z");
    var batchJob = createBatchJob(now);

    var user = sampleUser().build();
    var requestId = UUID.randomUUID();
    var customerIban = "EE123456789012345678";
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(user.getId())
            .status(VERIFIED)
            .customerIban(customerIban)
            .requestedAt(now.minus(1, DAYS))
            .build();

    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(eq(VERIFIED), any()))
        .thenReturn(List.of(request))
        .thenReturn(List.of()); // Second run finds no VERIFIED requests
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE111111111111111111");
    when(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
        .thenReturn("EE222222222222222222");
    when(savingFundPaymentRepository.findRemitterNameByIban(user.getId(), customerIban))
        .thenReturn(Optional.of("John Smith"));

    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());

    // First run - processes the request
    batchJob.runJob();

    // Second run - should not find any VERIFIED requests (status changed to REDEEMED)
    batchJob.runJob();

    // Verify payment was only sent once per type (batch + individual)
    verify(eventPublisher, times(2)).publishEvent(any(RequestPaymentEvent.class));
    verify(redemptionStatusService, times(1)).changeStatus(requestId, REDEEMED);
  }

  @Test
  @DisplayName("runJob skips pricing when ledger entry already exists")
  void runJob_skipsPricingWhenLedgerEntryExists() {
    var now = Instant.parse("2025-01-15T15:00:00Z");
    var batchJob = createBatchJob(now);

    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(VERIFIED)
            .cashAmount(null)
            .requestedAt(now.minus(1, DAYS))
            .build();

    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(eq(VERIFIED), any()))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(savingsFundLedger.hasPricingEntry(requestId)).thenReturn(true);

    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());

    batchJob.runJob();

    verify(savingsFundLedger, never())
        .redeemFundUnitsFromReserved(any(), any(), any(), any(), any());
  }
}
