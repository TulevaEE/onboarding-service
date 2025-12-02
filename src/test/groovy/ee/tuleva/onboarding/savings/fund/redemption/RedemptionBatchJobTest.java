package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.WITHDRAWAL_EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RedemptionBatchJobTest {

  @Mock private PublicHolidays publicHolidays;
  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private RedemptionStatusService redemptionStatusService;
  @Mock private SavingsFundLedger savingsFundLedger;
  @Mock private UserService userService;
  @Mock private SwedbankGatewayClient swedbankGatewayClient;
  @Mock private SwedbankAccountConfiguration swedbankAccountConfiguration;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private SavingsFundNavProvider navProvider;

  private RedemptionBatchJob batchJob;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    fixedClock =
        Clock.fixed(
            LocalDateTime.of(2024, 1, 15, 16, 30).atZone(ZoneId.of("Europe/Tallinn")).toInstant(),
            ZoneId.of("Europe/Tallinn"));

    lenient().when(navProvider.getCurrentNav()).thenReturn(BigDecimal.ONE);

    batchJob =
        new RedemptionBatchJob(
            fixedClock,
            publicHolidays,
            redemptionRequestRepository,
            redemptionStatusService,
            savingsFundLedger,
            userService,
            swedbankGatewayClient,
            swedbankAccountConfiguration,
            transactionTemplate,
            navProvider);
  }

  @Test
  @DisplayName("processDailyRedemptions skips on non-working days")
  void processDailyRedemptions_skipsOnNonWorkingDay() {
    when(publicHolidays.isWorkingDay(LocalDate.of(2024, 1, 15))).thenReturn(false);

    batchJob.processDailyRedemptions();

    verifyNoInteractions(redemptionRequestRepository);
  }

  @Test
  @DisplayName("processDailyRedemptions processes on working days")
  void processDailyRedemptions_processesOnWorkingDay() {
    when(publicHolidays.isWorkingDay(LocalDate.of(2024, 1, 15))).thenReturn(true);
    when(publicHolidays.previousWorkingDay(any(LocalDate.class)))
        .thenReturn(LocalDate.of(2024, 1, 12));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
            eq(PENDING), any(Instant.class)))
        .thenReturn(List.of());
    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(
            eq(RESERVED), any(Instant.class)))
        .thenReturn(List.of());

    batchJob.processDailyRedemptions();

    verify(redemptionRequestRepository)
        .findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(eq(PENDING), any(Instant.class));
  }

  @Test
  @DisplayName("processDailyRedemptions reserves pending requests")
  void processDailyRedemptions_reservesPendingRequests() {
    var user = sampleUser().build();
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(user.getId())
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(PENDING)
            .build();

    when(publicHolidays.isWorkingDay(LocalDate.of(2024, 1, 15))).thenReturn(true);
    when(publicHolidays.previousWorkingDay(any(LocalDate.class)))
        .thenReturn(LocalDate.of(2024, 1, 12));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
            eq(PENDING), any(Instant.class)))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(
            eq(RESERVED), any(Instant.class)))
        .thenReturn(List.of());
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);

    doAnswer(
            invocation -> {
              Consumer<TransactionStatus> callback = invocation.getArgument(0);
              callback.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());

    batchJob.processDailyRedemptions();

    verify(savingsFundLedger).reserveFundUnitsForRedemption(user, new BigDecimal("10.00000"));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED);
  }

  @Test
  @DisplayName("processDailyRedemptions handles errors during reservation")
  void processDailyRedemptions_handlesReservationErrors() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(PENDING)
            .build();

    when(publicHolidays.isWorkingDay(LocalDate.of(2024, 1, 15))).thenReturn(true);
    when(publicHolidays.previousWorkingDay(any(LocalDate.class)))
        .thenReturn(LocalDate.of(2024, 1, 12));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
            eq(PENDING), any(Instant.class)))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(
            eq(RESERVED), any(Instant.class)))
        .thenReturn(List.of());
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

    doThrow(new RuntimeException("Test error"))
        .when(transactionTemplate)
        .executeWithoutResult(any());

    batchJob.processDailyRedemptions();

    verify(redemptionStatusService).changeStatus(requestId, FAILED);
  }

  @Test
  @DisplayName("processDailyRedemptions processes reserved requests with NAV")
  void processDailyRedemptions_processesReservedRequests() {
    var user = sampleUser().build();
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(user.getId())
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(RESERVED)
            .build();

    when(publicHolidays.isWorkingDay(LocalDate.of(2024, 1, 15))).thenReturn(true);
    when(publicHolidays.previousWorkingDay(any(LocalDate.class)))
        .thenReturn(LocalDate.of(2024, 1, 12));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
            eq(PENDING), any(Instant.class)))
        .thenReturn(List.of());
    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(
            eq(RESERVED), any(Instant.class)))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE111111111111111111");
    when(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
        .thenReturn("EE222222222222222222");

    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());

    doAnswer(
            invocation -> {
              Consumer<TransactionStatus> callback = invocation.getArgument(0);
              callback.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());

    batchJob.processDailyRedemptions();

    verify(savingsFundLedger)
        .redeemFundUnitsFromReserved(
            eq(user), eq(new BigDecimal("10.00000")), any(BigDecimal.class), eq(BigDecimal.ONE));
    verify(savingsFundLedger).transferFromFundAccount(any(BigDecimal.class));
    verify(savingsFundLedger)
        .recordRedemptionPayout(eq(user), any(BigDecimal.class), eq("EE123456789012345678"));
    verify(swedbankGatewayClient, times(2))
        .sendPaymentRequest(any(PaymentRequest.class), any(UUID.class));
  }

  @Test
  @DisplayName("processDailyRedemptions handles payout errors")
  void processDailyRedemptions_handlesPayoutErrors() {
    var user = sampleUser().build();
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(user.getId())
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(RESERVED)
            .cashAmount(new BigDecimal("10.00"))
            .build();

    when(publicHolidays.isWorkingDay(LocalDate.of(2024, 1, 15))).thenReturn(true);
    when(publicHolidays.previousWorkingDay(any(LocalDate.class)))
        .thenReturn(LocalDate.of(2024, 1, 12));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
            eq(PENDING), any(Instant.class)))
        .thenReturn(List.of());
    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(
            eq(RESERVED), any(Instant.class)))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE111111111111111111");
    when(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
        .thenReturn("EE222222222222222222");

    doAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());

    var callCount = new int[] {0};
    doAnswer(
            invocation -> {
              callCount[0]++;
              if (callCount[0] == 1) {
                Consumer<TransactionStatus> callback = invocation.getArgument(0);
                callback.accept(null);
                return null;
              }
              throw new RuntimeException("Payout error");
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());

    batchJob.processDailyRedemptions();

    verify(redemptionStatusService).changeStatus(requestId, FAILED);
  }

  @Test
  @DisplayName("handleError marks request as failed and saves error reason")
  void handleError_marksRequestAsFailed() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(PENDING)
            .build();

    when(publicHolidays.isWorkingDay(LocalDate.of(2024, 1, 15))).thenReturn(true);
    when(publicHolidays.previousWorkingDay(any(LocalDate.class)))
        .thenReturn(LocalDate.of(2024, 1, 12));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
            eq(PENDING), any(Instant.class)))
        .thenReturn(List.of(request));
    when(redemptionRequestRepository.findByStatusAndRequestedAtBefore(
            eq(RESERVED), any(Instant.class)))
        .thenReturn(List.of());
    when(redemptionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

    doThrow(new RuntimeException("Test error"))
        .when(transactionTemplate)
        .executeWithoutResult(any());

    batchJob.processDailyRedemptions();

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(redemptionRequestRepository).save(captor.capture());
    assertThat(captor.getValue().getErrorReason()).contains("Test error");
    verify(redemptionStatusService).changeStatus(requestId, FAILED);
  }
}
