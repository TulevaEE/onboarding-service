package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Optional;
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

  @Mock private CustodyVerificationService custodyVerificationService;
  @Mock private ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  @Mock private UserService userService;
  @Mock private AmlService amlService;

  @InjectMocks private ChildCoParentCaptureListener listener;

  @Test
  void capturesPendingLinksForTheOtherGuardians() {
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.empty());

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(CO_PARENT, CHILD, "Mari", "Maasikas");
  }

  @Test
  void screensTheCapturedCoParentForSanctionsAndPep() {
    User coParent = sampleUserNonMember().personalCode(CO_PARENT).build();
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.of(coParent));

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(amlService).addSanctionAndPepCheckIfMissing(coParent, new Country(null));
  }

  @Test
  void skipsScreeningWhenTheCoParentHasNoUserAccount() {
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.empty());

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(CO_PARENT, CHILD, "Mari", "Maasikas");
    verify(amlService, never()).addSanctionAndPepCheckIfMissing(any(), any());
  }

  @Test
  void screeningFailureDoesNotUndoTheCapturedLink() {
    User coParent = sampleUserNonMember().personalCode(CO_PARENT).build();
    given(custodyVerificationService.findGuardiansWithAssetManagement(CHILD, PARENT))
        .willReturn(List.of(CO_PARENT));
    given(userService.findByPersonalCode(CO_PARENT)).willReturn(Optional.of(coParent));
    given(amlService.addSanctionAndPepCheckIfMissing(coParent, new Country(null)))
        .willThrow(new RuntimeException("screening unavailable"));

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(CO_PARENT, CHILD, "Mari", "Maasikas");
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
        .willReturn(List.of(CO_PARENT, OTHER_CO_PARENT));
    given(parentChildLinkRegistrationService.registerPending(CO_PARENT, CHILD, "Mari", "Maasikas"))
        .willThrow(new RuntimeException("constraint violation"));
    given(userService.findByPersonalCode(OTHER_CO_PARENT)).willReturn(Optional.empty());

    listener.onChildOnboarded(new ChildOnboardedEvent(PARENT, CHILD, "Mari", "Maasikas"));

    verify(parentChildLinkRegistrationService)
        .registerPending(OTHER_CO_PARENT, CHILD, "Mari", "Maasikas");
    verify(userService, never()).findByPersonalCode(CO_PARENT);
  }
}
