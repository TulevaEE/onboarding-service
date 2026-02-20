package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class MandateBatchRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private MandateBatchRepository repository;

  @Test
  @DisplayName("should persist and findById should work")
  void shouldSaveMandateBatch() {

    var initialMandateBatchStatus = INITIALIZED;
    User user =
        User.builder()
            .firstName("Sander")
            .lastName("Nemvalts")
            .personalCode("39907052754")
            .email("sander.nemvalts@tuleva.ee")
            .phoneNumber("5555555")
            .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
            .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
            .active(true)
            .build();
    entityManager.persistAndFlush(user);

    var fundPensionOpeningMandate = MandateFixture.sampleFundPensionOpeningMandate();
    var partialWithdrawalMandate = MandateFixture.samplePartialWithdrawalMandate();

    fundPensionOpeningMandate.setId(null);
    fundPensionOpeningMandate.setUser(user);
    partialWithdrawalMandate.setId(null);
    partialWithdrawalMandate.setUser(user);

    entityManager.persistAndFlush(fundPensionOpeningMandate);
    entityManager.persistAndFlush(partialWithdrawalMandate);

    var mandateBatch =
        MandateBatch.builder()
            .status(initialMandateBatchStatus)
            .mandates(List.of(fundPensionOpeningMandate, partialWithdrawalMandate))
            .build();

    entityManager.persistAndFlush(mandateBatch);

    var foundMandateBatch = repository.findById(mandateBatch.getId()).orElseThrow();

    assertThat(foundMandateBatch.getStatus() == initialMandateBatchStatus).isTrue();

    var mandatesFromBatch = foundMandateBatch.getMandates();

    var fundPensionMandateInBatch =
        mandatesFromBatch.stream()
            .filter(mandate -> mandate.getMandateType().equals(FUND_PENSION_OPENING))
            .findFirst()
            .orElseThrow();
    var partialWithdrawalMandateInBatch =
        mandatesFromBatch.stream()
            .filter(mandate -> mandate.getMandateType().equals(PARTIAL_WITHDRAWAL))
            .findFirst()
            .orElseThrow();

    assertThat(fundPensionMandateInBatch).isNotNull();
    assertThat(partialWithdrawalMandateInBatch).isNotNull();
  }
}
