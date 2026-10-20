package ee.tuleva.onboarding.payment.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class RecurringPaymentConfirmationRepositoryTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final String ANOTHER_PERSONAL_CODE = "48888888888";
  private static final Instant SINCE = Instant.parse("2026-06-30T21:00:00Z");

  @Autowired private RecurringPaymentConfirmationRepository repository;

  @Test
  void findsAConfirmationMadeAtTheMomentAskedAbout() {
    repository.save(new RecurringPaymentConfirmation(PERSONAL_CODE, SINCE));

    assertThat(repository.existsByPersonalCodeAndConfirmedAtGreaterThanEqual(PERSONAL_CODE, SINCE))
        .isTrue();
  }

  @Test
  void doesNotFindAConfirmationMadeBeforeTheMomentAskedAbout() {
    repository.save(new RecurringPaymentConfirmation(PERSONAL_CODE, SINCE.minusSeconds(1)));

    assertThat(repository.existsByPersonalCodeAndConfirmedAtGreaterThanEqual(PERSONAL_CODE, SINCE))
        .isFalse();
  }

  @Test
  void doesNotFindAnotherPersonsConfirmation() {
    repository.save(new RecurringPaymentConfirmation(ANOTHER_PERSONAL_CODE, SINCE));

    assertThat(repository.existsByPersonalCodeAndConfirmedAtGreaterThanEqual(PERSONAL_CODE, SINCE))
        .isFalse();
  }
}
