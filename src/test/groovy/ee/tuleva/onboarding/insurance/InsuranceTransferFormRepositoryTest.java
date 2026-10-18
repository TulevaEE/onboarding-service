package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.Insurer.COMPENSA;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class InsuranceTransferFormRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-10-18T08:00:00Z");

  @Autowired private TestEntityManager entityManager;
  @Autowired private InsuranceTransferFormRepository repository;

  @Test
  void keepsTheFilledFormAndThePensionAccountNumberItWasFilledWith() {
    User owner = user("38001085718");
    byte[] pdf = {1, 2, 3};
    Long id =
        repository
            .save(new InsuranceTransferForm(owner.getId(), COMPENSA, "993432432", pdf, CREATED_AT))
            .getIdOrThrow();
    entityManager.flush();
    entityManager.clear();

    var form = repository.findByIdAndUserId(id, owner.getId()).orElseThrow();

    assertThat(form)
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(
            new InsuranceTransferForm(owner.getId(), COMPENSA, "993432432", pdf, CREATED_AT));
  }

  @Test
  void findsAFormOnlyForItsOwner() {
    User owner = user("38001085718");
    User someoneElse = user("39901019992");
    Long id =
        repository
            .save(
                new InsuranceTransferForm(
                    owner.getId(), COMPENSA, "993432432", new byte[] {1}, CREATED_AT))
            .getIdOrThrow();

    assertThat(repository.findByIdAndUserId(id, someoneElse.getId())).isEmpty();
  }

  @Test
  void summarizesAFormForItsOwnerOnly() {
    User owner = user("38001085718");
    User someoneElse = user("39901019992");
    var form =
        repository.save(
            new InsuranceTransferForm(
                owner.getId(), COMPENSA, "993432432", new byte[] {1}, CREATED_AT));
    form.sign(new byte[] {9}, CREATED_AT.plusSeconds(60));
    form.recordEmailStatus(InsuranceTransferEmailStatus.SENT);
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findSummaryByIdAndUserId(form.getIdOrThrow(), owner.getId()))
        .contains(
            new InsuranceTransferFormSummary(
                form.getIdOrThrow(),
                COMPENSA,
                CREATED_AT.plusSeconds(60),
                InsuranceTransferEmailStatus.SENT));
    assertThat(repository.findSummaryByIdAndUserId(form.getIdOrThrow(), someoneElse.getId()))
        .isEmpty();
  }

  @Test
  void recordsTheSignedContainerAndTheEmailOutcome() {
    User owner = user("38001085718");
    var form =
        repository.save(
            new InsuranceTransferForm(
                owner.getId(), COMPENSA, "993432432", new byte[] {1}, CREATED_AT));
    form.sign(new byte[] {9}, CREATED_AT.plusSeconds(60));
    form.recordEmailStatus(InsuranceTransferEmailStatus.SENT);
    entityManager.flush();
    entityManager.clear();

    var signed = repository.findForUpdateById(form.getIdOrThrow()).orElseThrow();

    assertThat(signed.isSigned()).isTrue();
    assertThat(signed.getSignedContainer()).containsExactly(9);
    assertThat(signed.getSignedAt()).isEqualTo(CREATED_AT.plusSeconds(60));
    assertThat(signed.getEmailStatus()).isEqualTo(InsuranceTransferEmailStatus.SENT);
  }

  private User user(String personalCode) {
    User user = new User();
    user.setPersonalCode(personalCode);
    user.setFirstName("Jaan");
    user.setLastName("Õunapuu");
    return entityManager.persist(user);
  }
}
