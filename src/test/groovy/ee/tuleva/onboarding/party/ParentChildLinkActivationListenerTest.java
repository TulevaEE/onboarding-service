package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.MEDIUM;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.kyc.KycSurveyPurpose;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ParentChildLinkActivationListenerTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";

  @Mock private ParentChildLinkRegistrationService parentChildLinkRegistrationService;

  @InjectMocks private ParentChildLinkActivationListener listener;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void activatesActingParentsLinkWhenChildKycCompletes() {
    authenticateAs(PARENT, new Role(RoleType.PERSON, CHILD, "Mari Maasikas"));

    listener.onKycCheckPerformed(event(CHILD, LOW, PERSONAL_ONBOARDING));

    verify(parentChildLinkRegistrationService).activate(PARENT, CHILD);
  }

  @Test
  void activatesForNoneRiskToo() {
    authenticateAs(PARENT, new Role(RoleType.PERSON, CHILD, "Mari Maasikas"));

    listener.onKycCheckPerformed(event(CHILD, NONE, PERSONAL_ONBOARDING));

    verify(parentChildLinkRegistrationService).activate(PARENT, CHILD);
  }

  @Test
  void doesNotActivateWhenKycDoesNotComplete() {
    authenticateAs(PARENT, new Role(RoleType.PERSON, CHILD, "Mari Maasikas"));

    listener.onKycCheckPerformed(event(CHILD, MEDIUM, PERSONAL_ONBOARDING));
    listener.onKycCheckPerformed(event(CHILD, HIGH, PERSONAL_ONBOARDING));

    verifyNoInteractions(parentChildLinkRegistrationService);
  }

  @Test
  void doesNotActivateForNonOnboardingPurpose() {
    authenticateAs(PARENT, new Role(RoleType.PERSON, CHILD, "Mari Maasikas"));

    listener.onKycCheckPerformed(event(CHILD, LOW, IDENTITY_ONLY));

    verifyNoInteractions(parentChildLinkRegistrationService);
  }

  @Test
  void doesNotActivateForSelfOnboarding() {
    // Acting as self: personal code equals the KYC subject, so there is no child to activate.
    authenticateAs(PARENT, new Role(RoleType.PERSON, PARENT, "Parent Person"));

    listener.onKycCheckPerformed(event(PARENT, LOW, PERSONAL_ONBOARDING));

    verify(parentChildLinkRegistrationService, never())
        .activate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void doesNotActivateWithoutAnAuthenticatedActingParent() {
    SecurityContextHolder.clearContext();

    listener.onKycCheckPerformed(event(CHILD, LOW, PERSONAL_ONBOARDING));

    verifyNoInteractions(parentChildLinkRegistrationService);
  }

  private static KycCheckPerformedEvent event(
      String subjectPersonalCode, RiskLevel riskLevel, KycSurveyPurpose purpose) {
    return new KycCheckPerformedEvent(
        new Object(), subjectPersonalCode, new KycCheck(riskLevel, Map.of()), purpose);
  }

  private static void authenticateAs(String personalCode, Role role) {
    var principal =
        AuthenticatedPerson.builder()
            .personalCode(personalCode)
            .firstName("Acting")
            .lastName("Parent")
            .userId(1L)
            .role(role)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }
}
