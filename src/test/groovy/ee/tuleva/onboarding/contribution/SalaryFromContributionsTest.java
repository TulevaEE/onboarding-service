package ee.tuleva.onboarding.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.Contribution;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.SecondPillarContribution;
import ee.tuleva.onboarding.epis.ThirdPillarContribution;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalaryFromContributionsTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final Clock CLOCK =
      Clock.fixed(LocalDateTime.parse("2026-09-15T09:00:00").atZone(TALLINN).toInstant(), TALLINN);

  @Mock private EpisService episService;
  @Mock private Person person;

  private SalaryFromContributions salary;

  @BeforeEach
  void setUp() {
    salary = new SalaryFromContributions(episService, CLOCK);
  }

  @Test
  void theLatestMonthOfSecondPillarReceiptsBecomesTheGrossSalaryAtTheCurrentRate() {
    given(episService.getContributions(person))
        .willReturn(
            List.of(
                secondPillar("2026-08-10T12:00:00", "198.00", "0.00"),
                secondPillar("2026-07-10T12:00:00", "120.00", "0.00")));

    assertThat(salary.latestGross(person, 2)).contains(new BigDecimal("3300.00"));
  }

  @Test
  void twoEmployersInTheSameMonthAreOneSalary() {
    given(episService.getContributions(person))
        .willReturn(
            List.of(
                secondPillar("2026-09-10T12:00:00", "120.00", "0.00"),
                secondPillar("2026-09-12T12:00:00", "78.00", "0.00")));

    assertThat(salary.latestGross(person, 2)).contains(new BigDecimal("3300.00"));
  }

  @Test
  void theStatesParentalBenefitShareIsPartOfTheReceiptAndIsTakenOutBeforeTheSalaryIsDerived() {
    given(episService.getContributions(person))
        .willReturn(List.of(secondPillar("2026-09-10T12:00:00", "238.00", "40.00")));

    assertThat(salary.latestGross(person, 2)).contains(new BigDecimal("3300.00"));
  }

  @Test
  void aHigherPaymentRateMeansTheSameReceiptComesFromASmallerSalary() {
    given(episService.getContributions(person))
        .willReturn(List.of(secondPillar("2026-09-10T12:00:00", "198.00", "0.00")));

    assertThat(salary.latestGross(person, 6)).contains(new BigDecimal("1980.00"));
  }

  @Test
  void aReceiptOlderThanTwoMonthsLeavesTheSalaryUnknown() {
    given(episService.getContributions(person))
        .willReturn(List.of(secondPillar("2026-06-10T12:00:00", "198.00", "0.00")));

    assertThat(salary.latestGross(person, 2)).isEmpty();
  }

  @Test
  void aReceiptExactlyTwoMonthsBackStillCounts() {
    given(episService.getContributions(person))
        .willReturn(List.of(secondPillar("2026-07-10T12:00:00", "198.00", "0.00")));

    assertThat(salary.latestGross(person, 2)).contains(new BigDecimal("3300.00"));
  }

  @Test
  void receiptsWithoutMoneyAndThirdPillarReceiptsNeverDeriveASalary() {
    given(episService.getContributions(person))
        .willReturn(
            List.of(
                secondPillar("2026-09-10T12:00:00", "0.00", "0.00"),
                thirdPillar("2026-09-11T12:00:00", "500.00")));

    assertThat(salary.latestGross(person, 2)).isEmpty();
  }

  @Test
  void aPersonWithNoContributionsAtAllHasNoKnownSalary() {
    given(episService.getContributions(person)).willReturn(List.of());

    assertThat(salary.latestGross(person, 2)).isEmpty();
  }

  private static Contribution secondPillar(
      String time, String amount, String additionalParentalBenefit) {
    return SecondPillarContribution.builder()
        .time(LocalDateTime.parse(time).atZone(TALLINN).toInstant())
        .sender("Employer")
        .amount(new BigDecimal(amount))
        .currency(Currency.EUR)
        .pillar(2)
        .additionalParentalBenefit(new BigDecimal(additionalParentalBenefit))
        .build();
  }

  private static Contribution thirdPillar(String time, String amount) {
    return new ThirdPillarContribution(
        LocalDateTime.parse(time).atZone(TALLINN).toInstant(),
        "Employer",
        new BigDecimal(amount),
        Currency.EUR,
        3);
  }
}
