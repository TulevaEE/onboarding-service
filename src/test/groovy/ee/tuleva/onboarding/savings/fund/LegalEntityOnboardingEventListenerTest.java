package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybScreeningTrigger.RESCREENING;
import static ee.tuleva.onboarding.kyb.KybScreeningTrigger.SUBMISSION;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.ON_HOLD;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyb.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class LegalEntityOnboardingEventListenerTest {

  private static final String APPLICANT = "38501010001";
  private static final String OTHER_OWNER = "38001010001";

  private final SavingsFundOnboardingRepository repository =
      mock(SavingsFundOnboardingRepository.class);
  private final List<Object> publishedEvents = new ArrayList<>();
  private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
  private final LegalEntityOnboardingEventListener listener =
      new LegalEntityOnboardingEventListener(repository, eventPublisher);

  private final CompanyDto company =
      new CompanyDto(new RegistryCode("12345678"), "Test OÜ", List.of("62011"), LegalForm.OÜ);

  private final List<KybRelatedPerson> relatedPersons =
      List.of(boardMemberOwner("38501010001", 100.0).kycStatus(KybKycStatus.COMPLETED).build());

  @Test
  void setsStatusCompletedWhenAllChecksPass() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED);
  }

  @Test
  void setsStatusRejectedWhenAnyCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void setsStatusCompletedWhenOnlyDataChangedCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED);
  }

  @Test
  void setsStatusCompletedWhenOnlyRiskSignalCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()),
            new KybCheck(COMPANY_AGE, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED);
  }

  @Test
  void setsStatusRejectedEvenIfPreviouslyCompletedWhenNonOwnershipGateCheckFails() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void keepsCompletedForManualReviewWhenOnlyTheActivityCheckFails() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(HIGH_RISK_NACE, false, Map.of("activityCodes", List.of("66191"))),
            dataChangedFor(HIGH_RISK_NACE));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void keepsCompletedWhenTheActivityCheckFailsAlongsideAnUnchangedOwnershipFailure() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(HIGH_RISK_NACE, false, Map.of("activityCodes", List.of("66191"))),
            new KybCheck(DUAL_MEMBER_OWNERSHIP, false, Map.of("totalOwnership", "100")),
            dataChangedFor(HIGH_RISK_NACE));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void setsStatusRejectedWhenTheActivityCheckFailsAlongsideOwnershipThatChanged() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(HIGH_RISK_NACE, false, Map.of("activityCodes", List.of("66191"))),
            new KybCheck(DUAL_MEMBER_OWNERSHIP, false, Map.of("totalOwnership", "60")),
            dataChangedFor(DUAL_MEMBER_OWNERSHIP));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void setsStatusRejectedWhenACompletedCompanyFailsTheActivityCheckAlongsideAnotherGateCheck() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, false, Map.of()),
            new KybCheck(HIGH_RISK_NACE, false, Map.of("activityCodes", List.of("66191"))),
            dataChangedFor(COMPANY_ACTIVE));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void holdsAPendingCompanyThatFailsTheActivityCheckForReview() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(PENDING));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(HIGH_RISK_NACE, false, Map.of("activityCodes", List.of("66301"))));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, ON_HOLD);
  }

  @Test
  void holdsANewApplicationThatFailsTheActivityCheckForReview() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.empty());
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(HIGH_RISK_NACE, false, Map.of("activityCodes", List.of("66301"))));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, ON_HOLD);
    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void holdsANewApplicationWithASanctionOrPepHitOrAnInactiveStatusForReview() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, false, Map.of()),
            new KybCheck(COMPANY_SANCTION, false, Map.of()),
            new KybCheck(COMPANY_PEP, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, ON_HOLD);
  }

  @Test
  void rejectsANewApplicationThatAlsoFailsAnEligibilityCheck() {
    var checks =
        List.of(
            new KybCheck(HIGH_RISK_NACE, false, Map.of()),
            new KybCheck(COMPANY_LEGAL_FORM, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void waitsForRelatedPersonsBeforeHoldingACompanyForReview() {
    var checks =
        List.of(
            new KybCheck(HIGH_RISK_NACE, false, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING);
  }

  @Test
  void keepsACompletedCompanyCompletedWhenAnAcceptedHighRiskFindingIsUnchanged() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_SANCTION, false, Map.of()),
            new KybCheck(COMPANY_PEP, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void rejectsACompletedCompanyWhenAHighRiskFindingIsNew() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(new KybCheck(COMPANY_SANCTION, false, Map.of()), dataChangedFor(COMPANY_SANCTION));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void keepsAHeldCompanyHeldWhateverTheScreeningFinds() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(ON_HOLD));

    listener.onKybCheckPerformed(
        eventWith(List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())), RESCREENING));
    listener.onKybCheckPerformed(
        eventWith(List.of(new KybCheck(COMPANY_STRUCTURE, false, Map.of())), SUBMISSION));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void keepsARejectedCompanyRejectedWhenMonitoringFindsOnlyAHighRiskIndicator() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(REJECTED));

    listener.onKybCheckPerformed(
        eventWith(List.of(new KybCheck(HIGH_RISK_NACE, false, Map.of())), RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void holdsARejectedCompanyForReviewWhenItsFreshSubmissionFailsOnlyAHighRiskIndicator() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(REJECTED));

    listener.onKybCheckPerformed(
        eventWith(List.of(new KybCheck(HIGH_RISK_NACE, false, Map.of())), SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, ON_HOLD);
  }

  @Test
  void keepsCompletedWhenOwnershipCheckFailsWithoutEvidenceOfChange() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                SOLE_MEMBER_OWNERSHIP,
                false,
                Map.of("personalCode", "38501010001", "ownershipPercent", "100")),
            new KybCheck(
                DATA_CHANGED,
                false,
                Map.of(
                    "changes",
                    List.of(
                        Map.of(
                            "check", "SOLE_MEMBER_OWNERSHIP",
                            "previousSuccess", true,
                            "currentSuccess", false,
                            "metadataChanged", false)))));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void keepsCompletedWhenOwnershipCheckFailsAndNoDataChangedCheckPresent() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(SINGLE_BOARD_MEMBER_OWNERSHIP, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void setsStatusRejectedWhenOwnershipDataActuallyChanged() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                SOLE_MEMBER_OWNERSHIP,
                false,
                Map.of("personalCode", "39901010000", "ownershipPercent", "100")),
            new KybCheck(
                DATA_CHANGED,
                false,
                Map.of(
                    "changes",
                    List.of(
                        Map.of(
                            "check", "SOLE_MEMBER_OWNERSHIP",
                            "previousSuccess", true,
                            "currentSuccess", false,
                            "metadataChanged", true)))));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void keepsCompletedWhenFailingOwnershipCheckHasNoOwnEvidenceDespiteStaleRemovedCheckEntry() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(DUAL_MEMBER_OWNERSHIP, false, Map.of("totalOwnership", "100")),
            new KybCheck(
                DATA_CHANGED,
                false,
                Map.of(
                    "changes",
                    List.of(
                        Map.of(
                            "check",
                            "SOLE_MEMBER_OWNERSHIP",
                            "previousSuccess",
                            true,
                            "currentSuccess",
                            "N/A",
                            "metadataChanged",
                            true)))));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void setsStatusRejectedWhenFailingOwnershipCheckItselfShowsMetadataChange() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(DUAL_MEMBER_OWNERSHIP, false, Map.of("totalOwnership", "60")),
            new KybCheck(
                DATA_CHANGED,
                false,
                Map.of(
                    "changes",
                    List.of(
                        Map.of(
                            "check",
                            "DUAL_MEMBER_OWNERSHIP",
                            "previousSuccess",
                            true,
                            "currentSuccess",
                            false,
                            "metadataChanged",
                            true)))));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void holdsANewCompanyWhoseOwnershipCheckFailsForReview() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.empty());
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, ON_HOLD);
  }

  @Test
  void doesNothingWhenStatusUnchanged() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void setsStatusPendingWhenOnlyRelatedPersonsKycFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING);
  }

  @Test
  void setsStatusPendingWhenRescreeningACompanyWithoutAStatusFindsOnlyRelatedPersonsKycFailing() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING);
  }

  @Test
  void setsStatusRejectedWhenRelatedPersonsKycFailsAlongsideAnotherGateCheck() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  // An account that is already open losing a related person's verification is an
  // alarm, not a company waiting to be onboarded, so it must not soften to pending.
  @Test
  void setsStatusRejectedWhenRelatedPersonsKycFailsAfterCompletion() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  // Monitoring must not heal a rejection on its own: only a fresh survey submission clears the
  // slate and lets a company back into the waiting queue.
  @Test
  void keepsRejectedWhenRescreeningARejectedCompanyFindsOnlyRelatedPersonsKycFailing() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(REJECTED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  // The mirror image: the applicant fixed what got them rejected and submitted again, so the
  // company earns its place back in the waiting queue.
  @Test
  void queuesARejectedCompanyAgainWhenAFreshSubmissionOnlyLacksRelatedPersonsKyc() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(REJECTED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING);
  }

  // Submitting is blocked for a completed company, so this cannot happen through the survey; the
  // rule is pinned anyway because an open account must never be walked back into the queue.
  @Test
  void neverQueuesACompletedCompanyEvenUnderASubmissionTrigger() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
    verify(repository, never()).saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING);
  }

  // The company was open, monitoring demoted it, and the next monitoring run must not walk it
  // back into the queue and re-send an account-opened email for an account that never closed.
  @Test
  void neverQueuesACompanyThatMonitoringDemotedFromCompleted() {
    given(repository.findStatus("12345678", LEGAL_ENTITY))
        .willReturn(Optional.of(COMPLETED), Optional.of(REJECTED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));
    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, times(1)).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
    verify(repository, never()).saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING);
  }

  @Test
  void completesAPendingCompanyOnceRelatedPersonsAreVerified() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(PENDING));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED);
  }

  @Test
  void publishesOnboardedEventWhenAPendingCompanyCompletes() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(PENDING));
    given(repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED))
        .willReturn(Optional.of(PENDING));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    assertThat(publishedEvents)
        .singleElement(type(LegalEntityOnboardedEvent.class))
        .returns(
            new LegalEntityOnboardedEvent.OnboardedCompany(company.registryCode(), company.name()),
            LegalEntityOnboardedEvent::getCompany)
        .returns(listener, LegalEntityOnboardedEvent::getSource);
  }

  @Test
  void publishesOnboardedEventWhenANewCompanyCompletesOnTheSpot() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    assertThat(publishedEvents)
        .singleElement(type(LegalEntityOnboardedEvent.class))
        .returns(
            new LegalEntityOnboardedEvent.OnboardedCompany(company.registryCode(), company.name()),
            LegalEntityOnboardedEvent::getCompany);
  }

  @Test
  void publishesOnboardedEventWhenARejectedCompanyCompletesOnResubmission() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(REJECTED));
    given(repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED))
        .willReturn(Optional.of(REJECTED));
    var checks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    assertThat(publishedEvents).singleElement(type(LegalEntityOnboardedEvent.class));
  }

  @Test
  void publishesNoOnboardedEventWhenMonitoringReopensACompanyItHadRejected() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(REJECTED));
    given(repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED))
        .willReturn(Optional.of(REJECTED));
    var checks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED);
    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void publishesNoOnboardedEventWhenAnotherRelatedPersonAlreadyCompletedTheCompany() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(PENDING));
    given(repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED))
        .willReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void publishesWaitingEventWhenANewCompanyStartsWaitingForAnotherRelatedPerson() {
    var checks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of()), waitingFor(OTHER_OWNER));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    assertThat(publishedEvents)
        .containsExactly(
            new LegalEntityWaitingForRelatedPersonsEvent(company.registryCode(), company.name()));
  }

  @Test
  void publishesWaitingEventWhenAResubmittedRejectedCompanyStartsWaitingForAnotherRelatedPerson() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(REJECTED));
    given(repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING))
        .willReturn(Optional.of(REJECTED));
    var checks = List.of(waitingFor(OTHER_OWNER));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    assertThat(publishedEvents)
        .containsExactly(
            new LegalEntityWaitingForRelatedPersonsEvent(company.registryCode(), company.name()));
  }

  @Test
  void publishesNoWaitingEventWhenAConcurrentScreeningAlreadyQueuedTheCompany() {
    given(repository.saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING))
        .willReturn(Optional.of(PENDING));
    var checks = List.of(waitingFor(OTHER_OWNER));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void publishesNoWaitingEventWhenOnlyTheApplicantHasYetToVerifyTheirIdentity() {
    var checks = List.of(waitingFor(APPLICANT));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING);
    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void publishesNoWaitingEventWhenTheCompanyWouldBeHeldForReviewOnceTheOtherPersonVerifies() {
    var checks = List.of(new KybCheck(HIGH_RISK_NACE, false, Map.of()), waitingFor(OTHER_OWNER));

    listener.onKybCheckPerformed(eventWith(checks, SUBMISSION));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, PENDING);
    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void leavesAPendingCompanyAloneWhileRelatedPersonsAreStillMissing() {
    given(repository.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(PENDING));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(RELATED_PERSONS_KYC, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks, RESCREENING));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  private static KybCheck dataChangedFor(KybCheckType changedCheck) {
    return new KybCheck(
        DATA_CHANGED,
        false,
        Map.of(
            "changes",
            List.of(
                Map.of(
                    "check", changedCheck.name(),
                    "previousSuccess", true,
                    "currentSuccess", false,
                    "metadataChanged", true))));
  }

  private static KybCheck waitingFor(String personalCode) {
    return new KybCheck(
        RELATED_PERSONS_KYC,
        false,
        RelatedPersonsKycMetadata.forIncompletePersons(
            List.of(boardMemberOwner(personalCode, 50.0).kycStatus(KybKycStatus.UNKNOWN).build())));
  }

  private KybCheckPerformedEvent eventWith(List<KybCheck> checks, KybScreeningTrigger trigger) {
    return new KybCheckPerformedEvent(
        this, company, new PersonalCode(APPLICANT), relatedPersons, checks, List.of(), trigger);
  }
}
