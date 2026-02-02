package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import({TestSchedulerLockConfiguration.class, MockSavingsFundLedgerConfiguration.class})
@Transactional
class SavingsFundReservationJobIntegrationTest {

  @Autowired private SavingsFundReservationJob job;
  @Autowired private SavingFundPaymentRepository repository;
  @Autowired private SavingsFundLedger ledger;
  @Autowired private UserService userService;

  // Monday 6th January 2025, 18:00 EET (16:00 UTC) - after 16:00
  private static final Instant NOW = Instant.parse("2025-01-06T16:00:00Z");
  private User user;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneId.of("UTC")));
    user = userService.createNewUser(createSampleUser());
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("processes VERIFIED payment before cutoff and moves to RESERVED")
  void processesPaymentBeforeCutoff() {
    // Cutoff is Monday 16:00 EET (14:00 UTC)
    var beforeCutoff = Instant.parse("2025-01-06T13:00:00Z"); // Monday 15:00 EET

    var paymentId =
        repository.savePaymentData(createPayment().receivedBefore(beforeCutoff).build());
    repository.attachUser(paymentId, user.getId());
    repository.changeStatus(paymentId, RECEIVED);
    repository.changeStatus(paymentId, VERIFIED);

    job.runJob();

    var payments = repository.findAll();
    assertThat(payments).hasSize(1);
    var payment = payments.getFirst();
    assertThat(payment.getStatus()).isEqualTo(RESERVED);
    verify(ledger).reservePaymentForSubscription(eq(user), eq(payment.getAmount()));
  }

  @Test
  @DisplayName("does not process payment after cutoff")
  @Disabled
  void doesNotProcessPaymentAfterCutoff() {
    var afterCutoff = Instant.parse("2025-01-06T15:00:00Z"); // Monday 17:00 EET

    var paymentId = repository.savePaymentData(createPayment().receivedBefore(afterCutoff).build());
    repository.attachUser(paymentId, user.getId());
    repository.changeStatus(paymentId, RECEIVED);
    repository.changeStatus(paymentId, VERIFIED);

    job.runJob();

    var payments = repository.findAll();
    assertThat(payments).hasSize(1);
    var payment = payments.getFirst();
    assertThat(payment.getStatus()).isEqualTo(VERIFIED);
    verify(ledger, never()).reservePaymentForSubscription(any(), any());
  }

  @Test
  @DisplayName("continues processing other payments when ledger fails for one payment")
  void continuesProcessingWhenOnePaymentFails() {
    var beforeCutoff = Instant.parse("2025-01-06T13:00:00Z");

    // Configure mock to throw an exception for amounts of 999.00
    doThrow(new RuntimeException("Ledger error"))
        .when(ledger)
        .reservePaymentForSubscription(
            any(), argThat(amount -> amount.compareTo(new BigDecimal("999.00")) == 0));

    // Payment that will cause ledger to throw an exception
    var invalidPaymentId =
        repository.savePaymentData(
            createPayment().receivedBefore(beforeCutoff).amount(new BigDecimal("999.00")).build());
    repository.attachUser(invalidPaymentId, user.getId());
    repository.changeStatus(invalidPaymentId, RECEIVED);
    repository.changeStatus(invalidPaymentId, VERIFIED);

    // Valid payment with existing user
    var validPaymentId =
        repository.savePaymentData(createPayment().receivedBefore(beforeCutoff).build());
    repository.attachUser(validPaymentId, user.getId());
    repository.changeStatus(validPaymentId, RECEIVED);
    repository.changeStatus(validPaymentId, VERIFIED);

    job.runJob();

    var payments = repository.findAll();
    assertThat(payments).hasSize(2);

    var invalidPayment =
        payments.stream().filter(p -> p.getId().equals(invalidPaymentId)).findFirst().orElseThrow();
    var validPayment =
        payments.stream().filter(p -> p.getId().equals(validPaymentId)).findFirst().orElseThrow();

    // Invalid payment should remain VERIFIED (failed to process)
    assertThat(invalidPayment.getStatus()).isEqualTo(VERIFIED);

    // Valid payment should be RESERVED
    assertThat(validPayment.getStatus()).isEqualTo(RESERVED);

    // Ledger should be called for both payments, but only valid one succeeds
    verify(ledger).reservePaymentForSubscription(eq(user), eq(invalidPayment.getAmount()));
    verify(ledger).reservePaymentForSubscription(eq(user), eq(validPayment.getAmount()));
  }

  private SavingFundPayment.SavingFundPaymentBuilder createPayment() {
    return SavingFundPayment.builder()
        .amount(new BigDecimal("100.00"))
        .currency(Currency.EUR)
        .description("Test payment")
        .remitterIban("EE123456789")
        .remitterName("John Doe")
        .beneficiaryIban("EE987654321")
        .beneficiaryName("Tuleva")
        .createdAt(NOW);
  }

  private User createSampleUser() {
    return User.builder()
        .firstName("John")
        .lastName("Doe")
        .personalCode("38501010002")
        .email("john.doe@example.com")
        .phoneNumber("+372 5555 5555")
        .build();
  }
}
