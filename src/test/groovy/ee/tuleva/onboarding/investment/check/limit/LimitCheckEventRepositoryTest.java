package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.POSITION;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.PROVIDER;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class LimitCheckEventRepositoryTest {

  @Autowired LimitCheckEventRepository repository;

  @Test
  void savesAndQueriesByFundAndDate() {
    var checkDate = LocalDate.of(2026, 3, 4);
    var event =
        LimitCheckEvent.builder()
            .fund(TUK75)
            .checkDate(checkDate)
            .checkType(POSITION)
            .breachesFound(true)
            .result(Map.of("breaches", 2))
            .build();

    repository.save(event);

    var found = repository.findByFundAndCheckDate(TUK75, checkDate);
    assertThat(found).singleElement().satisfies(e -> assertThat(e.isBreachesFound()).isTrue());
  }

  @Test
  void deletingOneCheckTypeLeavesTheOtherCheckTypesAlone() {
    var checkDate = LocalDate.of(2026, 3, 4);
    repository.save(event(POSITION));
    var retained = repository.save(event(PROVIDER));

    repository.deleteByFundAndCheckDateAndCheckType(TUK75, checkDate, POSITION);

    assertThat(repository.findByFundAndCheckDate(TUK75, checkDate))
        .singleElement()
        .satisfies(e -> assertThat(e.getId()).isEqualTo(retained.getId()));
  }

  private LimitCheckEvent event(CheckType checkType) {
    return LimitCheckEvent.builder()
        .fund(TUK75)
        .checkDate(LocalDate.of(2026, 3, 4))
        .checkType(checkType)
        .breachesFound(false)
        .result(Map.of())
        .build();
  }
}
