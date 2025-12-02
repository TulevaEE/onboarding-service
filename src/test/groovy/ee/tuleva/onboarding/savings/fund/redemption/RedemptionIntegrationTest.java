package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
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

  @MockitoBean SwedbankGatewayClient swedbankGatewayClient;

  User testUser;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneId.of("UTC")));
    doNothing().when(swedbankGatewayClient).sendPaymentRequest(any(), any());

    testUser =
        userRepository.save(sampleUser().id(null).member(null).personalCode("39901019992").build());
    savingsFundOnboardingRepository.completeOnboarding(testUser.getId());
    setupUserWithFundUnits(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("Create redemption request creates request in PENDING status")
  void createRedemptionRequest_createsRequestInPendingStatus() {
    var amount = new BigDecimal("10.00");

    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), amount, EUR, VALID_IBAN);

    assertThat(request.getStatus()).isEqualTo(PENDING);
    assertThat(request.getFundUnits()).isEqualByComparingTo(new BigDecimal("10.00000"));
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
  @DisplayName("Near max withdrawal rounds to exact balance")
  void createRedemptionRequest_nearMaxWithdrawalRoundsToExactBalance() {
    var nearMaxAmount = new BigDecimal("99.999999");

    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), nearMaxAmount, EUR, VALID_IBAN);

    assertThat(request.getFundUnits()).isEqualByComparingTo(new BigDecimal("100.00000"));
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
  @DisplayName("Get user redemptions returns user's redemptions")
  void getUserRedemptions_returnsUserRedemptions() {
    redemptionService.createRedemptionRequest(
        testUser.getId(), new BigDecimal("5.00"), EUR, VALID_IBAN);
    redemptionService.createRedemptionRequest(
        testUser.getId(), new BigDecimal("3.00"), EUR, VALID_IBAN);

    var redemptions = redemptionService.getUserRedemptions(testUser.getId());

    assertThat(redemptions).hasSize(2);
  }

  @Test
  @DisplayName("Reserve units locks units in ledger")
  void reserveUnits_locksUnitsInLedger() {
    var amount = new BigDecimal("10.00");
    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), amount, EUR, VALID_IBAN);

    savingsFundLedger.reserveFundUnitsForRedemption(testUser, request.getFundUnits());
    redemptionStatusService.changeStatus(request.getId(), RESERVED);

    var reserved = redemptionRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(reserved.getStatus()).isEqualTo(RESERVED);

    var reservedUnitsBalance = getUserFundUnitsReservedAccount().getBalance();
    assertThat(reservedUnitsBalance).isEqualByComparingTo(new BigDecimal("-10.00000"));
  }

  @Test
  @DisplayName("Full redemption flow with ledger operations")
  void fullRedemptionFlow_allBalancesCorrect() {
    var fundUnits = new BigDecimal("10.00000");
    var navPerUnit = new BigDecimal("10.00");
    var cashAmount = new BigDecimal("100.00");

    savingsFundLedger.reserveFundUnitsForRedemption(testUser, fundUnits);
    savingsFundLedger.redeemFundUnitsFromReserved(testUser, fundUnits, cashAmount, navPerUnit);
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
  @DisplayName("End-to-end batch job: PENDING → RESERVED → PAID_OUT")
  void endToEndBatchJob_processesRedemptionRequest() {
    var initialFundUnits = new BigDecimal("100.00000");
    var redemptionAmount = new BigDecimal("25.00");
    var expectedFundUnits = new BigDecimal("25.00000");
    var remainingUnits = initialFundUnits.subtract(expectedFundUnits);

    // Friday 17:00 EET (14:00 UTC) - AFTER 16:00 EET cutoff
    var friday = Instant.parse("2025-09-26T14:00:00Z");
    var monday = Instant.parse("2025-09-29T15:00:00Z");
    var tuesday = Instant.parse("2025-09-30T15:00:00Z");

    // Step 1: Create redemption request on Friday (after cutoff)
    ClockHolder.setClock(Clock.fixed(friday, ZoneId.of("UTC")));
    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    assertThat(request.getStatus()).isEqualTo(PENDING);

    // Step 2: Run batch job on Monday - should RESERVE the fund units (request is after Friday
    // cutoff)
    ClockHolder.setClock(Clock.fixed(monday, ZoneId.of("UTC")));
    redemptionBatchJob.processDailyRedemptions();

    var afterReservation = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterReservation.getStatus()).isEqualTo(RESERVED);

    assertThat(getUserFundUnitsAccount().getBalance())
        .isEqualByComparingTo(remainingUnits.negate());
    assertThat(getUserFundUnitsReservedAccount().getBalance())
        .isEqualByComparingTo(expectedFundUnits.negate());

    // Step 3: Run batch job on Tuesday - should process and PAID_OUT
    ClockHolder.setClock(Clock.fixed(tuesday, ZoneId.of("UTC")));
    redemptionBatchJob.processDailyRedemptions();

    var afterPayout = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterPayout.getStatus()).isEqualTo(PAID_OUT);
    assertThat(afterPayout.getCashAmount()).isEqualByComparingTo(redemptionAmount);
    assertThat(afterPayout.getNavPerUnit()).isNotNull();
    assertThat(afterPayout.getProcessedAt()).isNotNull();

    assertThat(getUserFundUnitsAccount().getBalance())
        .isEqualByComparingTo(remainingUnits.negate());
    assertThat(getUserFundUnitsReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashRedemptionAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserRedemptionsAccount().getBalance()).isEqualByComparingTo(redemptionAmount);

    assertThat(getFundUnitsOutstandingAccount().getBalance()).isEqualByComparingTo(remainingUnits);
    assertThat(getPayoutsCashClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  @Test
  @DisplayName("Cancelled redemption is not processed by batch job")
  void cancelledRedemption_notProcessedByBatchJob() {
    var initialFundUnits = new BigDecimal("100.00000");
    var redemptionAmount = new BigDecimal("50.00");

    var friday = Instant.parse("2025-09-26T10:00:00Z");
    var monday = Instant.parse("2025-09-29T15:00:00Z");

    // Create redemption on Friday
    ClockHolder.setClock(Clock.fixed(friday, ZoneId.of("UTC")));
    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), redemptionAmount, EUR, VALID_IBAN);
    var requestId = request.getId();

    // Cancel before batch job runs
    redemptionService.cancelRedemption(requestId, testUser.getId());

    // Run batch job on Monday
    ClockHolder.setClock(Clock.fixed(monday, ZoneId.of("UTC")));
    redemptionBatchJob.processDailyRedemptions();

    // Should remain cancelled
    var afterBatch = redemptionRequestRepository.findById(requestId).orElseThrow();
    assertThat(afterBatch.getStatus()).isEqualTo(CANCELLED);

    // Fund units unchanged
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

  private LedgerAccount getPayoutsCashClearingAccount() {
    return ledgerService.getSystemAccount(PAYOUTS_CASH_CLEARING);
  }
}
