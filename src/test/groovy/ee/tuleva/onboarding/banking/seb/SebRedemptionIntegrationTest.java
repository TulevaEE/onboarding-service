package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionBatchJob;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionService;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionStatusService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SebIntegrationTest
class SebRedemptionIntegrationTest {

  private static final String SEB_DEPOSIT_IBAN = "EE001234567890123456";
  private static final Instant FRIDAY = Instant.parse("2025-09-26T14:00:00Z");
  private static final Instant TUESDAY = Instant.parse("2025-09-30T15:00:00Z");

  @Autowired RedemptionService redemptionService;
  @Autowired RedemptionStatusService redemptionStatusService;
  @Autowired RedemptionBatchJob redemptionBatchJob;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired UserRepository userRepository;
  @Autowired SavingFundPaymentRepository savingFundPaymentRepository;

  @MockitoBean SebGatewayClient sebGatewayClient;
  @MockitoBean SavingsFundNavProvider navProvider;

  User testUser;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(FRIDAY, ZoneId.of("UTC")));
    when(navProvider.getCurrentNav()).thenReturn(BigDecimal.ONE);
    when(navProvider.getCurrentNavForIssuing()).thenReturn(BigDecimal.ONE);

    testUser =
        userRepository.save(sampleUser().id(null).member(null).personalCode("39901019992").build());
    savingsFundOnboardingRepository.saveOnboardingStatus(
        testUser.getPersonalCode(), SavingsFundOnboardingStatus.COMPLETED);
    setupUserWithFundUnits(new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    setupUserDepositIban(SEB_DEPOSIT_IBAN);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void redemptionBatchJob_withSebIban_callsSebGatewayClient() {
    var redemptionAmount = new BigDecimal("25.00");

    ClockHolder.setClock(Clock.fixed(FRIDAY, ZoneId.of("UTC")));
    var request =
        redemptionService.createRedemptionRequest(
            testUser.getId(), redemptionAmount, EUR, SEB_DEPOSIT_IBAN);

    assertThat(request.getStatus()).isEqualTo(RESERVED);

    redemptionStatusService.changeStatus(request.getId(), VERIFIED);

    ClockHolder.setClock(Clock.fixed(TUESDAY, ZoneId.of("UTC")));
    redemptionBatchJob.runJob();

    verify(sebGatewayClient, times(2)).submitPaymentFile(anyString(), anyString());
  }

  private void setupUserWithFundUnits(BigDecimal cashAmount, BigDecimal fundUnits) {
    var navPerUnit = cashAmount.divide(fundUnits, 5, HALF_UP);
    var paymentId = UUID.randomUUID();
    savingsFundLedger.recordPaymentReceived(testUser, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(testUser, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        testUser, cashAmount, fundUnits, navPerUnit, paymentId);
    savingsFundLedger.transferToFundAccount(cashAmount, paymentId);
  }

  private void setupUserDepositIban(String iban) {
    var payment =
        SavingFundPayment.builder()
            .externalId("TEST-" + UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .currency(EUR)
            .description("Test deposit")
            .remitterIban(iban)
            .remitterIdCode(testUser.getPersonalCode())
            .remitterName(testUser.getFirstName() + " " + testUser.getLastName())
            .beneficiaryIban("EE362200221234567897")
            .beneficiaryIdCode("12345678")
            .beneficiaryName("Tuleva")
            .status(Status.PROCESSED)
            .build();

    var paymentId = savingFundPaymentRepository.savePaymentData(payment);
    savingFundPaymentRepository.attachUser(paymentId, testUser.getId());
    savingFundPaymentRepository.changeStatus(paymentId, Status.RECEIVED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.VERIFIED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.RESERVED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.ISSUED);
    savingFundPaymentRepository.changeStatus(paymentId, Status.PROCESSED);
  }
}
