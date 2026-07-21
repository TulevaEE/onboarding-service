package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.event.TrackableEventType.MINOR_CUSTODY_VERIFICATION;
import static ee.tuleva.onboarding.party.ChildOnboardingService.CUSTODY_MAX_AGE;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.populationregister.CustodyRight;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ChildOnboardingServiceTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final String ADULT = "39912310015";

  @Mock private CustodyVerificationService custodyVerificationService;
  @Mock private ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  @Mock private SavingsFundOnboardingService savingsFundOnboardingService;
  @Mock private AmlService amlService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);

  private ChildOnboardingService service;

  @BeforeEach
  void setUp() {
    service =
        new ChildOnboardingService(
            custodyVerificationService,
            parentChildLinkRegistrationService,
            savingsFundOnboardingService,
            amlService,
            applicationEventPublisher,
            clock);
  }

  private final AuthenticatedPerson parent = sampleAuthenticatedPersonNonMember().build();

  private final PopulationRegisterPerson child =
      new PopulationRegisterPerson(
          CHILD, "MARI", "MAASIKAS", LocalDate.of(2015, 6, 15), ALIVE, "EESTI VABARIIK");

  private static final String CUSTODY_MESSAGE_ID = "11111111-1111-1111-1111-111111111111";

  @Test
  void findEligibleChildren_returnsChildrenWithAssetManagementCustodyIncludingNames() {
    given(
            custodyVerificationService.findChildrenWithAssetManagementCustody(
                PARENT, CUSTODY_MAX_AGE))
        .willReturn(
            List.of(new CustodyRight(CHILD, PROPERTY_CUSTODY, true, true, "Mari", "Maasikas")));

    assertThat(service.findEligibleChildren(parent))
        .containsExactly(new EligibleChild(CHILD, "Mari", "Maasikas", false));
  }

  @Test
  void findEligibleChildren_excludesChildrenWhoAreNoLongerMinors() {
    given(
            custodyVerificationService.findChildrenWithAssetManagementCustody(
                PARENT, CUSTODY_MAX_AGE))
        .willReturn(
            List.of(
                new CustodyRight(CHILD, PROPERTY_CUSTODY, true, true, "Mari", "Maasikas"),
                new CustodyRight(ADULT, PROPERTY_CUSTODY, true, true, "Jüri", "Tamm")));

    assertThat(service.findEligibleChildren(parent))
        .containsExactly(new EligibleChild(CHILD, "Mari", "Maasikas", false));
  }

  @Test
  void findEligibleChildren_marksChildrenWhoHaveAlreadyBeenOnboarded() {
    var secondChild = "61001010000";
    given(
            custodyVerificationService.findChildrenWithAssetManagementCustody(
                PARENT, CUSTODY_MAX_AGE))
        .willReturn(
            List.of(
                new CustodyRight(CHILD, PROPERTY_CUSTODY, true, true, "Mari", "Maasikas"),
                new CustodyRight(secondChild, PROPERTY_CUSTODY, true, true, "Jüri", "Tamm")));
    given(savingsFundOnboardingService.getOnboardingStatus(new PartyId(PERSON, CHILD)))
        .willReturn(PENDING);

    assertThat(service.findEligibleChildren(parent))
        .containsExactly(
            new EligibleChild(CHILD, "Mari", "Maasikas", true),
            new EligibleChild(secondChild, "Jüri", "Tamm", false));
  }

  @Test
  void verifiedCustody_createsLinkSeedsOnboardingRecordsCheckReturnsChild() {
    var evidence =
        Map.<String, Object>of(
            "outcome",
            "OK",
            "childPersonalCode",
            CHILD,
            "custodyType",
            "PROPERTY_CUSTODY",
            "custodyResponseMessageId",
            CUSTODY_MESSAGE_ID);
    given(custodyVerificationService.verify(PARENT, CHILD, CUSTODY_MAX_AGE))
        .willReturn(new CustodyVerification(OK, child, evidence));

    ChildOnboardingResult result = service.onboardChild(parent, CHILD);

    assertThat(result.verified()).isTrue();
    assertThat(result.firstName()).isEqualTo("MARI");
    assertThat(result.lastName()).isEqualTo("MAASIKAS");
    assertThat(result.dateOfBirth()).isEqualTo(LocalDate.of(2015, 6, 15));
    verify(parentChildLinkRegistrationService).register(PARENT, CHILD, "MARI", "MAASIKAS");
    verify(savingsFundOnboardingService).seedPersonOnboardingIfAbsent(CHILD);
    verify(amlService).addCustodyRightCheck(CHILD, true, evidence);
    verify(applicationEventPublisher)
        .publishEvent(new TrackableEvent(parent, MINOR_CUSTODY_VERIFICATION, evidence));
  }

  @Test
  void verifiedCustody_publishesChildOnboardedEventToCaptureCoParentsAfterCommit() {
    var evidence =
        Map.<String, Object>of(
            "outcome", "OK", "childPersonalCode", CHILD, "custodyType", "PROPERTY_CUSTODY");
    given(custodyVerificationService.verify(PARENT, CHILD, CUSTODY_MAX_AGE))
        .willReturn(new CustodyVerification(OK, child, evidence));

    service.onboardChild(parent, CHILD);

    verify(parentChildLinkRegistrationService).register(PARENT, CHILD, "MARI", "MAASIKAS");
    verify(applicationEventPublisher)
        .publishEvent(new ChildOnboardedEvent(PARENT, CHILD, "MARI", "MAASIKAS"));
  }

  @Test
  void unverifiedCustody_recordsFailedCheckReturnsUnderReviewWithoutCreatingLink() {
    var evidence =
        Map.<String, Object>of(
            "outcome",
            "NO_CUSTODY",
            "childPersonalCode",
            CHILD,
            "custodyResponseMessageId",
            CUSTODY_MESSAGE_ID);
    given(custodyVerificationService.verify(PARENT, CHILD, CUSTODY_MAX_AGE))
        .willReturn(CustodyVerification.notVerified(NO_CUSTODY, evidence));

    ChildOnboardingResult result = service.onboardChild(parent, CHILD);

    assertThat(result.verified()).isFalse();
    assertThat(result.firstName()).isNull();
    verify(amlService).addCustodyRightCheck(CHILD, false, evidence);
    verify(parentChildLinkRegistrationService, never()).register(any(), any(), any(), any());
    verify(savingsFundOnboardingService, never()).seedPersonOnboardingIfAbsent(any());
    verify(applicationEventPublisher)
        .publishEvent(new TrackableEvent(parent, MINOR_CUSTODY_VERIFICATION, evidence));
  }
}
