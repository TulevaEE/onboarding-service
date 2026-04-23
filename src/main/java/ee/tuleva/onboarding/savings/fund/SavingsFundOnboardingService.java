package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;

import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.user.User;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsFundOnboardingService {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final ApplicationEventPublisher eventPublisher;

  public boolean isOnboardingCompleted(PartyId partyId) {
    return isOnboardingCompleted(partyId.code(), partyId.type());
  }

  public boolean isOnboardingCompleted(String code, PartyId.Type type) {
    return savingsFundOnboardingRepository.isOnboardingCompleted(code, type);
  }

  public SavingsFundOnboardingStatus getOnboardingStatus(PartyId partyId) {
    return savingsFundOnboardingRepository.findStatus(partyId.code(), partyId.type()).orElse(null);
  }

  public void updateOnboardingStatusIfNeeded(User user, KycCheck kycCheck) {
    SavingsFundOnboardingStatus oldStatus =
        savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON).orElse(null);
    if (oldStatus == COMPLETED) {
      return;
    }
    SavingsFundOnboardingStatus newStatus = mapRiskLevelToStatus(kycCheck.riskLevel());
    if (newStatus == oldStatus) {
      return;
    }
    savingsFundOnboardingRepository.saveOnboardingStatus(user.getPersonalCode(), PERSON, newStatus);
    if (newStatus == COMPLETED) {
      eventPublisher.publishEvent(new SavingsFundOnboardingCompletedEvent(user));
    }
    Map<String, Object> eventData = new HashMap<>();
    if (oldStatus != null) {
      eventData.put("oldStatus", oldStatus);
    }
    eventData.put("newStatus", newStatus);
    eventPublisher.publishEvent(
        new TrackableEvent(user, SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, eventData));
  }

  public void whitelistLegalEntity(String registryCode, boolean override) {
    var existingStatus =
        savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY).orElse(null);
    if (existingStatus == WHITELISTED) {
      return;
    }
    if (existingStatus != null && !override) {
      throw new CompanyAlreadyHasOnboardingStatusException(registryCode, existingStatus);
    }

    log.info(
        "Whitelisting company: registryCode={}, previousStatus={}, override={}",
        registryCode,
        existingStatus,
        override);
    savingsFundOnboardingRepository.saveOnboardingStatus(registryCode, LEGAL_ENTITY, WHITELISTED);

    var eventData = new LinkedHashMap<String, Object>();
    eventData.put("partyType", LEGAL_ENTITY.name());
    eventData.put("registryCode", registryCode);
    if (existingStatus != null) {
      eventData.put("oldStatus", existingStatus);
    }
    eventData.put("newStatus", WHITELISTED);
    eventData.put("outcome", "WHITELISTED");
    eventData.put("override", override);
    eventPublisher.publishEvent(
        new TrackableSystemEvent(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, eventData));
  }

  private SavingsFundOnboardingStatus mapRiskLevelToStatus(RiskLevel riskLevel) {
    return switch (riskLevel) {
      case LOW, NONE -> COMPLETED;
      case MEDIUM -> PENDING;
      case HIGH -> REJECTED;
    };
  }
}
