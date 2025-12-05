package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestSchedulerLockConfiguration.class)
@Transactional
class RedemptionIntegrationTest {

  // Monday 2025-09-29 17:00 EET (15:00 UTC) - after 16:00 cutoff
  private static final Instant NOW = Instant.parse("2025-09-29T15:00:00Z");
  private static final String VALID_IBAN = "EE471000001020145685";

  @Autowired RedemptionService redemptionService;
  @Autowired RedemptionStatusService redemptionStatusService;
  @Autowired RedemptionRequestRepository redemptionRequestRepository;
  @Autowired RedemptionBatchJob redemptionBatchJob;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired LedgerService ledgerService;
  @Autowired UserRepository userRepository;
  @Autowired SavingFundPaymentRepository savingFundPaymentRepository;

  @MockitoBean SwedbankGatewayClient swedbankGatewayClient;
  @MockitoBean SavingsFundNavProvider navProvider;

  User testUser;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneId.of("UTC")));
    doNothing().when(swedbankGatewayClient).sendPaymentRequest(any(), any());
    lenient().when(navProvider.getCurrentNav()).thenReturn(BigDecimal.ONE);

    testUser =
        userRepository.save(sampleUser().id(null).member(null).personalCode("39901019992").build());
    savingsFundOnboardingRepository.saveOnboardingStatus(testUser.getId(), COMPLETED);
    setupUserWithFundUnits(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    setupUserDepositIban(VALID_IBAN);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("Create redemption request creates request in RESERVED status")
  void createRedemptionRequest_createsRequestInReservedStatus() {
    var amount = new BigDecimal("10.00");

    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), amount, EUR, VALID_IBAN);

    assertThat(request.getStatus()).isEqualTo(RESERVED);
    assertThat(request.getFundUnits()).isEqualByComparingTo(new BigDecimal("10.00000"));
    assertThat(request.getRequestedAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    assertThat(request.getCustomerIban()).isEqualTo(VALID_IBAN);
    assertThat(request.getUserId()).isEqualTo(testUser.getId());
    assertThat(request.getRequestedAt()).isNotNull();
  }

  @Test
  @DisplayName("Create redemption request fails when insufficient units")
  void createRedemptionRequest_failsWhenInsufficientUnits() {
    var amount = new BigDecimal("200.00");

    assertThatThrownBy(
            () ->
                redemptionService.createRedemptionRequest(
                    testUser.getId(), amount, EUR, VALID_IBAN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Max withdrawal rounds to exact balance")
  void createRedemptionRequest_maxWithdrawalRoundsToExactBalance() {
    var maxAmount = new BigDecimal("100.00");

    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), maxAmount, EUR, VALID_IBAN);

    assertThat(request.getFundUnits()).isEqualByComparingTo(new BigDecimal("100.00000"));
  }

  @Test
  @DisplayName("Create redemption request fails when amount has more than 2 decimals")
  void createRedemptionRequest_failsWhenAmountHasMoreThanTwoDecimals() {
    var amount = new BigDecimal("10.123");

    assertThatThrownBy(
            () ->
                redemptionService.createRedemptionRequest(
                    testUser.getId(), amount, EUR, VALID_IBAN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Cancel redemption before cutoff")
  void cancelRedemption_cancelsRequestBeforeCutoff() {
    var amount = new BigDecimal("10.00");
    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), amount, EUR, VALID_IBAN);

    redemptionService.cancelRedemption(request.getId(), testUser.getId());

    var cancelled = redemptionRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(CANCELLED);
    assertThat(cancelled.getCancelledAt()).isNotNull();
  }

  @Test
  @DisplayName("Cancel redemption after deadline fails")
  void cancelRedemption_afterDeadline_throwsException() {
    // Monday 2025-09-29 17:00 EET (15:00 UTC) - after 16:00 cutoff
    // Deadline for cancellation: Tuesday 2025-09-30 16:00 EET
    var monday = Instant.parse("2025-09-29T15:00:00Z");
    ClockHolder.setClock(Clock.fixed(monday, ZoneId.of("Europe/Tallinn")));

    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), new BigDecimal("10.00"), EUR, VALID_IBAN);

    var tuesdayAfterDeadline = Instant.parse("2025-09-30T14:01:00Z");
    ClockHolder.setClock(Clock.fixed(tuesdayAfterDeadline, ZoneId.of("Europe/Tallinn")));

    assertThatThrownBy(() -> redemptionService.cancelRedemption(request.getId(), testUser.getId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cancellation deadline has passed");
  }

  @Test
  @DisplayName("Get user redemptions returns user's redemptions")
  void getUserRedemptions_returnsUserRedemptions() {
    redemptionService.createRedemptionRequest(
        testUser.getId(), new BigDecimal("5.00"), EUR, VALID_IBAN);
    redemptionService.createRedemptionRequest(
        testUser.getId(), new BigDecimal("3.00"), EUR, VALID_IBAN);

    var redemptions = redemptionService.getPendingRedemptionsForUser(testUser.getId());

    assertThat(redemptions).hasSize(2);
  }

  @Test
  @DisplayName("Create redemption request locks units in ledger immediately")
  void createRedemptionRequest_locksUnitsInLedger() {
    var amount = new BigDecimal("10.00");

    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), amount, EUR, VALID_IBAN);

    assertThat(request.getStatus()).isEqualTo(RESERVED);

    var reservedUnitsBalance = getUserFundUnitsReservedAccount().getBalance();
    assertThat(reservedUnitsBalance).isEqualByComparingTo(new BigDecimal("-10.00000"));
  }

  @Test
  @DisplayName("Full redemption flow with ledger operations")
  void fullRedemptionFlow_allBalancesCorrect() {
    var fundUnits = new BigDecimal("10.00000");
    var navPerUnit = new BigDecimal("10.00");
    var cashAmount = new BigDecimal("100.00");

    // Step 1: Reserve units (happens when redemption request created)
    savingsFundLedger.reserveFundUnitsForRedemption(testUser, fundUnits);

    // Step 2: Price and redeem (happens in batch job at T+2)
    savingsFundLedger.redeemFundUnitsFromReserved(testUser, fundUnits, cashAmount, navPerUnit);

    // Steps 3 & 4: Transfer and payout (happens during bank statement reconciliation)
    savingsFundLedger.transferFromFundAccount(cashAmount);
    savingsFundLedger.recordRedemptionPayout(testUser, cashAmount, VALID_IBAN);

    var fundsUnitsBalance = getUserFundUnitsAccount().getBalance();
    var reservedUnitsBalance = getUserFundUnitsReservedAccount().getBalance();
    var redemptionsBalance = getUserRedemptionsAccount().getBalance();

    assertThat(fundsUnitsBalance.negate()).isEqualByComparingTo(new BigDecimal("90.00000"));
    assertThat(reservedUnitsBalance).isEqualByComparingTo(ZERO);
    assertThat(redemptionsBalance).isEqualByComparingTo(cashAmount);
  }

  @Test
  @DisplayName("End-to-end batch job: RESERVED → VERIFIED → REDEEMED")
  void endToEndBatchJob_processesRedemptionRequest() {
    var initialFundUnits = new BigDecimal("100.00000");
    var redemptionAmount = new BigDecimal("25.00");
    var expectedFundUnits = new BigDecimal("25.00000");
    var remainingUnits = initialFundUnits.subtract(expectedFundUnits);

    // Friday 17:00 EET (14:00 UTC) - AFTER 16:00 EET cutoff
    var friday = Instant.parse("2025-09-26T14:00:00Z");
    var tuesday = Instant.parse("2025-09-30T15:00:00Z");

    // Step 1: Create redemption request on Friday (after cutoff) - immediately RESERVED
    ClockHolder.setClock(Clock.fixed(friday, ZoneId.of("UTC")));
    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    assertThat(request.getStatus()).isEqualTo(RESERVED);
    assertThat(getUserFundUnitsAccount().getBalance())
        .isEqualByComparingTo(remainingUnits.negate());
    assertThat(getUserFundUnitsReservedAccount().getBalance())
        .isEqualByComparingTo(expectedFundUnits.negate());

    // Step 2: Simulate verification job changing status to VERIFIED
    redemptionStatusService.changeStatus(requestId, VERIFIED);

    var afterVerification = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterVerification.getStatus()).isEqualTo(VERIFIED);

    // Step 3: Run batch job on Tuesday - prices units and sends payments to bank
    // Note: Ledger entries for transfer (step 3) and payout (step 4) happen during reconciliation
    ClockHolder.setClock(Clock.fixed(tuesday, ZoneId.of("UTC")));
    redemptionBatchJob.runJob();

    var afterBatchJob = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterBatchJob.getStatus()).isEqualTo(REDEEMED);
    assertThat(afterBatchJob.getCashAmount()).isEqualByComparingTo(redemptionAmount);
    assertThat(afterBatchJob.getNavPerUnit()).isNotNull();
    assertThat(afterBatchJob.getProcessedAt()).isNotNull();

    // Fund units redeemed from reserved
    assertThat(getUserFundUnitsAccount().getBalance())
        .isEqualByComparingTo(remainingUnits.negate());
    assertThat(getUserFundUnitsReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getFundUnitsOutstandingAccount().getBalance()).isEqualByComparingTo(remainingUnits);

    // Cash redemption account has pending payout (will be cleared during reconciliation)
    assertThat(getUserCashRedemptionAccount().getBalance())
        .isEqualByComparingTo(redemptionAmount.negate());
  }

  @Test
  @DisplayName("Cancelled redemption is not processed by batch job and releases reserved units")
  void cancelledRedemption_notProcessedByBatchJob() {
    var initialFundUnits = new BigDecimal("100.00000");
    var redemptionAmount = new BigDecimal("50.00");
    var reservedFundUnits = new BigDecimal("50.00000");

    var friday = Instant.parse("2025-09-26T10:00:00Z");
    var monday = Instant.parse("2025-09-29T15:00:00Z");

    // Create redemption on Friday - immediately RESERVED
    ClockHolder.setClock(Clock.fixed(friday, ZoneId.of("UTC")));
    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    assertThat(request.getStatus()).isEqualTo(RESERVED);
    assertThat(getUserFundUnitsReservedAccount().getBalance())
        .isEqualByComparingTo(reservedFundUnits.negate());

    // Cancel before batch job runs - should release reserved units
    redemptionService.cancelRedemption(requestId, testUser.getId());

    // Run batch job on Monday
    ClockHolder.setClock(Clock.fixed(monday, ZoneId.of("UTC")));
    redemptionBatchJob.runJob();

    // Should remain cancelled
    var afterBatch = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterBatch.getStatus()).isEqualTo(CANCELLED);

    // Fund units returned to user account
    assertThat(getUserFundUnitsAccount().getBalance())
        .isEqualByComparingTo(initialFundUnits.negate());
    assertThat(getUserFundUnitsReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  private void setupUserWithFundUnits(BigDecimal cashAmount, BigDecimal fundUnits) {
    var navPerUnit = cashAmount.divide(fundUnits, 5, HALF_UP);
    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, UUID.randomUUID());
    savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);
    savingsFundLedger.transferToFundAccount(cashAmount);
  }

  private LedgerAccount getUserFundUnitsAccount() {
    return ledgerService.getUserAccount(testUser, FUND_UNITS);
  }

  private LedgerAccount getUserFundUnitsReservedAccount() {
    return ledgerService.getUserAccount(testUser, FUND_UNITS_RESERVED);
  }

  private LedgerAccount getUserCashRedemptionAccount() {
    return ledgerService.getUserAccount(testUser, CASH_REDEMPTION);
  }

  private LedgerAccount getUserRedemptionsAccount() {
    return ledgerService.getUserAccount(testUser, REDEMPTIONS);
  }

  private LedgerAccount getFundUnitsOutstandingAccount() {
    return ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING);
  }

  private void setupUserDepositIban(String iban) {
    setupUserDepositIban(testUser, iban);
  }

  private void setupUserDepositIban(User user, String iban) {
    var payment =
        SavingFundPayment.builder()
            .externalId("TEST-" + UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .currency(EUR)
            .description("Test deposit")
            .remitterIban(iban)
            .remitterIdCode(user.getPersonalCode())
            .remitterName(user.getFirstName() + " " + user.getLastName())
            .beneficiaryIban("EE362200221234567897")
            .beneficiaryIdCode("12345678")
            .beneficiaryName("Tuleva")
            .status(SavingFundPayment.Status.PROCESSED)
            .build();

    var paymentId = savingFundPaymentRepository.savePaymentData(payment);
    savingFundPaymentRepository.attachUser(paymentId, user.getId());
    savingFundPaymentRepository.changeStatus(paymentId, SavingFundPayment.Status.RECEIVED);
    savingFundPaymentRepository.changeStatus(paymentId, SavingFundPayment.Status.VERIFIED);
    savingFundPaymentRepository.changeStatus(paymentId, SavingFundPayment.Status.RESERVED);
    savingFundPaymentRepository.changeStatus(paymentId, SavingFundPayment.Status.ISSUED);
    savingFundPaymentRepository.changeStatus(paymentId, SavingFundPayment.Status.PROCESSED);
  }

  @Test
  @DisplayName("Create redemption request fails when IBAN does not belong to user")
  void createRedemptionRequest_failsWhenIbanDoesNotBelongToUser() {
    var amount = new BigDecimal("10.00");
    var invalidIban = "EE382200221020145685";

    assertThatThrownBy(
            () ->
                redemptionService.createRedemptionRequest(
                    testUser.getId(), amount, EUR, invalidIban))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("IBAN does not belong to user");
  }

  @Test
  @DisplayName("Max withdrawal with precise NAV rounds to exact balance")
  void createRedemptionRequest_maxWithdrawalWithPreciseNavRoundsToExactBalance() {
    var preciseNav = new BigDecimal("1.23456");
    var availableFundUnits = new BigDecimal("100.00000");
    var maxWithdrawalValue = availableFundUnits.multiply(preciseNav).setScale(2, HALF_UP);

    when(navProvider.getCurrentNav()).thenReturn(preciseNav);

    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), maxWithdrawalValue, EUR, VALID_IBAN);

    assertThat(request.getFundUnits()).isEqualByComparingTo(availableFundUnits);
    assertThat(request.getRequestedAmount()).isEqualByComparingTo(maxWithdrawalValue);
  }

  @Test
  @DisplayName("Running batch job twice does not create duplicate ledger entries or payments")
  void batchJobIdempotency_runningTwiceDoesNotCreateDuplicates() {
    var redemptionAmount = new BigDecimal("25.00");

    var friday = Instant.parse("2025-09-26T14:00:00Z");
    var tuesday = Instant.parse("2025-09-30T15:00:00Z");

    ClockHolder.setClock(Clock.fixed(friday, ZoneId.of("UTC")));
    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    redemptionStatusService.changeStatus(requestId, VERIFIED);

    ClockHolder.setClock(Clock.fixed(tuesday, ZoneId.of("UTC")));

    redemptionBatchJob.runJob();
    var afterFirstRun = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterFirstRun.getStatus()).isEqualTo(REDEEMED);

    var cashRedemptionAfterFirst = getUserCashRedemptionAccount().getBalance();

    redemptionBatchJob.runJob();
    var afterSecondRun = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterSecondRun.getStatus()).isEqualTo(REDEEMED);

    var cashRedemptionAfterSecond = getUserCashRedemptionAccount().getBalance();

    // Ledger balances should not change on second run
    assertThat(cashRedemptionAfterSecond).isEqualByComparingTo(cashRedemptionAfterFirst);

    // Payments sent only once (batch + individual)
    verify(swedbankGatewayClient, times(2)).sendPaymentRequest(any(), any());
  }
}
