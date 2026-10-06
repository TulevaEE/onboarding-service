package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class ChildAccountOpenedEmailSenderTest {

  private static final String TEMPLATE = "savings_fund_onboarding_completed_child_et";
  private static final List<String> TAGS = List.of("savings_fund", "onboarding_completed");
  private static final String CHILD_CODE = "61506150006";
  private static final String PARENT_CODE = "38812121215";
  private static final UUID PARENT_LINK = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private final EmailService emailService = mock(EmailService.class);
  private final EmailPersistenceService emailPersistenceService =
      mock(EmailPersistenceService.class);
  private final UserService userService = mock(UserService.class);
  private final ParentChildLinkService parentChildLinkService = mock(ParentChildLinkService.class);
  private final SavingsFundFees savingsFundFees = mock(SavingsFundFees.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC);

  private final ChildAccountOpenedEmailSender sender =
      new ChildAccountOpenedEmailSender(
          emailService,
          emailPersistenceService,
          userService,
          parentChildLinkService,
          savingsFundFees,
          transactionManager,
          clock);

  private final User child =
      sampleUserNonMember().personalCode(CHILD_CODE).firstName("KATI").lastName("TAMM").build();
  private final User parent =
      sampleUserNonMember()
          .personalCode(PARENT_CODE)
          .firstName("Mari")
          .lastName("Tamm")
          .email("mari@example.com")
          .build();

  @BeforeEach
  void setUp() {
    given(savingsFundFees.ongoingChargesPercent(Locale.of("et"))).willReturn("0,28");
    given(userService.findByPersonalCode(PARENT_CODE)).willReturn(Optional.of(parent));
    given(emailService.newMandrillMessage(any(), any(), any(), any()))
        .willReturn(new MandrillMessage());
  }

  @Test
  void emailsTheParentWhoStartedTheAccountWithALinkToTheChildsPaymentPage() {
    startedBy(PARENT_CODE, PARENT_LINK);

    sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(child));

    verify(emailService)
        .newMandrillMessage("mari@example.com", TEMPLATE, mergeVars(PARENT_LINK, false), TAGS);
    verify(emailService, times(1)).newMandrillMessage(any(), any(), any(), any());
  }

  @Test
  void tellsTheParentHowToBringInTheOtherParentWhenOneIsWaitingToConfirm() {
    startedBy(PARENT_CODE, PARENT_LINK);
    given(parentChildLinkService.hasPendingRepresentative(CHILD_CODE)).willReturn(true);

    sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(child));

    verify(emailService)
        .newMandrillMessage("mari@example.com", TEMPLATE, mergeVars(PARENT_LINK, true), TAGS);
  }

  @Test
  void recordsTheEmailAgainstTheChildSoEachChildsEmailCanBeTold() {
    startedBy(PARENT_CODE, PARENT_LINK);
    var message = new MandrillMessage();
    var response = mandrillResponse("message-id", "sent");
    given(emailService.newMandrillMessage(eq("mari@example.com"), eq(TEMPLATE), any(), any()))
        .willReturn(message);
    given(emailService.send(parent, message, TEMPLATE)).willReturn(Optional.of(response));

    sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(child));

    verify(emailPersistenceService)
        .save(child, "message-id", SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD, "sent");
  }

  @Test
  void aFailureToRecordTheSentEmailIsContained() {
    startedBy(PARENT_CODE, PARENT_LINK);
    var message = new MandrillMessage();
    var response = mandrillResponse("message-id", "sent");
    given(emailService.newMandrillMessage(eq("mari@example.com"), eq(TEMPLATE), any(), any()))
        .willReturn(message);
    given(emailService.send(parent, message, TEMPLATE)).willReturn(Optional.of(response));
    given(emailPersistenceService.save(any(), any(), any(), any()))
        .willThrow(new RuntimeException("database is down"));

    assertThatCode(
            () -> sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(child)))
        .doesNotThrowAnyException();
  }

  @Test
  void sendsNothingWhenTheAccountHolderHasANonEstonianCode() {
    var foreigner = sampleUserNonMember().personalCode("PNOGB-1234567890").build();

    assertThatCode(
            () -> sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(foreigner)))
        .doesNotThrowAnyException();
    verifyNoInteractions(parentChildLinkService, emailService);
  }

  @Test
  void sendsNothingWhenAnAdultsAccountOpens() {
    var adult = sampleUserNonMember().personalCode(PARENT_CODE).build();

    sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(adult));

    verifyNoInteractions(parentChildLinkService, emailService, emailPersistenceService);
  }

  @Test
  void sendsNothingWhenNoParentRepresentsTheChild() {
    given(parentChildLinkService.findFirstActiveRepresentative(CHILD_CODE))
        .willReturn(Optional.empty());

    sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(child));

    verify(emailService, never()).newMandrillMessage(any(), any(), any(), any());
  }

  @Test
  void skipsAParentWithoutAnEmailAddress() {
    startedBy(PARENT_CODE, PARENT_LINK);
    given(userService.findByPersonalCode(PARENT_CODE))
        .willReturn(
            Optional.of(sampleUserNonMember().personalCode(PARENT_CODE).email(" ").build()));

    sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(child));

    verify(emailService, never()).newMandrillMessage(any(), any(), any(), any());
  }

  @Test
  void aFailedSendDoesNotBreakTheAccountOpening() {
    startedBy(PARENT_CODE, PARENT_LINK);
    given(emailService.newMandrillMessage(any(), any(), any(), any()))
        .willThrow(new RuntimeException("Mandrill is down"));

    assertThatCode(
            () -> sender.onOnboardingCompleted(new SavingsFundOnboardingCompletedEvent(child)))
        .doesNotThrowAnyException();
  }

  private void startedBy(String parentCode, UUID accountId) {
    given(parentChildLinkService.findFirstActiveRepresentative(CHILD_CODE))
        .willReturn(Optional.of(parentCode));
    given(parentChildLinkService.findRepresentation(parentCode, CHILD_CODE, Set.of(ACTIVE)))
        .willReturn(Optional.of(accountId));
  }

  private Map<String, Object> mergeVars(UUID accountId, boolean hasCoParent) {
    return Map.of(
        "fname", "Mari",
        "lname", "Tamm",
        "recipientName", "Kati Tamm",
        "recipientAccountId", accountId.toString(),
        "savingsFundFee", "0,28",
        "hasCoParent", hasCoParent);
  }

  private MandrillMessageStatus mandrillResponse(String id, String status) {
    var response = mock(MandrillMessageStatus.class);
    given(response.getId()).willReturn(id);
    given(response.getStatus()).willReturn(status);
    return response;
  }
}
