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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThirdPillarTaxHeadroomTest {

  private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");

  @Mock private EpisService episService;
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  @Mock private Person person;

  private ThirdPillarTaxHeadroom headroom;

  @BeforeEach
  void setUp() {
    headroom = new ThirdPillarTaxHeadroom(episService, clock);
  }

  @Test
  void hasHeadroomWhenThirdPillarContributionsAreWellBelowTheCeiling() {
    given(episService.getContributions(person))
        .willReturn(concat(monthlySalaryOf2000(), thirdPillarMonthly(new BigDecimal("50.00"), 12)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void hasNoHeadroomWhenContributionsReachTheSalaryBasedCeiling() {
    given(episService.getContributions(person))
        .willReturn(
            concat(monthlySalaryOf2000(), thirdPillarMonthly(new BigDecimal("300.00"), 12)));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void hasNoHeadroomWhenSalaryCannotBeDerived() {
    given(episService.getContributions(person))
        .willReturn(thirdPillarMonthly(new BigDecimal("50.00"), 12));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void absoluteCeilingCapsHighSalaries() {
    given(episService.getContributions(person))
        .willReturn(
            concat(
                monthlySalary(new BigDecimal("400.00")),
                thirdPillarMonthly(new BigDecimal("450.00"), 12)));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void decemberTopUpWithinTheLastYearCountsAgainstTheCeiling() {
    List<Contribution> topUp =
        List.of(
            ThirdPillarContribution.builder()
                .time(Instant.parse("2025-12-15T10:00:00Z"))
                .amount(new BigDecimal("3600.00"))
                .currency(Currency.EUR)
                .pillar(3)
                .build());

    given(episService.getContributions(person)).willReturn(concat(monthlySalaryOf2000(), topUp));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void habitualDecemberTopUpperIsNotNudgedEvenWhenTheTopUpFellOutOfTheTrailingYear() {
    List<Contribution> oldTopUp =
        List.of(
            ThirdPillarContribution.builder()
                .time(monthsAgo(14))
                .amount(new BigDecimal("3600.00"))
                .currency(Currency.EUR)
                .pillar(3)
                .build());

    given(episService.getContributions(person))
        .willReturn(
            concat(
                concat(monthlySalaryOf2000(), thirdPillarMonthly(new BigDecimal("100.00"), 12)),
                oldTopUp));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void staleSalaryDataFromBeforeUnemploymentGivesNoConfidenceAndNoNudge() {
    List<Contribution> oldSalary =
        IntStream.rangeClosed(14, 25)
            .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("80.00")))
            .toList();

    given(episService.getContributions(person))
        .willReturn(concat(oldSalary, thirdPillarMonthly(new BigDecimal("50.00"), 12)));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void parentalLeaveMonthsWithReducedPortionsDoNotDragTheSalaryDown() {
    List<Contribution> salary =
        concat(
            IntStream.rangeClosed(1, 8)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("80.00")))
                .toList(),
            IntStream.rangeClosed(9, 12)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("8.00")))
                .toList());

    given(episService.getContributions(person))
        .willReturn(concat(salary, thirdPillarMonthly(new BigDecimal("50.00"), 8)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void zeroPortionMonthsAreIgnoredWhenDerivingTheSalary() {
    List<Contribution> salary =
        concat(
            IntStream.rangeClosed(1, 6)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("80.00")))
                .toList(),
            IntStream.rangeClosed(7, 12)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), BigDecimal.ZERO))
                .toList());

    given(episService.getContributions(person))
        .willReturn(concat(salary, thirdPillarMonthly(new BigDecimal("50.00"), 6)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void oneOffBonusMonthDoesNotInflateTheCeiling() {
    List<Contribution> salary =
        concat(
            IntStream.rangeClosed(1, 10)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("80.00")))
                .toList(),
            List.of(secondPillarPortion(monthsAgo(11), new BigDecimal("800.00"))));

    given(episService.getContributions(person))
        .willReturn(concat(salary, thirdPillarMonthly(new BigDecimal("300.00"), 11)));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void irregularFreelancerIncomeSettlesOnTheTypicalMonth() {
    List<Contribution> salary =
        concat(
            IntStream.rangeClosed(1, 6)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("40.00")))
                .toList(),
            IntStream.rangeClosed(7, 12)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("400.00")))
                .toList());

    given(episService.getContributions(person))
        .willReturn(concat(salary, thirdPillarMonthly(new BigDecimal("100.00"), 11)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void shortEmploymentHistoryOfAFewMonthsIsEnoughToDeriveTheSalary() {
    List<Contribution> salary =
        IntStream.rangeClosed(1, 3)
            .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("80.00")))
            .toList();

    given(episService.getContributions(person))
        .willReturn(concat(salary, thirdPillarMonthly(new BigDecimal("50.00"), 3)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void contributionLookupFailureMeansNoNudgeInsteadOfABrokenEmail() {
    given(episService.getContributions(person)).willThrow(new IllegalStateException("EPIS down"));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void noContributionsAtAllMeansNoNudge() {
    given(episService.getContributions(person)).willReturn(List.of());

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void salaryWithoutAnyThirdPillarContributionsHasHeadroom() {
    given(episService.getContributions(person)).willReturn(monthlySalaryOf2000());

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  // With the day-5 anchor used by monthsAgo(), monthsAgo(12) always lands just before
  // yearsAgo(1) and monthsAgo(24) always lands just before yearsAgo(2), so only 11 of every
  // 12 monthly contributions fall inside a given one-year window; these two tests use a
  // salary (portion 44.00/month -> monthlyGross 1100.00 -> confidentCeiling 1584.00) and an
  // 11-month third pillar contribution amount (144.00) chosen so 11 x 144.00 = 1584.00 lands
  // exactly on that ceiling.
  @Test
  void lastYearExactlyAtTheConfidentCeilingIsNotConfidentlyBelowIt() {
    given(episService.getContributions(person))
        .willReturn(
            concat(
                monthlySalary(new BigDecimal("44.00")),
                thirdPillarMonthly(new BigDecimal("144.00"), 11)));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void yearBeforeExactlyAtTheConfidentCeilingIsNotConfidentlyBelowIt() {
    List<Contribution> yearBeforeContributions =
        IntStream.rangeClosed(13, 23)
            .mapToObj(
                m ->
                    (Contribution)
                        ThirdPillarContribution.builder()
                            .time(monthsAgo(m))
                            .amount(new BigDecimal("144.00"))
                            .currency(Currency.EUR)
                            .pillar(3)
                            .build())
            .toList();

    given(episService.getContributions(person))
        .willReturn(concat(monthlySalary(new BigDecimal("44.00")), yearBeforeContributions));

    assertThat(headroom.hasHeadroom(person)).isFalse();
  }

  @Test
  void thirdPillarContributionsOutsideTheTwoYearWindowAreExcludedFromTheSums() {
    List<Contribution> veryOldTopUp =
        List.of(
            ThirdPillarContribution.builder()
                .time(monthsAgo(36))
                .amount(new BigDecimal("5000.00"))
                .currency(Currency.EUR)
                .pillar(3)
                .build());

    given(episService.getContributions(person))
        .willReturn(concat(monthlySalaryOf2000(), veryOldTopUp));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void secondPillarContributionsWithoutASocialTaxPortionAreExcludedFromTheMedian() {
    Contribution missingPortion =
        SecondPillarContribution.builder()
            .time(monthsAgo(1))
            .amount(BigDecimal.ONE)
            .currency(Currency.EUR)
            .pillar(2)
            .socialTaxPortion(null)
            .build();

    given(episService.getContributions(person))
        .willReturn(
            concat(
                concat(monthlySalaryOf2000(), List.of(missingPortion)),
                thirdPillarMonthly(new BigDecimal("50.00"), 12)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void manyZeroPortionMonthsWouldZeroOutTheMedianIfNotFiltered() {
    List<Contribution> salary =
        concat(
            IntStream.rangeClosed(1, 3)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), new BigDecimal("80.00")))
                .toList(),
            IntStream.rangeClosed(4, 12)
                .mapToObj(m -> secondPillarPortion(monthsAgo(m), BigDecimal.ZERO))
                .toList());

    given(episService.getContributions(person))
        .willReturn(concat(salary, thirdPillarMonthly(new BigDecimal("10.00"), 12)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void oddNumberOfDistinctMonthlyPortionsUsesTheMiddleValueNotAnAverage() {
    List<Contribution> salary =
        List.of(
            secondPillarPortion(monthsAgo(1), new BigDecimal("10.00")),
            secondPillarPortion(monthsAgo(2), new BigDecimal("20.00")),
            secondPillarPortion(monthsAgo(3), new BigDecimal("30.00")));

    given(episService.getContributions(person))
        .willReturn(concat(salary, thirdPillarMonthly(new BigDecimal("50.00"), 12)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  @Test
  void evenNumberOfDistinctMonthlyPortionsAveragesTheTwoMiddleValues() {
    List<Contribution> salary =
        List.of(
            secondPillarPortion(monthsAgo(1), new BigDecimal("40.00")),
            secondPillarPortion(monthsAgo(2), new BigDecimal("120.00")));

    given(episService.getContributions(person))
        .willReturn(concat(salary, thirdPillarMonthly(new BigDecimal("50.00"), 12)));

    assertThat(headroom.hasHeadroom(person)).isTrue();
  }

  private static Contribution secondPillarPortion(Instant time, BigDecimal socialTaxPortion) {
    return SecondPillarContribution.builder()
        .time(time)
        .amount(BigDecimal.ONE)
        .currency(Currency.EUR)
        .pillar(2)
        .socialTaxPortion(socialTaxPortion)
        .build();
  }

  private static List<Contribution> monthlySalaryOf2000() {
    return monthlySalary(new BigDecimal("80.00"));
  }

  private static Instant monthsAgo(int monthsAgo) {
    return NOW.atZone(ZoneOffset.UTC)
        .toLocalDate()
        .withDayOfMonth(5)
        .minusMonths(monthsAgo)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant();
  }

  private static List<Contribution> monthlySalary(BigDecimal socialTaxPortion) {
    return IntStream.rangeClosed(1, 12)
        .mapToObj(
            months ->
                (Contribution)
                    SecondPillarContribution.builder()
                        .time(monthsAgo(months))
                        .amount(BigDecimal.ONE)
                        .currency(Currency.EUR)
                        .pillar(2)
                        .socialTaxPortion(socialTaxPortion)
                        .build())
        .toList();
  }

  private static List<Contribution> thirdPillarMonthly(BigDecimal amount, int months) {
    return IntStream.rangeClosed(1, months)
        .mapToObj(
            m ->
                (Contribution)
                    ThirdPillarContribution.builder()
                        .time(monthsAgo(m))
                        .amount(amount)
                        .currency(Currency.EUR)
                        .pillar(3)
                        .build())
        .toList();
  }

  private static List<Contribution> concat(List<Contribution> a, List<Contribution> b) {
    return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
  }
}
