package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.AWAITING_SIGNATURE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class TaxDataRetentionJobTest {

  private static final Instant NOW = Instant.parse("2026-11-18T01:30:00Z");

  @Autowired private TestEntityManager entityManager;
  @Autowired private TaxDataConsentRepository consentRepository;
  @Autowired private TaxIncomeMonthRepository incomeMonthRepository;
  @Autowired private TaxIncomeCorrectionRepository correctionRepository;

  private TaxDataRetentionJob job;
  private TaxIncomeStore incomeStore;

  @BeforeEach
  void setUp() {
    incomeStore = new TaxIncomeStore(incomeMonthRepository, correctionRepository);
    job =
        new TaxDataRetentionJob(
            consentRepository,
            incomeMonthRepository,
            correctionRepository,
            Clock.fixed(NOW, ZoneId.of("Europe/Tallinn")));
  }

  @Test
  void deletesAConsentLeftUnsignedForMoreThanADay() {
    Long abandoned = awaitingSignature(user("38001085718"), NOW.minus(Duration.ofHours(25)));
    Long fresh = awaitingSignature(user("39901019992"), NOW.minus(Duration.ofHours(23)));

    job.deleteExpiredData();

    assertThat(consentRepository.findById(abandoned)).isEmpty();
    assertThat(consentRepository.findById(fresh))
        .get()
        .extracting(TaxDataConsent::getStatus)
        .isEqualTo(AWAITING_SIGNATURE);
  }

  @Test
  void keepsTheIncomeOfAPersonWhoseConsentWasConfirmedWithinThirtyDays() {
    User person = user("38001085718");
    approved(person, NOW.minus(Duration.ofDays(29)));
    storeIncome(person);

    job.deleteExpiredData();

    assertThat(incomeStore.declared(person.getId())).hasSize(1);
    assertThat(incomeStore.corrections(person.getId())).hasSize(1);
  }

  @Test
  void deletesTheIncomeOfAPersonWhoWithdrewAndNeverCameBack() {
    User person = user("38001085718");
    approved(person, NOW.minus(Duration.ofDays(31)));
    storeIncome(person);

    job.deleteExpiredData();

    assertThat(incomeStore.declared(person.getId())).isEmpty();
    assertThat(incomeStore.corrections(person.getId())).isEmpty();
  }

  @Test
  void deletesIncomeLeftBehindWithoutAConsent() {
    User person = user("38001085718");
    storeIncome(person);

    job.deleteExpiredData();

    assertThat(incomeStore.declared(person.getId())).isEmpty();
    assertThat(incomeStore.corrections(person.getId())).isEmpty();
  }

  private Long awaitingSignature(User person, Instant createdAt) {
    var consent = new TaxDataConsent(person.getId(), createdAt);
    consent.awaitSignatureOf(new ConsentDocument("confirm-reference", new byte[] {1}), createdAt);
    return consentRepository.save(consent).getIdOrThrow();
  }

  private void approved(User person, Instant refreshedAt) {
    var consent = new TaxDataConsent(person.getId(), refreshedAt);
    consent.approve("consent-reference");
    consent.refreshed(refreshedAt);
    assertThat(consentRepository.save(consent).getStatus()).isEqualTo(APPROVED);
  }

  private void storeIncome(User person) {
    incomeStore.replaceDeclared(
        person.getId(),
        Map.of(
            YearMonth.of(2026, 10),
            new DeclaredMonth(new BigDecimal("2500.00"), new BigDecimal("90.00"))));
    incomeStore.correct(person.getId(), YearMonth.of(2026, 12), new BigDecimal("3000.00"));
    entityManager.flush();
  }

  private User user(String personalCode) {
    User user = new User();
    user.setPersonalCode(personalCode);
    user.setFirstName("Näidis");
    user.setLastName("Isik");
    return entityManager.persist(user);
  }
}
