package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildOnboardingServiceTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";

  @Mock private CustodyVerificationService custodyVerificationService;
  @Mock private ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  @Mock private SavingsFundOnboardingService savingsFundOnboardingService;
  @Mock private AmlService amlService;

  @InjectMocks private ChildOnboardingService service;

  private final PopulationRegisterPerson child =
      new PopulationRegisterPerson(
          CHILD, "MARI", "MAASIKAS", LocalDate.of(2015, 6, 15), ALIVE, "EESTI VABARIIK");

  @Test
  void verifiedCustody_createsLinkSeedsOnboardingRecordsCheckReturnsChild() {
    var evidence = Map.<String, Object>of("custodyType", "PROPERTY");
    given(custodyVerificationService.verify(PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, evidence));

    ChildOnboardingResult result = service.onboardChild(PARENT, CHILD);

    assertThat(result.verified()).isTrue();
    assertThat(result.firstName()).isEqualTo("MARI");
    assertThat(result.lastName()).isEqualTo("MAASIKAS");
    assertThat(result.dateOfBirth()).isEqualTo(LocalDate.of(2015, 6, 15));
    verify(parentChildLinkRegistrationService)
        .register(PARENT, CHILD, "MARI", "MAASIKAS", LEGAL_REPRESENTATIVE);
    verify(savingsFundOnboardingService).seedPersonOnboardingIfAbsent(CHILD);
    verify(amlService).addCustodyRightCheck(CHILD, true, evidence);
  }

  @Test
  void unverifiedCustody_recordsFailedCheckReturnsUnderReviewWithoutCreatingLink() {
    given(custodyVerificationService.verify(PARENT, CHILD))
        .willReturn(CustodyVerification.notVerified(NO_CUSTODY));

    ChildOnboardingResult result = service.onboardChild(PARENT, CHILD);

    assertThat(result.verified()).isFalse();
    assertThat(result.firstName()).isNull();
    verify(amlService).addCustodyRightCheck(CHILD, false, Map.of("outcome", "NO_CUSTODY"));
    verify(parentChildLinkRegistrationService, never()).register(any(), any(), any(), any(), any());
    verify(savingsFundOnboardingService, never()).seedPersonOnboardingIfAbsent(any());
  }
}
