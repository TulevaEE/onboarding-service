package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationReadConfiguration;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

@DataJpaTest
@TestPropertySource(properties = "investment.kid.digest.enabled=true")
@Import({
  JdbcFundValueRepository.class,
  IndexOnlyKidSeries.class,
  KidConfiguration.class,
  KidFiguresService.class,
  PerformanceScenarioCalculator.class,
  StressScenarioCalculator.class,
  ScenarioPresentation.class,
  CostsOverTimeCalculator.class,
  KidCalculationRepository.class,
  KidPrintedFiguresRepository.class,
  KidCalculationService.class,
  SlackKidCalculationNotifier.class,
  OcfPublicationReadConfiguration.class,
  KidCalculationNotifierIT.DayAfterTheSeptemberPeriodEnds.class
})
class KidCalculationNotifierIT {

  private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

  @Autowired private KidCalculationService service;
  @Autowired private KidCalculationNotifier notifier;
  @Autowired private JdbcFundValueRepository fundValues;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private OperationsNotificationService notificationService;

  @BeforeEach
  void pricesAndAnOcfInForceFromSeptember() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");
  }

  @Test
  void aRunThatStoredMonthsSendsOneDigestAndARunThatStoredNothingSendsNone() {
    var job = new KidCalculationJob(service, notifier);

    job.calculateTheMissingMonths();
    job.calculateTheMissingMonths();

    verify(notificationService, times(1))
        .sendMessage(
            argThat(
                text ->
                    text.contains("CALCULATION_IDS=" + calculationIdOfSeptember())
                        && text.contains(
                            "Next: 2026-01, 2026-02, 2026-03, 2026-04, 2026-05, 2026-06,"
                                + " 2026-07, 2026-08: kanna KID-i jooksvate tasude näitaja")),
            eq(INVESTMENT),
            eq(WARNING));
    verify(notificationService, times(1)).sendMessage(anyString(), any(), any());
  }

  @Test
  void aRunWithTheDigestOffStoresItsMonthsAndSendsNothing() {
    var digestOff = new LoggingKidCalculationNotifier();

    new KidCalculationJob(service, digestOff).calculateTheMissingMonths();

    assertThat(storedCalculations()).isEqualTo(9);
    verifyNoInteractions(notificationService);
  }

  @Test
  void aSlackFailureLeavesTheStoredMonthsAndDoesNotFailTheRun() {
    willThrow(new RestClientException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(anyString(), any(), any());

    assertThatCode(() -> new KidCalculationJob(service, notifier).calculateTheMissingMonths())
        .doesNotThrowAnyException();

    assertThat(storedCalculations()).isEqualTo(9);
  }

  private long storedCalculations() {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM investment_kid_calculation")
        .query(Long.class)
        .single();
  }

  private long calculationIdOfSeptember() {
    return jdbcClient
        .sql(
            "SELECT id FROM investment_kid_calculation"
                + " WHERE calculation_month = DATE '2026-09-01'")
        .query(Long.class)
        .single();
  }

  @TestConfiguration
  static class DayAfterTheSeptemberPeriodEnds {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneId.of("Europe/Tallinn"));
    }
  }
}
