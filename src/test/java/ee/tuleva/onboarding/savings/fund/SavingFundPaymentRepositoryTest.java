package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.CREATED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status;

@SpringBootTest
@Transactional
class SavingFundPaymentRepositoryTest {

  @Autowired SavingFundPaymentRepository repository;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void savePaymentData() {
    var payment = createPayment().build();

    var id = repository.savePaymentData(payment);

    var payments = repository.findPaymentsWithStatus(CREATED);
    assertThat(payments).hasSize(1);

    var savedPayment = payments.getFirst();
    assertThat(savedPayment).usingRecursiveComparison().ignoringFields("id", "createdAt", "statusChangedAt").isEqualTo(payment);
    assertThat(savedPayment.getId()).isEqualTo(id);
    assertThat(savedPayment.getCreatedAt()).isCloseTo(Instant.now(), within(10, SECONDS));
    assertThat(savedPayment.getStatusChangedAt()).isEqualTo(savedPayment.getCreatedAt());
  }

  @Test
  void updatePaymentData() {
    var originalPayment = SavingFundPayment.builder()
        .remitterName("John")
        .remitterIban("IBAN-1")
        .beneficiaryName("Jane")
        .beneficiaryIban("IBAN-2")
        .amount(new BigDecimal("100.70"))
        .description("my money")
        .build();
    var id = repository.savePaymentData(originalPayment);

    var payments1 = repository.findPaymentsWithStatus(CREATED);

    var updatedPayment = createPayment().build();
    repository.updatePaymentData(id, updatedPayment);

    var payments2 = repository.findPaymentsWithStatus(CREATED);
    assertThat(payments2).hasSize(1);

    var savedPayment = payments2.getFirst();
    assertThat(savedPayment).usingRecursiveComparison().ignoringFields("id", "createdAt", "statusChangedAt").isEqualTo(updatedPayment);
    assertThat(savedPayment.getId()).isEqualTo(id);
    assertThat(savedPayment.getCreatedAt()).isCloseTo(Instant.now(), within(10, SECONDS));
    assertThat(savedPayment.getCreatedAt()).isEqualTo(payments1.getFirst().getCreatedAt());
  }

  @Test
  void changeStatus() {
    var id1 = repository.savePaymentData(createPayment().externalId("11").build());
    var id2 = repository.savePaymentData(createPayment().externalId("22").build());

    assertThat(repository.findPaymentsWithStatus(CREATED)).hasSize(2);
    assertThat(repository.findPaymentsWithStatus(RECEIVED)).hasSize(0);
    assertThat(repository.findPaymentsWithStatus(RESERVED)).hasSize(0);

    repository.changeStatus(id1, RECEIVED);

    assertThat(repository.findPaymentsWithStatus(CREATED)).hasSize(1);
    assertThat(repository.findPaymentsWithStatus(RECEIVED)).hasSize(1);
    assertThat(repository.findPaymentsWithStatus(VERIFIED)).hasSize(0);

    repository.changeStatus(id1, VERIFIED);
    repository.changeStatus(id2, RECEIVED);

    assertThat(repository.findPaymentsWithStatus(CREATED)).hasSize(0);
    assertThat(repository.findPaymentsWithStatus(RECEIVED)).hasSize(1);
    assertThat(repository.findPaymentsWithStatus(VERIFIED)).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource({
      "CREATED, RECEIVED",
      "RECEIVED, VERIFIED|FROZEN|TO_BE_RETURNED",
      "VERIFIED, RESERVED|TO_BE_RETURNED",
      "RESERVED, PROCESSED",
      "PROCESSED, -",
      "FROZEN, -",
      "TO_BE_RETURNED, RETURNED",
      "RETURNED, -",
  })
  void permittedStatusChanges(Status initialStatus, String permittedNextStatusesString) {
    var id = repository.savePaymentData(createPayment().build());
    var permittedNextStatuses = permittedNextStatusesString.equals("-") ? List.of() :
        Arrays.stream(permittedNextStatusesString.split("\\|")).map(Status::valueOf).toList();
    for (Status status : Status.values()) {
      updatePaymentStatus(id, initialStatus);
      if (permittedNextStatuses.contains(status))
        assertThatCode(() -> repository.changeStatus(id, status))
            .withFailMessage(initialStatus + " -> " + status + " should be allowed")
            .doesNotThrowAnyException();
      else
        assertThatThrownBy(() -> repository.changeStatus(id, status),
            initialStatus + " -> " + status + " should not be allowed")
            .isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  void statusChangedAt() {
    var id = repository.savePaymentData(createPayment().build());
    jdbcTemplate.update("update saving_fund_payment set status_changed_at='2020-01-01'::date", Map.of());

    repository.changeStatus(id, RECEIVED);

    var payments = repository.findPaymentsWithStatus(RECEIVED);

    assertThat(payments.getFirst().getStatusChangedAt()).isCloseTo(Instant.now(), within(10, SECONDS));
  }

  @Test
  void findRecentPayments() {
    var id1 = repository.savePaymentData(createPayment().externalId("1").description("abc").build());
    var id2 = repository.savePaymentData(createPayment().externalId("2").description("abc").build());
    var ignored = repository.savePaymentData(createPayment().externalId("3").description("def").build());

    jdbcTemplate.update("update saving_fund_payment set created_at='2025-08-10'::date where id=:id",
        Map.of("id", id1));

    var recentPayments = repository.findRecentPayments("abc");

    assertThat(recentPayments).hasSize(1);
    assertThat(recentPayments).extracting("id").containsExactly(id2);
  }

  @ParameterizedTest
  @EnumSource(value = Status.class, names = {"CREATED", "RECEIVED", "VERIFIED"})
  void cancel(Status status) {
    var id = repository.savePaymentData(createPayment().build());
    updatePaymentStatus(id, status);

    repository.cancel(id);

    var payments = repository.findPaymentsWithStatus(status);

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().getCancelledAt()).isCloseTo(Instant.now(), within(10, SECONDS));
  }

  @ParameterizedTest
  @EnumSource(value = Status.class, names = {"CREATED", "RECEIVED", "VERIFIED"}, mode = EXCLUDE)
  void cancel_notAllowed(Status status) {
    var id = repository.savePaymentData(createPayment().build());
    updatePaymentStatus(id, status);

    assertThatThrownBy(() -> repository.cancel(id)).isInstanceOf(RuntimeException.class);

    var payments = repository.findPaymentsWithStatus(status);

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().getCancelledAt()).isNull();
  }

  @Test
  void returnReason() {
    var id = repository.savePaymentData(createPayment().build());

    repository.addReturnReason(id, "not ok");

    var payments = repository.findPaymentsWithStatus(CREATED);

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().getReturnReason()).isEqualTo("not ok");
  }

  private SavingFundPayment.SavingFundPaymentBuilder createPayment() {
    return SavingFundPayment.builder()
        .remitterName("John Doe")
        .remitterIdCode("12345")
        .remitterIban("IBAN-1")
        .beneficiaryName("Jane Smith")
        .beneficiaryIdCode("67890")
        .beneficiaryIban("IBAN-2")
        .amount(new BigDecimal("100.70"))
        .description("my money")
        .externalId("abc123")
        ;
  }

  private int updatePaymentStatus(UUID paymentId, Status initialStatus) {
    return jdbcTemplate.update("update saving_fund_payment set status=:status where id=:id",
        Map.of("status", initialStatus.name(), "id", paymentId));
  }
}
