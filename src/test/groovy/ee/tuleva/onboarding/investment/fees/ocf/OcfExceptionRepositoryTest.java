package ee.tuleva.onboarding.investment.fees.ocf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

@DataJpaTest
@Import({OcfExceptionRepository.class, OcfSnapshotRepository.class})
class OcfExceptionRepositoryTest {

  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final ExceptionDecision DECISION =
      new ExceptionDecision(
          "fund-manager",
          "the fee rate is set by the supervisory board decision",
          Instant.parse("2026-05-04T09:30:00Z"));

  @Autowired private OcfExceptionRepository repository;
  @Autowired private OcfSnapshotRepository snapshotRepository;

  @Test
  void nothingIsExceptedUntilTheFundManagerDecides() {
    var calculation = storedCalculation("{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}");

    assertThat(repository.findForTheCurrentCalculation(calculation)).isEmpty();
  }

  @Test
  void theFundManagersDecisionIsFoundForTheCalculationItWasDecidedFor() {
    var calculation = storedCalculation("{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}");

    repository.record(calculation, DECISION);

    assertThat(repository.findForTheCurrentCalculation(calculation)).contains(DECISION);
  }

  @Test
  void aRecalculationWithOtherGapsIsNoLongerExcepted() {
    var decidedFor = storedCalculation("{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}");
    repository.record(decidedFor, DECISION);

    var recalculated = storedCalculation("{\"gaps\":[\"NO_PUBLISHED_NAV_CALCULATION\"]}");

    assertThat(recalculated.storedId()).isEqualTo(decidedFor.storedId());
    assertThat(repository.findForTheCurrentCalculation(recalculated)).isEmpty();
  }

  @Test
  void aCalculationIsExceptedOnce() {
    var calculation = storedCalculation("{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}");
    repository.record(calculation, DECISION);

    assertThatThrownBy(() -> repository.record(calculation, DECISION))
        .isInstanceOf(DuplicateKeyException.class);
  }

  private OcfSnapshot storedCalculation(String checks) {
    snapshotRepository.save(
        OcfSnapshot.computed(
            "TUK75",
            APRIL,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            RebateBasis.NET,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            checks,
            new OcfAudit(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null)),
        List.of());
    return snapshotRepository.findByFundAndMonth("TUK75", APRIL).orElseThrow();
  }
}
