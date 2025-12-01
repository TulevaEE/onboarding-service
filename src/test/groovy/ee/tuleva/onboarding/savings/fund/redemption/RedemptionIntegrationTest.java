package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class RedemptionIntegrationTest {

  @Autowired RedemptionService redemptionService;
  @Autowired RedemptionStatusService redemptionStatusService;
  @Autowired RedemptionRequestRepository redemptionRequestRepository;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired LedgerService ledgerService;
  @Autowired UserRepository userRepository;

  User testUser;

  @BeforeEach
  void setUp() {
    testUser =
        userRepository.save(sampleUser().id(null).member(null).personalCode("39901019992").build());
    savingsFundOnboardingRepository.completeOnboarding(testUser.getId());
    setupUserWithFundUnits(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
  }

  @Test
  void createRedemptionRequest_createsRequestInPendingStatus() {
    var fundUnits = new BigDecimal("10.00000");
    var customerIban = "EE123456789012345678";

    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), fundUnits, customerIban);

    assertThat(request.getStatus()).isEqualTo(PENDING);
    assertThat(request.getFundUnits()).isEqualByComparingTo(fundUnits);
    assertThat(request.getCustomerIban()).isEqualTo(customerIban);
    assertThat(request.getUserId()).isEqualTo(testUser.getId());
    assertThat(request.getRequestedAt()).isNotNull();
  }

  @Test
  void createRedemptionRequest_failsWhenInsufficientUnits() {
    var fundUnits = new BigDecimal("200.00000");
    var customerIban = "EE123456789012345678";

    assertThatThrownBy(
            () ->
                redemptionService.createRedemptionRequest(
                    testUser.getId(), fundUnits, customerIban))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Insufficient fund units");
  }

  @Test
  void cancelRedemption_cancelsRequestBeforeCutoff() {
    var fundUnits = new BigDecimal("10.00000");
    var customerIban = "EE123456789012345678";
    var request =
        redemptionService.createRedemptionRequest(testUser.getId(), fundUnits, customerIban);

    redemptionService.cancelRedemption(request.getId(), testUser.getId());

    var cancelled = redemptionRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(CANCELLED);
    assertThat(cancelled.getCancelledAt()).isNotNull();
  }

  @Test
  void getUserRedemptions_returnsUserRedemptions() {
    redemptionService.createRedemptionRequest(
        testUser.getId(), new BigDecimal("5.00000"), "EE111111111111111111");
    redemptionService.createRedemptionRequest(
        testUser.getId(), new BigDecimal("3.00000"), "EE222222222222222222");

    var redemptions = redemptionService.getUserRedemptions(testUser.getId());

    assertThat(redemptions).hasSize(2);
  }

  @Test
  void reserveUnits_locksUnitsInLedger() {
    var fundUnits = new BigDecimal("10.00000");
    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), fundUnits, "EE123456789012345678");

    savingsFundLedger.reserveFundUnitsForRedemption(testUser, fundUnits);
    redemptionStatusService.changeStatus(request.getId(), RESERVED);

    var reserved = redemptionRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(reserved.getStatus()).isEqualTo(RESERVED);

    var reservedUnitsBalance =
        ledgerService.getUserAccount(testUser, FUND_UNITS_RESERVED).getBalance();
    assertThat(reservedUnitsBalance).isEqualByComparingTo(fundUnits.negate());
  }

  @Test
  void fullRedemptionFlow_allBalancesCorrect() {
    var fundUnits = new BigDecimal("10.00000");
    var navPerUnit = new BigDecimal("10.00");
    var cashAmount = new BigDecimal("100.00");
    var customerIban = "EE123456789012345678";

    savingsFundLedger.reserveFundUnitsForRedemption(testUser, fundUnits);
    savingsFundLedger.redeemFundUnitsFromReserved(testUser, fundUnits, cashAmount, navPerUnit);
    savingsFundLedger.transferFromFundAccount(cashAmount);
    savingsFundLedger.recordRedemptionPayout(testUser, cashAmount, customerIban);

    var fundsUnitsBalance = ledgerService.getUserAccount(testUser, FUND_UNITS).getBalance();
    var reservedUnitsBalance =
        ledgerService.getUserAccount(testUser, FUND_UNITS_RESERVED).getBalance();
    var redemptionsBalance = ledgerService.getUserAccount(testUser, REDEMPTIONS).getBalance();

    assertThat(fundsUnitsBalance.negate()).isEqualByComparingTo(new BigDecimal("90.00000"));
    assertThat(reservedUnitsBalance).isEqualByComparingTo(ZERO);
    assertThat(redemptionsBalance).isEqualByComparingTo(cashAmount);
  }

  private void setupUserWithFundUnits(BigDecimal cashAmount, BigDecimal fundUnits) {
    var navPerUnit = cashAmount.divide(fundUnits, 5, RoundingMode.HALF_UP);
    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, UUID.randomUUID());
    savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount);
    savingsFundLedger.issueFundUnitsFromReserved(testUser, cashAmount, fundUnits, navPerUnit);
    savingsFundLedger.transferToFundAccount(cashAmount);
  }
}
