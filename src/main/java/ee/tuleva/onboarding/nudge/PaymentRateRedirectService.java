package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.ExperimentArm.TREATMENT;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
class PaymentRateRedirectService {

  private static final String NUDGE_KEY = NudgeKey.SECOND_PILLAR_PAYMENT_RATE.name();

  private final PaymentRateRedirectProperties properties;
  private final PaymentRateRedirectEligibility eligibility;
  private final NudgeExposureRepository nudgeExposureRepository;
  private final UserService userService;
  private final PaymentRateSeasons paymentRateSeasons;
  private final Clock estonianClock;

  PaymentRateRedirect assign(AuthenticatedPerson person) {
    if (!isWindowOpenFor(person)) {
      return PaymentRateRedirect.no();
    }
    if (alreadyAssigned(person)) {
      return showASecondTimeIfDue(person);
    }
    User user = userService.getByIdOrThrow(person.getUserIdOrThrow());
    if (!eligibility.isEligible(user)) {
      return PaymentRateRedirect.no();
    }
    ExperimentArm arm =
        ExperimentArm.assign(
            person.getPersonalCode(), properties.activeSeed(), properties.holdoutPercent());
    boolean assigned =
        nudgeExposureRepository.recordAssignment(
            person.getUserIdOrThrow(), NUDGE_KEY, seasonYear(), arm, estonianClock.instant());
    log.info(
        "Payment rate redirect assigned: userId={}, arm={}, seasonYear={}, firstOfTheSeason={}",
        person.getUserIdOrThrow(),
        arm,
        seasonYear(),
        assigned);
    return assigned && arm == TREATMENT
        ? PaymentRateRedirect.to(arm, seasonYear())
        : PaymentRateRedirect.no();
  }

  private PaymentRateRedirect showASecondTimeIfDue(AuthenticatedPerson person) {
    final Period SECOND_SHOWING_AFTER = Period.ofDays(7);
    long userId = person.getUserIdOrThrow();
    LocalDate lastFirstShowingDay = LocalDate.now(estonianClock).minus(SECOND_SHOWING_AFTER);
    Instant firstShownBefore =
        lastFirstShowingDay.plusDays(1).atStartOfDay(estonianClock.getZone()).toInstant();
    if (!nudgeExposureRepository.isDueForSecondShowing(
            userId, NUDGE_KEY, seasonYear(), firstShownBefore)
        || !eligibility.isEligible(userService.getByIdOrThrow(userId))) {
      return PaymentRateRedirect.no();
    }
    if (!nudgeExposureRepository.recordSecondShowing(
        userId, NUDGE_KEY, seasonYear(), firstShownBefore, estonianClock.instant())) {
      return PaymentRateRedirect.no();
    }
    log.info(
        "Payment rate redirect shown a second time: userId={}, seasonYear={}",
        userId,
        seasonYear());
    return PaymentRateRedirect.to(TREATMENT, seasonYear());
  }

  void dismiss(AuthenticatedPerson person) {
    if (!person.isActingAsSelf()) {
      return;
    }
    nudgeExposureRepository.recordDismissal(
        person.getUserIdOrThrow(), NUDGE_KEY, seasonYear(), estonianClock.instant());
  }

  private boolean alreadyAssigned(AuthenticatedPerson person) {
    return nudgeExposureRepository.hasAssignment(
        person.getUserIdOrThrow(), NUDGE_KEY, seasonYear());
  }

  private boolean isWindowOpenFor(AuthenticatedPerson person) {
    if (!person.isActingAsSelf()) {
      return false;
    }
    LocalDate today = LocalDate.now(estonianClock);
    return !today.isBefore(properties.startDate()) && !today.isAfter(windowEnd());
  }

  private LocalDate windowEnd() {
    return paymentRateSeasons.deadlineFor(properties.startDate());
  }

  private int seasonYear() {
    return windowEnd().getYear();
  }
}
