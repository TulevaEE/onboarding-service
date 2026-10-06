package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  ConsecutiveBreachTracker.class,
  TrackingDifferenceCalculator.class,
  InvestmentParameterRepository.class,
  PublicHolidays.class
})
class TrackingDifferenceEscalationIT {

  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);
  private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 15);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 16);
  private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 17);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 18);
  private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 9, 21);
  private static final LocalDate SEPTEMBER_28 = LocalDate.of(2026, 9, 28);
  private static final LocalDate SEPTEMBER_29 = LocalDate.of(2026, 9, 29);
  private static final LocalDate SEPTEMBER_30 = LocalDate.of(2026, 9, 30);
  private static final LocalDate TEN_WORKING_DAYS_WITHOUT_A_CHECK_LATER =
      LocalDate.of(2026, 10, 15);
  private static final LocalDate ELEVEN_WORKING_DAYS_WITHOUT_A_CHECK_LATER =
      LocalDate.of(2026, 10, 16);

  private static final LocalDate TUESDAY_BEFORE_ANY_ESCALATION_PARAMETER =
      LocalDate.of(2024, 12, 10);
  private static final LocalDate WEDNESDAY_BEFORE_ANY_ESCALATION_PARAMETER =
      LocalDate.of(2024, 12, 11);
  private static final LocalDate THURSDAY_BEFORE_ANY_ESCALATION_PARAMETER =
      LocalDate.of(2024, 12, 12);
  private static final LocalDate FRIDAY_BEFORE_ANY_ESCALATION_PARAMETER =
      LocalDate.of(2024, 12, 13);

  private static final String ESCALATION_HEADER = "TD ESCALATION";
  private static final String FALLBACK_WARNING = "decided on built-in fallback constants";

  @Autowired private ConsecutiveBreachTracker tracker;
  @Autowired private TrackingDifferenceCalculator calculator;
  @Autowired private TrackingDifferenceEventRepository eventRepository;

  private final RecordingNotificationService notifications = new RecordingNotificationService();
  private TrackingDifferenceNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier =
        new TrackingDifferenceNotifier(
            notifications, calculator, mock(RedemptionCycleLookup.class));
  }

  @Test
  void threeBreachDaysThenACleanDayAreNotifiedOnTheFourthWorkingDay() {
    var escalated =
        escalatedOn(breach(TUESDAY), breach(WEDNESDAY), breach(THURSDAY), clean(FRIDAY));

    assertThat(escalated).containsExactly(FRIDAY);
  }

  @Test
  void theCleanFourthDayIsStoredWithNoStreakOfItsOwn() {
    escalatedOn(breach(TUESDAY), breach(WEDNESDAY), breach(THURSDAY), clean(FRIDAY));

    assertThat(
            eventRepository.findAll().stream()
                .sorted(comparing(TrackingDifferenceEvent::getCheckDate))
                .map(TrackingDifferenceEvent::getConsecutiveBreachDays))
        .containsExactly(1, 2, 3, 0);
  }

  @Test
  void threeBreachDaysWhoseTdNetsOutBelowTheEscalationThresholdAreNotNotifiedOnTheCleanDay() {
    var escalated =
        escalatedOn(
            breach(TUESDAY, "0.0020"),
            breach(WEDNESDAY, "-0.0015"),
            breach(THURSDAY, "-0.0010"),
            clean(FRIDAY));

    assertThat(escalated).isEmpty();
  }

  @Test
  void aFourthConsecutiveBreachDayStillEscalatesOnTheDayItself() {
    var escalated =
        escalatedOn(breach(TUESDAY), breach(WEDNESDAY), breach(THURSDAY), breach(FRIDAY));

    assertThat(escalated).containsExactly(FRIDAY);
  }

  @Test
  void aFourthBreachDayThatNetsTheStreakBelowTheThresholdIsStillNotifiedForTheThreeDaysBeforeIt() {
    var escalated =
        escalatedOn(
            breach(TUESDAY, "0.0020"),
            breach(WEDNESDAY, "0.0020"),
            breach(THURSDAY, "-0.0012"),
            breach(FRIDAY, "-0.0020"),
            clean(NEXT_MONDAY));

    assertThat(escalated).containsExactly(FRIDAY);
  }

  @Test
  void aCleanDayAfterFiveBreachDaysIsNotNotifiedAgainBecauseTheFourthDayAlreadyWas() {
    var escalated =
        escalatedOn(
            breach(MONDAY),
            breach(TUESDAY),
            breach(WEDNESDAY),
            breach(THURSDAY),
            breach(FRIDAY),
            clean(NEXT_MONDAY));

    assertThat(escalated).containsExactly(THURSDAY, FRIDAY);
  }

  @Test
  void aCleanDayAfterTwoBreachDaysIsNotNotified() {
    var escalated = escalatedOn(breach(WEDNESDAY), breach(THURSDAY), clean(FRIDAY));

    assertThat(escalated).isEmpty();
  }

  @Test
  void aFourthWorkingDayThatWasNeverCheckedIsNotifiedLateOnTheCleanDayAfterIt() {
    var escalated = escalatedOn(breach(MONDAY), breach(TUESDAY), breach(WEDNESDAY), clean(FRIDAY));

    assertThat(escalated).containsExactly(FRIDAY);
    assertThat(notifications.lastMessage())
        .contains("closed a 3-day breach streak, and no check ran in between")
        .contains("so it is sent now, late");
  }

  @Test
  void anUncheckedDayBetweenBreachDaysCountsTowardsTheFourthWorkingDay() {
    var escalated = escalatedOn(breach(MONDAY), breach(TUESDAY), breach(THURSDAY));

    assertThat(escalated).containsExactly(THURSDAY);
    assertThat(notifications.lastMessage())
        .contains("[4 consecutive days, 1 of them with no check");
    assertThat(
            eventRepository.findAll().stream()
                .sorted(comparing(TrackingDifferenceEvent::getCheckDate))
                .map(TrackingDifferenceEvent::getConsecutiveBreachDays))
        .containsExactly(1, 2, 4);
  }

  @Test
  void anUncheckedDayBeforeACleanDayDoesNotLengthenTheStreakItEnded() {
    var escalated = escalatedOn(breach(TUESDAY), breach(WEDNESDAY), clean(FRIDAY));

    assertThat(escalated).isEmpty();
  }

  @Test
  void aStreakIsStillNotifiedLateAfterTenWorkingDaysWithNoCheck() {
    var escalated =
        escalatedOn(
            breach(SEPTEMBER_28),
            breach(SEPTEMBER_29),
            breach(SEPTEMBER_30),
            clean(TEN_WORKING_DAYS_WITHOUT_A_CHECK_LATER));

    assertThat(escalated).containsExactly(TEN_WORKING_DAYS_WITHOUT_A_CHECK_LATER);
    assertThat(notifications.lastMessage()).contains("so it is sent now, late");
  }

  @Test
  void elevenWorkingDaysWithNoCheckEndTheStreakSoTheCleanDayAfterThemSendsNoLateNotice() {
    var escalated =
        escalatedOn(
            breach(SEPTEMBER_28),
            breach(SEPTEMBER_29),
            breach(SEPTEMBER_30),
            clean(ELEVEN_WORKING_DAYS_WITHOUT_A_CHECK_LATER));

    assertThat(escalated).isEmpty();
  }

  @Test
  void aBreachAfterElevenWorkingDaysWithNoCheckStartsANewStreak() {
    escalatedOn(
        breach(SEPTEMBER_28),
        breach(SEPTEMBER_29),
        breach(SEPTEMBER_30),
        breach(ELEVEN_WORKING_DAYS_WITHOUT_A_CHECK_LATER));

    assertThat(
            eventRepository.findAll().stream()
                .sorted(comparing(TrackingDifferenceEvent::getCheckDate))
                .map(TrackingDifferenceEvent::getConsecutiveBreachDays))
        .containsExactly(1, 2, 3, 1);
  }

  @Test
  void withoutEscalationParametersTheFallbackStillNotifiesTheCleanFourthWorkingDay() {
    var escalated =
        escalatedOn(
            breach(TUESDAY_BEFORE_ANY_ESCALATION_PARAMETER),
            breach(WEDNESDAY_BEFORE_ANY_ESCALATION_PARAMETER),
            breach(THURSDAY_BEFORE_ANY_ESCALATION_PARAMETER),
            clean(FRIDAY_BEFORE_ANY_ESCALATION_PARAMETER));

    assertThat(escalated).containsExactly(FRIDAY_BEFORE_ANY_ESCALATION_PARAMETER);
    assertThat(notifications.lastMessage()).contains(FALLBACK_WARNING);
  }

  private List<LocalDate> escalatedOn(CheckedDay... days) {
    var escalated = new ArrayList<LocalDate>();
    for (var day : days) {
      var result =
          tracker.updateConsecutiveCount(
              day.calculated(),
              tracker.countConsecutiveBreaches(TUK75, MODEL_PORTFOLIO, day.date()));
      eventRepository.save(TrackingDifferenceEventMapper.toEvent(result));
      notifier.notify(List.of(result));
      if (notifications.lastMessage().contains(ESCALATION_HEADER)) {
        escalated.add(day.date());
      }
    }
    return escalated;
  }

  private static CheckedDay breach(LocalDate date) {
    return breach(date, "0.0020");
  }

  private static CheckedDay breach(LocalDate date, String trackingDifference) {
    return new CheckedDay(date, new BigDecimal(trackingDifference), true);
  }

  private static CheckedDay clean(LocalDate date) {
    return new CheckedDay(date, new BigDecimal("0.0002"), false);
  }

  private record CheckedDay(LocalDate date, BigDecimal trackingDifference, boolean breach) {

    TrackingDifferenceResult calculated() {
      return TrackingDifferenceResult.builder()
          .fund(TUK75)
          .checkDate(date)
          .checkType(MODEL_PORTFOLIO)
          .trackingDifference(trackingDifference)
          .fundReturn(trackingDifference)
          .benchmarkReturn(ZERO)
          .breach(breach)
          .securityAttributions(List.of())
          .cashDrag(ZERO)
          .feeDrag(ZERO)
          .residual(ZERO)
          .benchmarkGapIsins(List.of())
          .build();
    }
  }

  private static class RecordingNotificationService implements OperationsNotificationService {

    private final List<String> messages = new ArrayList<>();

    @Override
    public void sendMessage(String message, Channel channel) {
      messages.add(message);
    }

    @Override
    public void sendMessage(String message, Channel channel, Severity severity) {
      sendMessage(message, channel);
    }

    String lastMessage() {
      return messages.getLast();
    }
  }
}
