package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.ALREADY_ONBOARDED;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.NOT_BOARD_MEMBER;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.ONBOARDING_PENDING;

import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyb.CompanyOnboarding;
import java.util.LinkedHashMap;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OnboardingGate {

  private final CompanyOnboarding companyOnboarding;
  private final ApplicationEventPublisher eventPublisher;

  Optional<ValidationError> getOnboardingError(String registryCode) {
    return getBlockedReason(registryCode).map(OnboardingGate::blockedReasonError);
  }

  void verifyOnboardingAllowed(String registryCode, String personalCode) {
    var blockedReason = getBlockedReason(registryCode);
    if (blockedReason.isEmpty()) {
      return;
    }
    var reason = blockedReason.get();
    auditBlocked(registryCode, personalCode, reason);
    throw new OnboardingNotAllowedException(registryCode, reason);
  }

  void auditBlocked(String registryCode, String personalCode, BlockedReason reason) {
    log.warn(
        "Company onboarding blocked: registryCode={}, personalCode={}, reason={}",
        registryCode,
        personalCode,
        reason);

    var data = new LinkedHashMap<String, @Nullable Object>();
    data.put("partyType", "LEGAL_ENTITY");
    data.put("registryCode", registryCode);
    data.put("personalCode", personalCode);
    data.put("outcome", "BLOCKED");
    data.put("blockedReason", reason.name());

    eventPublisher.publishEvent(
        new TrackableSystemEvent(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, data));
  }

  private Optional<BlockedReason> getBlockedReason(String registryCode) {
    return companyOnboarding.findState(registryCode).flatMap(OnboardingGate::blockedReasonFor);
  }

  private static Optional<BlockedReason> blockedReasonFor(CompanyOnboarding.State status) {
    return switch (status) {
      case COMPLETED -> Optional.of(ALREADY_ONBOARDED);
      case PENDING -> Optional.of(ONBOARDING_PENDING);
      case REJECTED -> Optional.empty();
    };
  }

  private static ValidationError blockedReasonError(BlockedReason reason) {
    return new ValidationError(reason.name(), blockedReasonMessage(reason));
  }

  private static String blockedReasonMessage(BlockedReason reason) {
    return switch (reason) {
      case ALREADY_ONBOARDED -> "Ettevõte on juba liitunud";
      case ONBOARDING_PENDING -> "Ettevõtte liitumine on pooleli";
      case NOT_BOARD_MEMBER -> "Isik ei ole ettevõtte juhatuse liige";
    };
  }
}
