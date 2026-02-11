package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.CREATED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.PROCESSED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestSchedulerLockConfiguration.class)
@Transactional
class SavingFundPaymentRepositoryTest {

  @Autowired SavingFundPaymentRepository repository;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired UserRepository userRepository;

  @Test
  void savePaymentData() {
    var payment = createPayment().build();

    var id = repository.savePaymentData(payment);

    var payments = repository.findPaymentsWithStatus(CREATED);
    assertThat(payments).hasSize(1);

    var savedPayment = payments.getFirst();
    assertThat(savedPayment)
        .usingRecursiveComparison()
        .ignoringFields("id", "createdAt", "statusChangedAt")
        .isEqualTo(payment);
    assertThat(savedPayment.getId()).isEqualTo(id);
    assertThat(savedPayment.getCreatedAt()).isCloseTo(Instant.now(), within(10, SECONDS));
    assertThat(savedPayment.getStatusChangedAt()).isEqualTo(savedPayment.getCreatedAt());
  }

  @Test
  void updatePaymentData() {
    var originalPayment =
        SavingFundPayment.builder()
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
    assertThat(savedPayment)
        .usingRecursiveComparison()
        .ignoringFields("id", "createdAt", "statusChangedAt")
        .isEqualTo(updatedPayment);
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
    "CREATED, RECEIVED|PROCESSED",
    "RECEIVED, VERIFIED|FROZEN|TO_BE_RETURNED",
    "VERIFIED, RESERVED|TO_BE_RETURNED",
    "RESERVED, ISSUED",
    "ISSUED, PROCESSED",
    "PROCESSED, -",
    "FROZEN, -",
    "TO_BE_RETURNED, RETURNED",
    "RETURNED, -",
  })
  void permittedStatusChanges(Status initialStatus, String permittedNextStatusesString) {
    var id = repository.savePaymentData(createPayment().build());
    var permittedNextStatuses =
        permittedNextStatusesString.equals("-")
            ? List.of()
            : Arrays.stream(permittedNextStatusesString.split("\\|")).map(Status::valueOf).toList();
    for (Status status : Status.values()) {
      updatePaymentStatus(id, initialStatus);
      if (permittedNextStatuses.contains(status))
        assertThatCode(() -> repository.changeStatus(id, status))
            .withFailMessage(initialStatus + " -> " + status + " should be allowed")
            .doesNotThrowAnyException();
      else
        assertThatThrownBy(
                () -> repository.changeStatus(id, status),
                initialStatus + " -> " + status + " should not be allowed")
            .isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  void statusChangedAt() {
    var id = repository.savePaymentData(createPayment().build());
    jdbcTemplate.update(
        "update saving_fund_payment set status_changed_at='2020-01-01'::date", Map.of());

    repository.changeStatus(id, RECEIVED);

    var payments = repository.findPaymentsWithStatus(RECEIVED);

    assertThat(payments.getFirst().getStatusChangedAt())
        .isCloseTo(Instant.now(), within(10, SECONDS));
  }

  @Test
  void findRecentPayments() {
    var id1 =
        repository.savePaymentData(createPayment().externalId("1").description("abc").build());
    var id2 =
        repository.savePaymentData(createPayment().externalId("2").description("abc").build());
    var ignored =
        repository.savePaymentData(createPayment().externalId("3").description("def").build());

    jdbcTemplate.update(
        "update saving_fund_payment set created_at='2025-08-10'::date where id=:id",
        Map.of("id", id1));

    var recentPayments = repository.findRecentPayments("abc");

    assertThat(recentPayments).hasSize(1);
    assertThat(recentPayments).extracting("id").containsExactly(id2);
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"CREATED", "RECEIVED", "VERIFIED"})
  void cancel(Status status) {
    var id = repository.savePaymentData(createPayment().build());
    updatePaymentStatus(id, status);

    repository.cancel(id);

    var payments = repository.findPaymentsWithStatus(status);

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().getCancelledAt()).isCloseTo(Instant.now(), within(10, SECONDS));
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"CREATED", "RECEIVED", "VERIFIED"},
      mode = EXCLUDE)
  void cancel_notAllowed(Status status) {
    var id = repository.savePaymentData(createPayment().build());
    updatePaymentStatus(id, status);

    assertThatThrownBy(() -> repository.cancel(id)).isInstanceOf(RuntimeException.class);

    var payments = repository.findPaymentsWithStatus(status);

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().getCancelledAt()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"CREATED", "RECEIVED"})
  void attachUser(Status status) {
    var userId = createUser();

    var id = repository.savePaymentData(createPayment().build());
    updatePaymentStatus(id, status);

    repository.attachUser(id, userId);

    var payments = repository.findPaymentsWithStatus(status);

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().getUserId()).isEqualTo(userId);
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"CREATED", "RECEIVED"},
      mode = EXCLUDE)
  void attachUser_notAllowed(Status status) {
    var userId = createUser();

    var id = repository.savePaymentData(createPayment().build());
    updatePaymentStatus(id, status);

    assertThatThrownBy(() -> repository.attachUser(id, userId))
        .isInstanceOf(RuntimeException.class);

    var payments = repository.findPaymentsWithStatus(status);

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().getUserId()).isNull();
  }

  @Test
  @Transactional(propagation = NOT_SUPPORTED)
  void attachUser_unknownUser() {
    var id = repository.savePaymentData(createPayment().build());

    try {
      assertThatThrownBy(() -> repository.attachUser(id, 123L))
          .isInstanceOf(RuntimeException.class);

      var payments = repository.findPaymentsWithStatus(CREATED);

      assertThat(payments).hasSize(1);
      assertThat(payments.getFirst().getUserId()).isNull();
    } finally {
      jdbcTemplate.update("DELETE FROM saving_fund_payment WHERE id=:id", Map.of("id", id));
    }
  }

  @Test
  void findUserPayments() {
    var id1 = repository.savePaymentData(createPayment().externalId("1").build());
    var id2 = repository.savePaymentData(createPayment().externalId("2").build());
    var id3 = repository.savePaymentData(createPayment().externalId("3").build());
    var ignoredId = repository.savePaymentData(createPayment().externalId("4").build());

    var user1 = createUser("37706154772");
    var user2 = createUser("36407145233");

    repository.attachUser(id1, user1);
    repository.attachUser(id2, user1);
    repository.attachUser(id3, user2);

    repository.changeStatus(id1, RECEIVED);

    assertThat(repository.findUserPayments(user1)).hasSize(2);
    assertThat(repository.findUserPayments(user1))
        .extracting("id")
        .containsExactlyInAnyOrder(id1, id2);
    assertThat(repository.findUserPayments(user2)).hasSize(1);
    assertThat(repository.findUserPayments(user2)).extracting("id").containsExactlyInAnyOrder(id3);
  }

  @Test
  void returnReason() {
    var id = repository.savePaymentData(createPayment().build());

    repository.addReturnReason(id, "not ok");

    var payments = repository.findPaymentsWithStatus(CREATED);

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().getReturnReason()).isEqualTo("not ok");
  }

  @Test
  void findByExternalId() {
    var id1 = repository.savePaymentData(createPayment().externalId("ext-123").build());
    var id2 = repository.savePaymentData(createPayment().externalId("ext-456").build());

    var result1 = repository.findByExternalId("ext-123");
    assertThat(result1).isPresent();
    assertThat(result1.get().getId()).isEqualTo(id1);
    assertThat(result1.get().getExternalId()).isEqualTo("ext-123");

    var result2 = repository.findByExternalId("ext-456");
    assertThat(result2).isPresent();
    assertThat(result2.get().getId()).isEqualTo(id2);

    var result3 = repository.findByExternalId("non-existent");
    assertThat(result3).isEmpty();
  }

  @Test
  void findAll() {
    assertThat(repository.findAll()).isEmpty();

    var id1 = repository.savePaymentData(createPayment().externalId("1").build());
    var id2 = repository.savePaymentData(createPayment().externalId("2").build());
    var id3 = repository.savePaymentData(createPayment().externalId("3").build());

    var allPayments = repository.findAll();
    assertThat(allPayments).hasSize(3);
    assertThat(allPayments).extracting("id").containsExactlyInAnyOrder(id1, id2, id3);
  }

  @Test
  void findUserDepositBankAccountIbans() {
    var user1 = createUser("37706154772");
    var user2 = createUser("36407145233");

    var id1 =
        repository.savePaymentData(
            createPayment().externalId("1").remitterIban("EE111111111111111111").build());
    var id2 =
        repository.savePaymentData(
            createPayment().externalId("2").remitterIban("EE222222222222222222").build());
    var id3 =
        repository.savePaymentData(
            createPayment().externalId("3").remitterIban("EE333333333333333333").build());
    var id4 =
        repository.savePaymentData(
            createPayment().externalId("4").remitterIban("EE111111111111111111").build());
    var id5 =
        repository.savePaymentData(
            createPayment().externalId("5").remitterIban("EE444444444444444444").build());
    var id6 =
        repository.savePaymentData(
            createPayment().externalId("6").remitterIban("EE555555555555555555").build());
    var id7 =
        repository.savePaymentData(
            createPayment()
                .externalId("7")
                .remitterIban("EE666666666666666666")
                .amount(new BigDecimal("-50.00"))
                .build());

    repository.attachUser(id1, user1);
    repository.attachUser(id2, user1);
    repository.attachUser(id3, user1);
    repository.attachUser(id4, user1);
    repository.attachUser(id5, user1);
    repository.attachUser(id6, user2);
    repository.attachUser(id7, user1);

    updatePaymentStatus(id1, RESERVED);
    updatePaymentStatus(id2, ISSUED);
    updatePaymentStatus(id3, PROCESSED);
    updatePaymentStatus(id4, RESERVED);
    updatePaymentStatus(id5, RECEIVED);
    updatePaymentStatus(id6, RESERVED);
    updatePaymentStatus(id7, PROCESSED);

    var result = repository.findUserDepositBankAccountIbans(user1);

    assertThat(result)
        .containsExactly("EE111111111111111111", "EE222222222222222222", "EE333333333333333333");
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
        .externalId("abc123");
  }

  private int updatePaymentStatus(UUID paymentId, Status initialStatus) {
    return jdbcTemplate.update(
        "update saving_fund_payment set status=:status where id=:id",
        Map.of("status", initialStatus.name(), "id", paymentId));
  }

  private Long createUser() {
    return createUser("48806046007");
  }

  private Long createUser(String personalCode) {
    return userRepository
        .save(User.builder().firstName("John").lastName("Smith").personalCode(personalCode).build())
        .getId();
  }

  @Test
  void findUnmatchedOutgoingReturns_excludesInternalTransfers() {
    var regularReturn =
        repository.savePaymentData(
            createPayment()
                .externalId("regular-return")
                .remitterName("Tuleva Fondid AS")
                .beneficiaryName("Customer Name")
                .amount(new BigDecimal("-100.00"))
                .build());
    var internalTransfer =
        repository.savePaymentData(
            createPayment()
                .externalId("internal-transfer")
                .remitterName("Tuleva Fondid AS")
                .beneficiaryName("Tuleva Fondid AS")
                .amount(new BigDecimal("-530000.00"))
                .build());
    var incomingPayment =
        repository.savePaymentData(
            createPayment().externalId("incoming").amount(new BigDecimal("50.00")).build());

    updatePaymentStatus(regularReturn, PROCESSED);
    updatePaymentStatus(internalTransfer, PROCESSED);
    updatePaymentStatus(incomingPayment, PROCESSED);

    var unmatchedReturns = repository.findUnmatchedOutgoingReturns();

    assertThat(unmatchedReturns).extracting("id").containsExactly(regularReturn);
  }
}
