package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.*;
import static ee.tuleva.onboarding.savings.TkfOnboardingDecision.statusAfter;
import static ee.tuleva.onboarding.savings.TkfOnboardingDecision.statusFor;

import ee.tuleva.onboarding.account.SavingsOnboardingCompletion;
import ee.tuleva.onboarding.aml.OnboardingReview.Outcome;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.TkfOnboardingDecision.Assessment;
import ee.tuleva.onboarding.savings.TkfOnboardingDecision.Review;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingCompletedEvent;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsFundOnboardingService implements SavingsOnboardingCompletion {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public boolean isOnboardingCompleted(PartyId partyId) {
    return isOnboardingCompleted(partyId.code(), partyId.type());
  }

  public boolean isOnboardingCompleted(String code, PartyId.Type type) {
    return savingsFundOnboardingRepository.isOnboardingCompleted(code, type);
  }

  public Optional<SavingsFundOnboardingStatus> findStatus(String code, PartyId.Type type) {
    return savingsFundOnboardingRepository.findStatus(code, type);
  }

  public @Nullable SavingsFundOnboardingStatus getOnboardingStatus(PartyId partyId) {
    return savingsFundOnboardingRepository.findStatus(partyId.code(), partyId.type()).orElse(null);
  }

  public void seedPersonOnboardingIfAbsent(String personalCode) {
    boolean inserted =
        savingsFundOnboardingRepository.insertOnboardingStatusIfAbsent(
            personalCode, PERSON, PENDING);
    if (inserted) {
      log.info("Seeded savings fund onboarding: personalCode={}, status={}", personalCode, PENDING);
    }
  }

  public void updateOnboardingStatusIfNeeded(User user, KycCheck kycCheck) {
    SavingsFundOnboardingStatus oldStatus =
        savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON).orElse(null);
    SavingsFundOnboardingStatus newStatus =
        statusFor(new Assessment(oldStatus, kycCheck.riskLevel()));
    if (newStatus == oldStatus) {
      return;
    }
    changeStatus(user, newStatus);
  }

  @Transactional
  public void applyReview(User user, Outcome outcome) {
    SavingsFundOnboardingStatus oldStatus =
        savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON).orElse(null);
    SavingsFundOnboardingStatus newStatus = statusAfter(new Review(oldStatus, outcome));
    if (newStatus == null || newStatus == oldStatus) {
      return;
    }
    log.info(
        "Savings fund onboarding reviewed: userId={}, oldStatus={}, newStatus={}",
        user.getId(),
        oldStatus,
        newStatus);
    changeStatus(user, newStatus);
  }

  @Transactional
  public boolean holdForReview(User user, Instant rejectedBefore) {
    if (!savingsFundOnboardingRepository.holdRejected(
        user.getPersonalCode(), PERSON, rejectedBefore)) {
      return false;
    }
    log.info("Savings fund onboarding held for review again: userId={}", user.getId());
    eventPublisher.publishEvent(
        new TrackableEvent(
            user,
            SAVINGS_FUND_ONBOARDING_STATUS_CHANGE,
            Map.of("oldStatus", REJECTED, "newStatus", ON_HOLD)));
    return true;
  }

  private void changeStatus(User user, SavingsFundOnboardingStatus newStatus) {
    SavingsFundOnboardingStatus previousStatus =
        savingsFundOnboardingRepository
            .saveOnboardingStatus(user.getPersonalCode(), PERSON, newStatus)
            .orElse(null);
    if (newStatus == COMPLETED && previousStatus != COMPLETED) {
      eventPublisher.publishEvent(new SavingsFundOnboardingCompletedEvent(user));
    }
    if (previousStatus == newStatus) {
      return;
    }
    Map<String, @Nullable Object> eventData = new HashMap<>();
    if (previousStatus != null) {
      eventData.put("oldStatus", previousStatus);
    }
    eventData.put("newStatus", newStatus);
    eventPublisher.publishEvent(
        new TrackableEvent(user, SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, eventData));
  }
}
