package ee.tuleva.onboarding.account.portfolio;

import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.SAVINGS_FUND;
import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.SECOND_PILLAR;
import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.THIRD_PILLAR;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.PERSONAL;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.account.SavingsFundNav;
import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.account.transaction.TransactionService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.ReturnsService;
import ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.savings.SavingsFundConfiguration;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

  private static final String TKF = "EE0000003283";
  private static final String PILLAR_2 = "EE3600109435";
  private static final String PILLAR_3 = "EE3600001707";
  private static final LocalDate FROM = LocalDate.parse("2025-01-01");
  private static final LocalDate TO = LocalDate.parse("2025-12-31");
  private static final LocalDate FAR_FUTURE = LocalDate.parse("9999-12-31");
  private static final LocalDate FIRST_HOLDING = LocalDate.parse("2019-03-05");
  private static final LocalDate TODAY = LocalDate.parse("2026-01-02");

  @Mock private TransactionService transactionService;
  @Mock private FundRepository fundRepository;
  @Mock private FundValueQueries fundValueQueries;
  @Mock private ReturnsService returnsService;
  @Mock private SavingsFundNav fundNavProvider;

  private final Clock clock =
      Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
  private final SavingsFundConfiguration savingsFundConfiguration = new SavingsFundConfiguration();
  private final AuthenticatedPerson person = sampleAuthenticatedPersonNonMember().build();

  private PortfolioService portfolioService;

  @BeforeEach
  void setUp() {
    portfolioService =
        new PortfolioService(
            transactionService,
            fundRepository,
            fundValueQueries,
            savingsFundConfiguration,
            returnsService,
            fundNavProvider,
            clock);
  }

  private static Transaction buy(String isin, String time, String units, String nav) {
    BigDecimal unitCount = new BigDecimal(units);
    BigDecimal navPerUnit = new BigDecimal(nav);
    return Transaction.builder()
        .id(UUID.nameUUIDFromBytes((isin + time).getBytes()))
        .amount(unitCount.multiply(navPerUnit))
        .currency(EUR)
        .time(Instant.parse(time))
        .isin(isin)
        .type(CONTRIBUTION_CASH)
        .units(unitCount)
        .nav(navPerUnit)
        .build();
  }

  private static FundValue fundValue(String isin, String date, String value) {
    return new FundValue(isin, LocalDate.parse(date), new BigDecimal(value), "TEST", Instant.EPOCH);
  }

  private static Returns personalReturn(String key, String rate) {
    return Returns.builder()
        .returns(
            List.of(
                Returns.Return.builder()
                    .key(key)
                    .type(PERSONAL)
                    .rate(new BigDecimal(rate))
                    .from(FROM)
                    .to(TO)
                    .build()))
        .build();
  }

  @Test
  void startsAnAllTimePeriodAtTheFirstDayAnythingWasHeld() {
    given(transactionService.getTransactions(person))
        .willReturn(List.of(buy(PILLAR_2, "2019-03-05T10:00:00Z", "100", "2")));
    given(fundRepository.findAll())
        .willReturn(List.of(Fund.builder().isin(PILLAR_2).pillar(2).build()));
    given(fundValueQueries.getLatestValue(any(), any())).willReturn(Optional.empty());
    given(fundValueQueries.findValuesBetweenDates(eq(PILLAR_2), eq(FIRST_HOLDING), eq(TO)))
        .willReturn(
            List.of(
                fundValue(PILLAR_2, "2019-03-05", "2"), fundValue(PILLAR_2, "2025-12-31", "3")));
    given(returnsService.get(eq(person), eq(FIRST_HOLDING), eq(TO), any()))
        .willReturn(personalReturn(PersonalReturnProvider.SECOND_PILLAR, "0.0712"));

    Portfolio portfolio = portfolioService.getPortfolio(person, null, TO);

    assertThat(portfolio.from()).isEqualTo(FIRST_HOLDING);
    assertThat(portfolio.series().getFirst().date()).isEqualTo(FIRST_HOLDING);
  }

  @Test
  void groupsHoldingsBySourceAndValuesThemOverThePeriod() {
    given(transactionService.getTransactions(person))
        .willReturn(
            List.of(
                buy(TKF, "2025-01-01T10:00:00Z", "100", "10"),
                buy(PILLAR_2, "2025-01-01T10:00:00Z", "200", "2")));
    given(fundRepository.findAll())
        .willReturn(
            List.of(
                Fund.builder().isin(TKF).pillar(null).build(),
                Fund.builder().isin(PILLAR_2).pillar(2).build()));
    given(fundNavProvider.safeMaxNavDate()).willReturn(FAR_FUTURE);
    given(fundValueQueries.getLatestValue(any(), any())).willReturn(Optional.empty());
    given(fundValueQueries.findValuesBetweenDates(eq(TKF), eq(FROM), eq(TO)))
        .willReturn(
            List.of(fundValue(TKF, "2025-01-01", "10"), fundValue(TKF, "2025-12-31", "12")));
    given(fundValueQueries.findValuesBetweenDates(eq(PILLAR_2), eq(FROM), eq(TO)))
        .willReturn(
            List.of(
                fundValue(PILLAR_2, "2025-01-01", "2"), fundValue(PILLAR_2, "2025-12-31", "3")));
    given(returnsService.get(eq(person), eq(FROM), eq(TO), any()))
        .willReturn(personalReturn(PersonalReturnProvider.SECOND_PILLAR, "0.0712"));

    Portfolio portfolio = portfolioService.getPortfolio(person, FROM, TO);

    assertThat(portfolio.groups().stream().map(Portfolio.GroupSummary::group))
        .containsExactly(SAVINGS_FUND, SECOND_PILLAR);

    Portfolio.GroupSummary savingsFund = portfolio.groups().getFirst();
    assertThat(savingsFund.endValue()).isEqualByComparingTo("1200.00");
    assertThat(savingsFund.contributions()).isEqualByComparingTo("1000.00");
    assertThat(savingsFund.annualReturnRate()).isNull();

    Portfolio.GroupSummary secondPillar = portfolio.groups().getLast();
    assertThat(secondPillar.endValue()).isEqualByComparingTo("600.00");
    assertThat(secondPillar.annualReturnRate()).isEqualByComparingTo("0.0712");

    assertThat(portfolio.series().stream().map(Portfolio.ValuePoint::date))
        .containsExactly(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));
    assertThat(portfolio.series().getLast().values().get(SAVINGS_FUND))
        .isEqualByComparingTo("1200.00");
    assertThat(portfolio.series().getLast().values().get(SECOND_PILLAR))
        .isEqualByComparingTo("600.00");
  }

  @Test
  void opensThePeriodWithThePriceKnownBeforeItStarted() {
    given(transactionService.getTransactions(person))
        .willReturn(List.of(buy(TKF, "2024-06-01T10:00:00Z", "100", "10")));
    given(fundRepository.findAll()).willReturn(List.of(Fund.builder().isin(TKF).build()));
    given(fundNavProvider.safeMaxNavDate()).willReturn(FAR_FUTURE);
    given(fundValueQueries.getLatestValue(TKF, FROM.minusDays(1)))
        .willReturn(Optional.of(fundValue(TKF, "2024-12-30", "11")));
    given(fundValueQueries.findValuesBetweenDates(eq(TKF), eq(FROM), eq(TO)))
        .willReturn(List.of(fundValue(TKF, "2025-12-31", "12")));

    Portfolio portfolio = portfolioService.getPortfolio(person, FROM, TO);

    Portfolio.GroupSummary savingsFund = portfolio.groups().getFirst();
    assertThat(savingsFund.startValue()).isEqualByComparingTo("1100.00");
    assertThat(savingsFund.endValue()).isEqualByComparingTo("1200.00");
    assertThat(savingsFund.gain()).isEqualByComparingTo("100.00");
  }

  @Test
  void asksForEachHeldPillarsReturnSeparately() {
    given(transactionService.getTransactions(person))
        .willReturn(
            List.of(
                buy(PILLAR_2, "2025-01-01T10:00:00Z", "200", "2"),
                buy(PILLAR_3, "2025-01-01T10:00:00Z", "100", "5")));
    given(fundRepository.findAll())
        .willReturn(
            List.of(
                Fund.builder().isin(PILLAR_2).pillar(2).build(),
                Fund.builder().isin(PILLAR_3).pillar(3).build()));
    given(fundValueQueries.getLatestValue(any(), any())).willReturn(Optional.empty());
    given(fundValueQueries.findValuesBetweenDates(eq(PILLAR_2), eq(FROM), eq(TO)))
        .willReturn(List.of(fundValue(PILLAR_2, "2025-12-31", "3")));
    given(fundValueQueries.findValuesBetweenDates(eq(PILLAR_3), eq(FROM), eq(TO)))
        .willReturn(List.of(fundValue(PILLAR_3, "2025-12-31", "6")));
    given(returnsService.get(person, FROM, TO, List.of(PersonalReturnProvider.SECOND_PILLAR)))
        .willReturn(personalReturn(PersonalReturnProvider.SECOND_PILLAR, "0.0712"));
    given(returnsService.get(person, FROM, TO, List.of(PersonalReturnProvider.THIRD_PILLAR)))
        .willReturn(personalReturn(PersonalReturnProvider.THIRD_PILLAR, "0.0435"));

    Portfolio portfolio = portfolioService.getPortfolio(person, FROM, TO);

    assertThat(portfolio.groups())
        .extracting(Portfolio.GroupSummary::group, Portfolio.GroupSummary::annualReturnRate)
        .containsExactly(
            tuple(SECOND_PILLAR, new BigDecimal("0.0712")),
            tuple(THIRD_PILLAR, new BigDecimal("0.0435")));
  }

  @Test
  void asksForNoReturnRatesWhenOnlyTheSavingsFundIsHeld() {
    given(transactionService.getTransactions(person))
        .willReturn(List.of(buy(TKF, "2025-01-01T10:00:00Z", "100", "10")));
    given(fundRepository.findAll()).willReturn(List.of(Fund.builder().isin(TKF).build()));
    given(fundNavProvider.safeMaxNavDate()).willReturn(FAR_FUTURE);
    given(fundValueQueries.getLatestValue(any(), any())).willReturn(Optional.empty());
    given(fundValueQueries.findValuesBetweenDates(eq(TKF), eq(FROM), eq(TO)))
        .willReturn(List.of(fundValue(TKF, "2025-12-31", "12")));

    Portfolio portfolio = portfolioService.getPortfolio(person, FROM, TO);

    assertThat(portfolio.groups()).hasSize(1);
    assertThat(portfolio.groups().getFirst().annualReturnRate()).isNull();
  }

  @Test
  void hidesSavingsFundNavsNewerThanTheSafeMaxNavDate() {
    LocalDate safeMaxNavDate = LocalDate.parse("2025-11-28");
    given(transactionService.getTransactions(person))
        .willReturn(List.of(buy(TKF, "2025-01-01T10:00:00Z", "100", "10")));
    given(fundRepository.findAll()).willReturn(List.of(Fund.builder().isin(TKF).build()));
    given(fundNavProvider.safeMaxNavDate()).willReturn(safeMaxNavDate);
    given(fundValueQueries.getLatestValue(TKF, FROM.minusDays(1))).willReturn(Optional.empty());
    given(fundValueQueries.findValuesBetweenDates(eq(TKF), eq(FROM), eq(safeMaxNavDate)))
        .willReturn(
            List.of(fundValue(TKF, "2025-01-01", "10"), fundValue(TKF, "2025-11-28", "11")));

    Portfolio portfolio = portfolioService.getPortfolio(person, FROM, TO);

    assertThat(portfolio.groups().getFirst().endValue()).isEqualByComparingTo("1100.00");
    assertThat(portfolio.series().stream().map(Portfolio.ValuePoint::date))
        .containsExactly(LocalDate.parse("2025-01-01"), safeMaxNavDate);
  }

  @Test
  void leavesPensionFundNavsUncapped() {
    given(transactionService.getTransactions(person))
        .willReturn(List.of(buy(PILLAR_2, "2025-01-01T10:00:00Z", "200", "2")));
    given(fundRepository.findAll())
        .willReturn(List.of(Fund.builder().isin(PILLAR_2).pillar(2).build()));
    given(fundValueQueries.getLatestValue(PILLAR_2, FROM.minusDays(1)))
        .willReturn(Optional.empty());
    given(fundValueQueries.findValuesBetweenDates(eq(PILLAR_2), eq(FROM), eq(TO)))
        .willReturn(List.of(fundValue(PILLAR_2, "2025-12-31", "3")));
    given(returnsService.get(eq(person), eq(FROM), eq(TO), any()))
        .willReturn(personalReturn(PersonalReturnProvider.SECOND_PILLAR, "0.0712"));

    Portfolio portfolio = portfolioService.getPortfolio(person, FROM, TO);

    assertThat(portfolio.groups().getFirst().endValue()).isEqualByComparingTo("600.00");
  }

  @Test
  void withholdsTheReturnRateOfAPeriodThatIsOverBeforeItStarts() {
    given(transactionService.getTransactions(person))
        .willReturn(List.of(buy(PILLAR_2, "2025-06-02T10:00:00Z", "200", "2")));
    given(fundRepository.findAll())
        .willReturn(List.of(Fund.builder().isin(PILLAR_2).pillar(2).build()));
    given(fundValueQueries.getLatestValue(PILLAR_2, TODAY.minusDays(1)))
        .willReturn(Optional.of(fundValue(PILLAR_2, "2026-01-01", "3")));
    given(fundValueQueries.findValuesBetweenDates(eq(PILLAR_2), eq(TODAY), eq(TODAY)))
        .willReturn(List.of(fundValue(PILLAR_2, "2026-01-02", "3.05")));

    Portfolio portfolio = portfolioService.getPortfolio(person, TODAY, TODAY);

    Portfolio.GroupSummary secondPillar = portfolio.groups().getFirst();
    assertThat(secondPillar.group()).isEqualTo(SECOND_PILLAR);
    assertThat(secondPillar.startValue()).isEqualByComparingTo("600.00");
    assertThat(secondPillar.endValue()).isEqualByComparingTo("610.00");
    assertThat(secondPillar.annualReturnRate()).isNull();
    assertThat(portfolio.series().getFirst().values().get(SECOND_PILLAR))
        .isEqualByComparingTo("610.00");
    verify(returnsService, never()).get(any(), any(), any(), any());
  }

  @Test
  void withholdsTheReturnRateOfAPeriodThatStartsTodayAndEndsInTheFuture() {
    LocalDate nextMonth = TODAY.plusMonths(1);
    given(transactionService.getTransactions(person))
        .willReturn(List.of(buy(PILLAR_2, "2025-06-02T10:00:00Z", "200", "2")));
    given(fundRepository.findAll())
        .willReturn(List.of(Fund.builder().isin(PILLAR_2).pillar(2).build()));
    given(fundValueQueries.getLatestValue(PILLAR_2, TODAY.minusDays(1)))
        .willReturn(Optional.of(fundValue(PILLAR_2, "2026-01-01", "3")));
    given(fundValueQueries.findValuesBetweenDates(eq(PILLAR_2), eq(TODAY), eq(nextMonth)))
        .willReturn(List.of(fundValue(PILLAR_2, "2026-01-02", "3.05")));

    Portfolio portfolio = portfolioService.getPortfolio(person, TODAY, nextMonth);

    assertThat(portfolio.groups().getFirst().endValue()).isEqualByComparingTo("610.00");
    assertThat(portfolio.groups().getFirst().annualReturnRate()).isNull();
    verify(returnsService, never()).get(any(), any(), any(), any());
  }
}
