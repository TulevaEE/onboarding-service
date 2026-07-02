package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyb.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegalEntityOnboardingEventListenerTest {

  private final SavingsFundOnboardingRepository repository =
      mock(SavingsFundOnboardingRepository.class);
  private final LegalEntityOnboardingEventListener listener =
      new LegalEntityOnboardingEventListener(repository);

  private final CompanyDto company =
      new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ);

  private final List<KybRelatedPerson> relatedPersons =
      List.of(boardMemberOwner("38501010001", 100.0).kycStatus(KybKycStatus.COMPLETED).build());

  @Test
  void setsStatusCompletedWhenAllChecksPass() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED);
  }

  @Test
  void setsStatusRejectedWhenAnyCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void setsStatusCompletedWhenOnlyDataChangedCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED);
  }

  @Test
  void setsStatusCompletedWhenOnlyRiskSignalCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()),
            new KybCheck(COMPANY_AGE, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, COMPLETED);
  }

  @Test
  void setsStatusRejectedEvenIfPreviouslyCompletedWhenNonOwnershipGateCheckFails() {
    when(repository.findStatus("12345678", LEGAL_ENTITY)).thenReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void keepsCompletedWhenOwnershipCheckFailsWithoutEvidenceOfChange() {
    when(repository.findStatus("12345678", LEGAL_ENTITY)).thenReturn(Optional.of(COMPLETED));
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

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void keepsCompletedWhenOwnershipCheckFailsAndNoDataChangedCheckPresent() {
    when(repository.findStatus("12345678", LEGAL_ENTITY)).thenReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(SINGLE_BOARD_MEMBER_OWNERSHIP, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void setsStatusRejectedWhenOwnershipDataActuallyChanged() {
    when(repository.findStatus("12345678", LEGAL_ENTITY)).thenReturn(Optional.of(COMPLETED));
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

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void keepsCompletedWhenFailingOwnershipCheckHasNoOwnEvidenceDespiteStaleRemovedCheckEntry() {
    when(repository.findStatus("12345678", LEGAL_ENTITY)).thenReturn(Optional.of(COMPLETED));
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

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  @Test
  void setsStatusRejectedWhenFailingOwnershipCheckItselfShowsMetadataChange() {
    when(repository.findStatus("12345678", LEGAL_ENTITY)).thenReturn(Optional.of(COMPLETED));
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

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void setsStatusRejectedForNewCompanyWhenOwnershipCheckFails() {
    when(repository.findStatus("12345678", LEGAL_ENTITY)).thenReturn(Optional.empty());
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, false, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository).saveOnboardingStatus("12345678", LEGAL_ENTITY, REJECTED);
  }

  @Test
  void doesNothingWhenStatusUnchanged() {
    when(repository.findStatus("12345678", LEGAL_ENTITY)).thenReturn(Optional.of(COMPLETED));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()));

    listener.onKybCheckPerformed(eventWith(checks));

    verify(repository, never()).saveOnboardingStatus(any(), any(), any());
  }

  private KybCheckPerformedEvent eventWith(List<KybCheck> checks) {
    return new KybCheckPerformedEvent(
        this, company, new PersonalCode("38501010001"), relatedPersons, checks, List.of());
  }
}
