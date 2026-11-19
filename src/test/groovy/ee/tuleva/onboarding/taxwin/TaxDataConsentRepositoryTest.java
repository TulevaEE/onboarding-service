package ee.tuleva.onboarding.taxwin;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class TaxDataConsentRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

  @Autowired private TestEntityManager entityManager;
  @Autowired private TaxDataConsentRepository consentRepository;
  @Autowired private TaxIncomeMonthRepository incomeMonthRepository;
  @Autowired private TaxIncomeCorrectionRepository correctionRepository;

  @Test
  void onlyOneRequestAtATimeGetsToTalkToTheConsentService() {
    Long id = consentRepository.save(new TaxDataConsent(user().getId(), NOW)).getIdOrThrow();

    assertThat(consentRepository.claimLease(id, NOW, NOW.plusSeconds(30))).isOne();
    assertThat(consentRepository.claimLease(id, NOW.plusSeconds(10), NOW.plusSeconds(40))).isZero();
    assertThat(consentRepository.claimLease(id, NOW.plusSeconds(31), NOW.plusSeconds(61))).isOne();
  }

  @Test
  void aReleasedLeaseCanBeClaimedAgainAtOnce() {
    Long id = consentRepository.save(new TaxDataConsent(user().getId(), NOW)).getIdOrThrow();
    consentRepository.claimLease(id, NOW, NOW.plusSeconds(30));

    consentRepository.releaseLease(id, NOW.plusSeconds(30));

    assertThat(consentRepository.claimLease(id, NOW.plusSeconds(1), NOW.plusSeconds(31))).isOne();
  }

  @Test
  void aRequestWhoseLeaseRanOutCannotReleaseTheLeaseOfTheRequestAfterIt() {
    Long id = consentRepository.save(new TaxDataConsent(user().getId(), NOW)).getIdOrThrow();
    consentRepository.claimLease(id, NOW, NOW.plusSeconds(30));
    consentRepository.claimLease(id, NOW.plusSeconds(31), NOW.plusSeconds(61));

    consentRepository.releaseLease(id, NOW.plusSeconds(30));

    assertThat(consentRepository.claimLease(id, NOW.plusSeconds(40), NOW.plusSeconds(70))).isZero();
  }

  @Test
  void theNewDeclarationsReplaceTheStoredOnesAndTheCorrectionsForMonthsNowDeclared() {
    User person = user();
    var store = new TaxIncomeStore(incomeMonthRepository, correctionRepository);
    store.replaceDeclared(person.getId(), Map.of(YearMonth.of(2026, 9), declared("2000.00")));
    store.correct(person.getId(), YearMonth.of(2026, 10), new BigDecimal("2100.00"));
    store.correct(person.getId(), YearMonth.of(2026, 11), new BigDecimal("2200.00"));

    store.replaceDeclared(
        person.getId(),
        Map.of(
            YearMonth.of(2026, 9),
            declared("2050.00"),
            YearMonth.of(2026, 10),
            declared("2500.00")));
    entityManager.flush();
    entityManager.clear();

    assertThat(store.declared(person.getId()))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            Map.of(
                YearMonth.of(2026, 9),
                declared("2050.00"),
                YearMonth.of(2026, 10),
                declared("2500.00")));
    assertThat(store.corrections(person.getId())).containsOnlyKeys(YearMonth.of(2026, 11));
  }

  @Test
  void correctingAMonthAgainKeepsOnlyTheLatestAmount() {
    User person = user();
    var store = new TaxIncomeStore(incomeMonthRepository, correctionRepository);

    store.correct(person.getId(), YearMonth.of(2026, 12), new BigDecimal("3000.00"));
    store.correct(person.getId(), YearMonth.of(2026, 12), new BigDecimal("9000.00"));
    entityManager.flush();
    entityManager.clear();

    assertThat(store.corrections(person.getId()).get(YearMonth.of(2026, 12)))
        .isEqualByComparingTo("9000.00");
  }

  private static DeclaredMonth declared(String income) {
    return new DeclaredMonth(new BigDecimal(income), new BigDecimal("72.00"));
  }

  private User user() {
    User user = new User();
    user.setPersonalCode("38001085718");
    user.setFirstName("Näidis");
    user.setLastName("Isik");
    return entityManager.persist(user);
  }
}
