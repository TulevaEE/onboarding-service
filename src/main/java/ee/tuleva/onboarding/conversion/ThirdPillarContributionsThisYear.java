package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.auth.principal.Person;
import java.math.BigDecimal;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ThirdPillarContributionsThisYear {

  private static final int THIRD_PILLAR = 3;

  private final ConversionCashFlows conversionCashFlows;
  private final Clock estonianClock;

  public BigDecimal forPerson(Person person) {
    return new CashFlowTotals(conversionCashFlows.forPerson(person), estonianClock)
        .contribution(THIRD_PILLAR)
        .getYearToDate();
  }
}
