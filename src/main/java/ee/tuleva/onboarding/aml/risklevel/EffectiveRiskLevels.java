package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.EffectiveRiskLevel.HIGH;

import ee.tuleva.onboarding.aml.RiskLevelUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
class EffectiveRiskLevels {

  private static final Duration QUIET_AFTER_AML_SERVICE_FAILED = Duration.ofSeconds(30);

  private final EffectiveRiskLevelClient effectiveRiskLevelClient;
  private final Clock clock;
  private volatile @Nullable Instant quietUntil;

  boolean isHighRisk(String personalCode) {
    Instant askAgainAt = quietUntil;
    if (askAgainAt != null && clock.instant().isBefore(askAgainAt)) {
      throw new RiskLevelUnavailableException(
          "aml-service failed recently, not asking it again yet: askAgainAt=" + askAgainAt);
    }
    try {
      return effectiveRiskLevelClient.effectiveRiskLevels(personalCode).contains(HIGH);
    } catch (RuntimeException e) {
      String reason = reasonWithoutPersonalData(e);
      if (!concernsOnlyThisPerson(e)) {
        quietUntil = clock.instant().plus(QUIET_AFTER_AML_SERVICE_FAILED);
      }
      log.warn("aml-service could not answer the effective risk levels: reason={}", reason);
      throw new RiskLevelUnavailableException(
          "aml-service could not answer the effective risk levels: reason=" + reason);
    }
  }

  private static boolean concernsOnlyThisPerson(RuntimeException failure) {
    return switch (failure) {
      case UnusableRiskLevelAnswerException _, HttpClientErrorException.BadRequest _ -> true;
      default -> false;
    };
  }

  static String reasonWithoutPersonalData(RuntimeException failure) {
    return switch (failure) {
      case RestClientResponseException response -> "HTTP " + response.getStatusCode().value();
      case RiskLevelsRefusedException refused -> refused.reason();
      default -> classNamesOf(failure);
    };
  }

  private static String classNamesOf(Throwable failure) {
    Throwable rootCause = rootCauseOf(failure);
    String failureClass = failure.getClass().getSimpleName();
    return rootCause == failure
        ? failureClass
        : failureClass + "(" + rootCause.getClass().getSimpleName() + ")";
  }

  private static Throwable rootCauseOf(Throwable failure) {
    Set<Throwable> walked = Collections.newSetFromMap(new IdentityHashMap<>());
    walked.add(failure);
    Throwable rootCause = failure;
    Throwable cause = failure.getCause();
    while (cause != null && walked.add(cause)) {
      rootCause = cause;
      cause = cause.getCause();
    }
    return rootCause;
  }
}
