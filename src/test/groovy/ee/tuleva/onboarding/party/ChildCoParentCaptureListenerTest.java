package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.populationregister.Guardian;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildCoParentCaptureListenerTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final String CO_PARENT = "38002020008";
  private static final String OTHER_CO_PARENT = "48002020009";
  private static final Guardian CO_PARENT_GUARDIAN = guardian(CO_PARENT, "Kati", "Karu");
  private static final Guardian OTHER_CO_PARENT_GUARDIAN =
      guardian(OTHER_CO_PARENT, "Tiit", "Tihane");

  @Mock private CustodyVerificationService custodyVerificationService;
  @Mock private ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  @Mock private UserService userService;
  @Mock private SanctionAndPepScreener sanctionAndPepScreener;

  @InjectMocks private ChildCoParentCaptureListener listener;

  @Test
  void capturesPendingLinksForTheOtherGuardians() {
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT_GUARDIAN));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.empty());

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(CO_PARENT_GUARDIAN, CHILD, "Mari", "Maasikas");
  }

  @Test
  void screensTheCapturedCoParentOnEveryCountryTheCoParentIsTiedTo() {
    User coParent = sampleUserNonMember().personalCode(CO_PARENT).build();
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT_GUARDIAN));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.of(coParent));

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(sanctionAndPepScreener).addSanctionAndPepCheckIfMissing(coParent);
  }

  @Test
  void capturesTheCoParentsNameFromTheRegister() {
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT_GUARDIAN));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.empty());

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(CO_PARENT_GUARDIAN, CHILD, "Mari", "Maasikas");
  }

  @Test
  void screensACoParentWithoutAUserAccountByTheNameInTheRegister() {
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT_GUARDIAN));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.empty());
    var coParent = new PersonImpl(CO_PARENT, "Kati", "Karu");
    given(sanctionAndPepScreener.recordedCitizenships(coParent)).willReturn(Countries.of("FI"));

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(sanctionAndPepScreener).addSanctionAndPepCheckIfMissing(coParent, Countries.of("FI"));
  }

  @Test
  void skipsScreeningWhenTheCoParentHasNeitherAUserAccountNorANameInTheRegister() {
    var unnamedCoParent = guardian(CO_PARENT, null, null);
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(unnamedCoParent));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.empty());

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(unnamedCoParent, CHILD, "Mari", "Maasikas");
    verifyNoInteractions(sanctionAndPepScreener);
  }

  @Test
  void screeningFailureDoesNotUndoTheCapturedLink() {
    User coParent = sampleUserNonMember().personalCode(CO_PARENT).build();
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT_GUARDIAN));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.of(coParent));
    given(sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(coParent))
        .willThrow(new RuntimeException("screening unavailable"));

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(CO_PARENT_GUARDIAN, CHILD, "Mari", "Maasikas");
  }

  @Test
  void swallowsLookupFailureSoItCannotAffectTheAlreadyCommittedParentOnboarding() {
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willThrow(new RuntimeException("population register unavailable"));

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verifyNoInteractions(parentChildLinkRegistrationService);
  }

  @Test
  void capturesRemainingCoParentsWhenOneRegistrationFails() {
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT_GUARDIAN, OTHER_CO_PARENT_GUARDIAN));
    given(
            parentChildLinkRegistrationService.registerPending(
                CO_PARENT_GUARDIAN, CHILD, "Mari", "Maasikas"))
        .willThrow(new RuntimeException("constraint violation"));
    given(userService.findByPersonalCode(OTHER_CO_PARENT)).willReturn(Optional.empty());

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(OTHER_CO_PARENT_GUARDIAN, CHILD, "Mari", "Maasikas");
    verify(userService, never()).findByPersonalCode(CO_PARENT);
  }

  private static Guardian guardian(
      String personalCode, @Nullable String firstName, @Nullable String lastName) {
    return new Guardian(personalCode, PROPERTY_CUSTODY, VALID, ALIVE, firstName, lastName);
  }
}
