package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DATA_VALID;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.TUK00_ACTIVE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PevaRavaPhaseUpdateJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 4, 20);
  private static final LocalDate LOCK_DATE = LocalDate.of(2026, 3, 31);
  private static final LocalDate EXEC_DATE = LocalDate.of(2026, 5, 1);

  private final PevaRavaPeriodService periodService = mock(PevaRavaPeriodService.class);
  private final PevaRavaCycleRepository cycleRepository = mock(PevaRavaCycleRepository.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  private final PevaRavaPhaseUpdateJob job =
      new PevaRavaPhaseUpdateJob(clock, periodService, cycleRepository, notificationService);

  @Test
  void doesNothingWithoutCurrentPeriod() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.empty());

    job.run();

    verifyNoInteractions(cycleRepository, notificationService);
  }

  @Test
  void createsCycleRowAndNotifiesWhenEnteringNewPeriod() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.of(period(DATA_VALID)));
    given(cycleRepository.findByExecDate(EXEC_DATE)).willReturn(Optional.empty());

    job.run();

    verify(cycleRepository)
        .save(
            PevaRavaCycleEntity.builder()
                .lockDate(LOCK_DATE)
                .execDate(EXEC_DATE)
                .phase(DATA_VALID)
                .build());
    verify(notificationService)
        .sendMessage(
            "📅 PEVA/RAVA faasi muutus: uus tsükkel → DATA_VALID\n"
                + "Tsükkel: lukustus 2026-03-31, täitmine 2026-05-01\n"
                + "TUK75: D-aktiivne 2026-04-22, müügi tähtaeg 2026-04-27\n"
                + "TUK00: D-aktiivne 2026-04-14, müügi tähtaeg 2026-04-23",
            INVESTMENT);
  }

  @Test
  void notifiesOnPhaseTransition() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.of(period(TUK00_ACTIVE)));
    given(cycleRepository.findByExecDate(EXEC_DATE)).willReturn(Optional.of(entity(DATA_VALID)));

    job.run();

    verify(cycleRepository).save(entity(TUK00_ACTIVE));
    verify(notificationService)
        .sendMessage(
            "📅 PEVA/RAVA faasi muutus: DATA_VALID → TUK00_ACTIVE\n"
                + "Tsükkel: lukustus 2026-03-31, täitmine 2026-05-01\n"
                + "TUK75: D-aktiivne 2026-04-22, müügi tähtaeg 2026-04-27\n"
                + "TUK00: D-aktiivne 2026-04-14, müügi tähtaeg 2026-04-23",
            INVESTMENT);
  }

  @Test
  void persistsDonePhaseWithoutNotification() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.of(period(DONE)));
    given(cycleRepository.findByExecDate(EXEC_DATE)).willReturn(Optional.of(entity(ACTIVE)));

    job.run();

    verify(cycleRepository).save(entity(DONE));
    verifyNoInteractions(notificationService);
  }

  @Test
  void persistsDonePhaseWithoutNotificationOnFirstSight() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.of(period(DONE)));
    given(cycleRepository.findByExecDate(EXEC_DATE)).willReturn(Optional.empty());

    job.run();

    verify(cycleRepository)
        .save(
            PevaRavaCycleEntity.builder()
                .lockDate(LOCK_DATE)
                .execDate(EXEC_DATE)
                .phase(DONE)
                .build());
    verifyNoInteractions(notificationService);
  }

  @Test
  void persistsPhaseWithoutNotificationWhenUnchanged() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.of(period(ACTIVE)));
    given(cycleRepository.findByExecDate(EXEC_DATE)).willReturn(Optional.of(entity(ACTIVE)));

    job.run();

    verify(cycleRepository).save(entity(ACTIVE));
    verifyNoInteractions(notificationService);
  }

  @Test
  void runsOnRequestedEvent() {
    given(periodService.getCurrentPeriod(TODAY)).willReturn(Optional.of(period(ACTIVE)));
    given(cycleRepository.findByExecDate(EXEC_DATE)).willReturn(Optional.of(entity(ACTIVE)));

    job.onPevaRavaPhaseUpdateRequested();

    verify(cycleRepository).save(entity(ACTIVE));
  }

  private static PevaRavaPeriod period(PevaRavaPhase phase) {
    return new PevaRavaPeriod(
        phase,
        new PevaRavaCycle(LOCK_DATE, EXEC_DATE),
        new FundCycleTimeline(LocalDate.of(2026, 4, 22), LocalDate.of(2026, 4, 27), false, false),
        new FundCycleTimeline(LocalDate.of(2026, 4, 14), LocalDate.of(2026, 4, 23), true, false));
  }

  private static PevaRavaCycleEntity entity(PevaRavaPhase phase) {
    return PevaRavaCycleEntity.builder()
        .id(1L)
        .lockDate(LOCK_DATE)
        .execDate(EXEC_DATE)
        .phase(phase)
        .build();
  }
}
