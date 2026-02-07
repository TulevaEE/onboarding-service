package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.time.TestClockHolder.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundNavProviderTest {

  private static final String ISIN = "EE0000003283";
  private static final LocalDate TODAY = LocalDate.of(2020, 1, 1);
  private static final LocalDate LAST_WORKING_DAY = LocalDate.of(2019, 12, 31);

  @Mock private FundValueRepository fundValueRepository;
  @Mock private SavingsFundConfiguration configuration;
  @Mock private PublicHolidays publicHolidays;
  @InjectMocks private SavingsFundNavProvider navProvider;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void getCurrentNav_returnsLatestNav() {
    BigDecimal expectedNav = new BigDecimal("1.2345");
    when(configuration.getIsin()).thenReturn(ISIN);
    when(fundValueRepository.findLastValueForFund(ISIN))
        .thenReturn(
            Optional.of(
                new FundValue(ISIN, LAST_WORKING_DAY, expectedNav, "MANUAL", Instant.now())));

    BigDecimal result = navProvider.getCurrentNav();

    assertThat(result).isEqualTo(expectedNav);
  }

  @Test
  void getCurrentNav_throwsWhenNavNotFound() {
    when(configuration.getIsin()).thenReturn(ISIN);
    when(fundValueRepository.findLastValueForFund(ISIN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> navProvider.getCurrentNav()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getCurrentNavForIssuing_returnsNavWhenDateMatches() {
    BigDecimal expectedNav = new BigDecimal("1.2345");
    when(configuration.getIsin()).thenReturn(ISIN);
    when(publicHolidays.previousWorkingDay(TODAY)).thenReturn(LAST_WORKING_DAY);
    when(fundValueRepository.findLastValueForFund(ISIN))
        .thenReturn(
            Optional.of(
                new FundValue(ISIN, LAST_WORKING_DAY, expectedNav, "MANUAL", Instant.now())));

    BigDecimal result = navProvider.getCurrentNavForIssuing();

    assertThat(result).isEqualTo(expectedNav);
  }

  @Test
  void getCurrentNavForIssuing_throwsWhenNavScaleExceedsFourDecimalPlaces() {
    BigDecimal navWithTooManyDecimals = new BigDecimal("1.12345");
    when(configuration.getIsin()).thenReturn(ISIN);
    when(publicHolidays.previousWorkingDay(TODAY)).thenReturn(LAST_WORKING_DAY);
    when(fundValueRepository.findLastValueForFund(ISIN))
        .thenReturn(
            Optional.of(
                new FundValue(
                    ISIN, LAST_WORKING_DAY, navWithTooManyDecimals, "MANUAL", Instant.now())));

    assertThatThrownBy(() -> navProvider.getCurrentNavForIssuing())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getCurrentNavForIssuing_throwsWhenNavIsStale() {
    LocalDate staleDate = LocalDate.of(2019, 12, 30);
    when(configuration.getIsin()).thenReturn(ISIN);
    when(publicHolidays.previousWorkingDay(TODAY)).thenReturn(LAST_WORKING_DAY);
    when(fundValueRepository.findLastValueForFund(ISIN))
        .thenReturn(
            Optional.of(new FundValue(ISIN, staleDate, BigDecimal.ONE, "MANUAL", Instant.now())));

    assertThatThrownBy(() -> navProvider.getCurrentNavForIssuing())
        .isInstanceOf(IllegalStateException.class);
  }
}
