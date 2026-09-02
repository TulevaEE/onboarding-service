package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.CheckType.FREE_CASH;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.POSITION;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.PROVIDER;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.RESERVE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Not wrapped in the test's own transaction: the point of these tests is what the writer's
// transaction commits and what it rolls back, which a surrounding transaction would hide.
@DataJpaTest
@Import(LimitCheckEventWriter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LimitCheckEventWriterTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 3, 4);

  @Autowired LimitCheckEventWriter writer;
  @Autowired LimitCheckEventRepository repository;

  @BeforeEach
  void clearPreviousRows() {
    repository.deleteAll();
  }

  @Test
  void replacesEveryCheckTypeItIsGiven() {
    writer.replaceEvents(TUK75, CHECK_DATE, List.of(event(POSITION, true), event(PROVIDER, true)));

    writer.replaceEvents(
        TUK75, CHECK_DATE, List.of(event(POSITION, false), event(PROVIDER, false)));

    assertThat(repository.findByFundAndCheckDate(TUK75, CHECK_DATE))
        .hasSize(2)
        .allSatisfy(event -> assertThat(event.isBreachesFound()).isFalse());
  }

  @Test
  void leavesCheckTypesItWasNotGivenAlone() {
    writer.replaceEvents(TUK75, CHECK_DATE, List.of(event(RESERVE, true)));

    writer.replaceEvents(TUK75, CHECK_DATE, List.of(event(POSITION, false)));

    assertThat(repository.findByFundAndCheckDate(TUK75, CHECK_DATE))
        .extracting(LimitCheckEvent::getCheckType)
        .containsExactlyInAnyOrder(RESERVE, POSITION);
  }

  // The reason the writer exists. A run that dies partway must not leave the date carrying some
  // check types from this run and some from the last one, with nothing recording the disagreement.
  @Test
  void keepsThePreviousRowsWhenPartOfTheReplacementFails() {
    writer.replaceEvents(
        TUK75,
        CHECK_DATE,
        List.of(event(POSITION, true), event(PROVIDER, true), event(RESERVE, true)));

    assertThatThrownBy(
            () ->
                writer.replaceEvents(
                    TUK75,
                    CHECK_DATE,
                    List.of(event(POSITION, false), event(PROVIDER, false), unsavable())))
        .isInstanceOf(Exception.class);

    assertThat(repository.findByFundAndCheckDate(TUK75, CHECK_DATE))
        .hasSize(3)
        .allSatisfy(event -> assertThat(event.isBreachesFound()).isTrue());
  }

  private LimitCheckEvent event(CheckType checkType, boolean breachesFound) {
    return LimitCheckEvent.builder()
        .fund(TUK75)
        .checkDate(CHECK_DATE)
        .checkType(checkType)
        .breachesFound(breachesFound)
        .result(Map.of("breaches", List.of()))
        .build();
  }

  private LimitCheckEvent unsavable() {
    return LimitCheckEvent.builder()
        .fund(TUK75)
        .checkDate(CHECK_DATE)
        .checkType(FREE_CASH)
        .breachesFound(false)
        .result(null)
        .build();
  }
}
