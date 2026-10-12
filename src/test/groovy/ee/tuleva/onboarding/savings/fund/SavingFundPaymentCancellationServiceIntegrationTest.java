package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.deadline.EstonianClockConfiguration;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  SavingFundPaymentCancellationService.class,
  SavingFundPaymentRepository.class,
  SavingFundDeadlinesService.class,
  PublicHolidays.class,
  ClockConfig.class,
  EstonianClockConfiguration.class
})
class SavingFundPaymentCancellationServiceIntegrationTest {

  private static final Instant NOW = Instant.parse("2025-10-01T12:00:00Z");
  private static final String OPS_REASON = "Unsupported payment provider";
  private static final PartyId PARTY = new PartyId(PERSON, "38888888888");

  @Autowired private SavingFundPaymentRepository repository;
  @Autowired private SavingFundPaymentCancellationService cancellationService;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneId.of("UTC")));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void cancelPayment_beforeDeadline_marksPaymentCancelled() {
    var paymentId = saveAttributedPayment();

    var cancelled = cancellationService.cancelPayment(paymentId, OPS_REASON);

    assertThat(cancelled.getId()).isEqualTo(paymentId);
    assertThat(cancelled.getCancelledAt()).isNotNull();
    assertThat(cancelled.getReturnReason()).isEqualTo(OPS_REASON);
    var stored = repository.findById(paymentId).orElseThrow();
    assertThat(stored.getCancelledAt()).isNotNull();
    assertThat(stored.getReturnReason()).isEqualTo(OPS_REASON);
  }

  @Test
  void userCancelPayment_beforeDeadline_recordsCustomersRequestAsReturnReason() {
    var paymentId = saveAttributedPayment();

    cancellationService.cancelPayment(PARTY, paymentId);

    var stored = repository.findById(paymentId).orElseThrow();
    assertThat(stored.getCancelledAt()).isNotNull();
    assertThat(stored.getReturnReason()).isEqualTo("Kasutaja soovil");
  }

  @Test
  void userCancelPayment_ofManuallyAttributedPayment_replacesStaleReturnReason() {
    var paymentId = repository.savePaymentData(payment().build());
    repository.changeStatus(paymentId, SavingFundPayment.Status.RECEIVED);
    repository.changeStatus(paymentId, SavingFundPayment.Status.TO_BE_RETURNED);
    repository.addReturnReason(paymentId, "isik ei ole Tuleva klient");
    repository.attributeManually(paymentId, PARTY);

    cancellationService.cancelPayment(PARTY, paymentId);

    var stored = repository.findById(paymentId).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(SavingFundPayment.Status.VERIFIED);
    assertThat(stored.getCancelledAt()).isNotNull();
    assertThat(stored.getReturnReason()).isEqualTo("Kasutaja soovil");
  }

  @Test
  void cancelPayment_afterDeadline_leavesPaymentUncancelled() {
    var paymentId = saveAttributedPayment();
    ClockHolder.setClock(Clock.fixed(NOW.plus(Duration.ofDays(1)), ZoneId.of("UTC")));

    assertThatThrownBy(() -> cancellationService.cancelPayment(paymentId, OPS_REASON))
        .isInstanceOf(PaymentCancellationRefusedException.class);
    assertThat(repository.findById(paymentId).orElseThrow().getCancelledAt()).isNull();
  }

  @Test
  void cancelPayment_whenStatusIsNotCancellable_leavesPaymentUncancelled() {
    var paymentId = saveAttributedPayment();
    repository.changeStatus(paymentId, SavingFundPayment.Status.RECEIVED);
    repository.changeStatus(paymentId, SavingFundPayment.Status.FROZEN);

    assertThatThrownBy(() -> cancellationService.cancelPayment(paymentId, OPS_REASON))
        .isInstanceOf(PaymentCancellationRefusedException.class);
    assertThat(repository.findById(paymentId).orElseThrow().getCancelledAt()).isNull();
  }

  @Test
  void cancelPayment_whenAlreadyCancelled_keepsOriginalCancelledAt() {
    var paymentId = saveAttributedPayment();
    repository.cancel(paymentId, "Kasutaja soovil");
    var originalCancelledAt = repository.findById(paymentId).orElseThrow().getCancelledAt();

    assertThatThrownBy(() -> cancellationService.cancelPayment(paymentId, OPS_REASON))
        .isInstanceOf(PaymentCancellationRefusedException.class);
    assertThat(repository.findById(paymentId).orElseThrow().getCancelledAt())
        .isEqualTo(originalCancelledAt);
  }

  @Test
  void cancelPayment_whenUnattributed_leavesPaymentUncancelled() {
    var paymentId = repository.savePaymentData(payment().build());

    assertThatThrownBy(() -> cancellationService.cancelPayment(paymentId, OPS_REASON))
        .isInstanceOf(PaymentCancellationRefusedException.class);
    assertThat(repository.findById(paymentId).orElseThrow().getCancelledAt()).isNull();
  }

  private UUID saveAttributedPayment() {
    var paymentId = repository.savePaymentData(payment().build());
    repository.attachParty(paymentId, PARTY);
    return paymentId;
  }

  private SavingFundPayment.SavingFundPaymentBuilder payment() {
    return SavingFundPayment.builder()
        .amount(new BigDecimal("100.50"))
        .currency(EUR)
        .description("Test payment")
        .remitterIban("EE471000001020145685")
        .remitterIdCode(PARTY.code())
        .remitterName("Mari Maasikas")
        .beneficiaryIban("EE442200221092874625")
        .beneficiaryIdCode("14118923")
        .beneficiaryName("TULEVA FONDID AS")
        .externalId("2025100112345-1")
        .receivedBefore(Instant.parse("2025-10-01T09:00:00Z"));
  }
}
