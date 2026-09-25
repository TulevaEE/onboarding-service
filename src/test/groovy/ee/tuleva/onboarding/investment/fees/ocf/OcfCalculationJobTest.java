package ee.tuleva.onboarding.investment.fees.ocf;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcfCalculationJobTest {

  @Mock private OcfCalculationService service;
  @Spy private BusinessDays businessDays = new BusinessDays(new PublicHolidays());
  @Mock private Clock clock;

  @InjectMocks private OcfCalculationJob job;

  private static final ZoneId ZONE = ZoneId.of("Europe/Tallinn");

  @Test
  void computeMonthlyOnFourthBusinessDay() {
    var fourthBd = LocalDate.of(2026, 6, 4);
    setupClock(fourthBd);

    job.computeMonthlyIfReady();

    verify(service).calculateForAllFunds(YearMonth.of(2026, 5));
  }

  @Test
  void skipWhenNotFourthBusinessDay() {
    var thirdBd = LocalDate.of(2026, 6, 3);
    setupClock(thirdBd);

    job.computeMonthlyIfReady();

    verify(service, never()).calculateForAllFunds(any());
  }

  @Test
  void onOcfCalculationRequestedTriggersComputation() {
    var today = LocalDate.of(2026, 6, 15);
    setupClock(today);

    job.onOcfCalculationRequested();

    verify(service).calculateForAllFunds(YearMonth.of(2026, 5));
  }

  private void setupClock(LocalDate date) {
    var fixedClock = Clock.fixed(date.atStartOfDay(ZONE).toInstant(), ZONE);
    given(clock.instant()).willReturn(fixedClock.instant());
    given(clock.getZone()).willReturn(ZONE);
  }
}
