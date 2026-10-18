package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.Insurer.ERGO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class InsuranceTransferFormRetentionJobTest {

  private static final Instant NOW = Instant.parse("2026-10-18T01:15:00Z");

  @Autowired private TestEntityManager entityManager;
  @Autowired private InsuranceTransferFormRepository repository;

  private InsuranceTransferFormRetentionJob job;
  private Long userId;

  @BeforeEach
  void setUp() {
    job = new InsuranceTransferFormRetentionJob(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    User user = new User();
    user.setPersonalCode("38001085718");
    user.setFirstName("Jaan");
    user.setLastName("Õunapuu");
    userId = entityManager.persist(user).getId();
  }

  @Test
  void clearsTheDocumentsOfAFormSignedMoreThan90DaysAgoAndKeepsTheRow() {
    Long id = signedForm(NOW.minus(Duration.ofDays(91)));

    job.clearExpiredDocuments();

    var form = repository.findById(id).orElseThrow();
    assertThat(form.getFilledPdf()).isNull();
    assertThat(form.getSignedContainer()).isNull();
    assertThat(form.getStatus()).isEqualTo(InsuranceTransferFormStatus.SIGNED);
  }

  @Test
  void keepsTheDocumentsOfAFormSigned90DaysAgoOrLater() {
    Long id = signedForm(NOW.minus(Duration.ofDays(90)));

    job.clearExpiredDocuments();

    var form = repository.findById(id).orElseThrow();
    assertThat(form.getFilledPdf()).containsExactly(1);
    assertThat(form.getSignedContainer()).containsExactly(2);
  }

  @Test
  void clearsTheFilledFormOfAnUnsignedFormCreatedMoreThan7DaysAgoAndKeepsTheRow() {
    Long id = unsignedForm(NOW.minus(Duration.ofDays(8)));

    job.clearExpiredDocuments();

    var form = repository.findById(id).orElseThrow();
    assertThat(form.getFilledPdf()).isNull();
    assertThat(form.getStatus()).isEqualTo(InsuranceTransferFormStatus.CREATED);
  }

  @Test
  void keepsTheFilledFormOfAnUnsignedFormCreatedWithinTheLast7Days() {
    Long id = unsignedForm(NOW.minus(Duration.ofDays(6)));

    job.clearExpiredDocuments();

    assertThat(repository.findById(id).orElseThrow().getFilledPdf()).containsExactly(1);
  }

  private Long signedForm(Instant signedAt) {
    var form =
        new InsuranceTransferForm(
            userId, ERGO, "993432432", new byte[] {1}, signedAt.minus(Duration.ofMinutes(5)));
    form.sign(new byte[] {2}, signedAt);
    return persisted(form);
  }

  private Long unsignedForm(Instant createdAt) {
    return persisted(
        new InsuranceTransferForm(userId, ERGO, "993432432", new byte[] {1}, createdAt));
  }

  private Long persisted(InsuranceTransferForm form) {
    Long id = repository.save(form).getIdOrThrow();
    entityManager.flush();
    entityManager.clear();
    return id;
  }
}
