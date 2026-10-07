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
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  private final AccountOpenedEmailClaims claims = mock(AccountOpenedEmailClaims.class);
  private final OpenedAccountRepository openedAccounts = mock(OpenedAccountRepository.class);

  private final ChildAccountOpenedEmailSender sender =
      new ChildAccountOpenedEmailSender(
          emailService,
          emailPersistenceService,
          userService,
          parentChildLinkService,
          savingsFundFees,
          claims,
          openedAccounts);

  private final OpenedAccount unpaidChild =
      new OpenedAccount(CHILD_CODE, "KATI", "TAMM", null, false, true, false);
  private final OpenedAccount paidChild =
      new OpenedAccount(CHILD_CODE, "KATI", "TAMM", null, false, true, true);
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
    given(claims.claim(CHILD_CODE)).willReturn(true);
  }

  @Test
  void releasesTheClaimWhenMandrillDoesNotTakeTheEmailSoTheNextRunRetries() {
    startedBy(PARENT_CODE, PARENT_LINK);
    var message = new MandrillMessage();
    given(emailService.newMandrillMessage(eq("mari@example.com"), eq(TEMPLATE), any(), any()))
        .willReturn(message);
    given(emailService.send(parent, message, TEMPLATE)).willReturn(Optional.empty());

    sender.send(unpaidChild);

    verify(claims).release(CHILD_CODE);
  }

  @Test
  void writesInEnglishToAParentWhoPrefersEnglish() {
    startedBy(PARENT_CODE, PARENT_LINK);
    given(openedAccounts.prefersEnglish(PARENT_CODE)).willReturn(true);
    given(savingsFundFees.ongoingChargesPercent(Locale.ENGLISH)).willReturn("0.28");

    sender.send(unpaidChild);

    var englishMergeVars = new java.util.HashMap<>(mergeVars(false, true));
    englishMergeVars.put("savingsFundFee", "0.28");
    verify(emailService)
        .newMandrillMessage(
            "mari@example.com",
            "savings_fund_onboarding_completed_child_en",
            englishMergeVars,
            TAGS);
  }

  @Test
  void sendsNothingWhenTheEmailWasAlreadyClaimed() {
    startedBy(PARENT_CODE, PARENT_LINK);
    given(claims.claim(CHILD_CODE)).willReturn(false);

    sender.send(unpaidChild);

    verify(emailService, never()).send(any(), any(), any());
  }

  @Test
  void keepsTheClaimWhenRecordingTheSentEmailFailsSoItIsNotSentAgain() {
    startedBy(PARENT_CODE, PARENT_LINK);
    var message = new MandrillMessage();
    var response = mandrillResponse("message-id", "sent");
    given(emailService.newMandrillMessage(eq("mari@example.com"), eq(TEMPLATE), any(), any()))
        .willReturn(message);
    given(emailService.send(parent, message, TEMPLATE)).willReturn(Optional.of(response));
    given(emailPersistenceService.save(any(), any(), any(), any()))
        .willThrow(new RuntimeException("database is down"));

    assertThatCode(() -> sender.send(unpaidChild)).doesNotThrowAnyException();
    verify(claims).claim(CHILD_CODE);
  }

  @Test
  void doesNotCountTheParentsOwnPendingLinkAsAnotherParent() {
    startedBy(PARENT_CODE, PARENT_LINK);
    given(parentChildLinkService.hasPendingRepresentativeOtherThan(CHILD_CODE, PARENT_CODE))
        .willReturn(false);

    sender.send(paidChild);

    verify(emailService)
        .newMandrillMessage("mari@example.com", TEMPLATE, mergeVars(false, false), TAGS);
  }

  @Test
  void emailsTheParentWhoStartedTheAccountWithALinkToTheChildsPaymentPage() {
    startedBy(PARENT_CODE, PARENT_LINK);

    sender.send(unpaidChild);

    verify(emailService)
        .newMandrillMessage("mari@example.com", TEMPLATE, mergeVars(false, true), TAGS);
  }

  @Test
  void leavesOutTheFirstPaymentCallWhenThePaymentHasAlreadyArrived() {
    startedBy(PARENT_CODE, PARENT_LINK);

    sender.send(paidChild);

    verify(emailService)
        .newMandrillMessage("mari@example.com", TEMPLATE, mergeVars(false, false), TAGS);
  }

  @Test
  void tellsTheParentHowToBringInTheOtherParentWhenOneIsWaitingToConfirm() {
    startedBy(PARENT_CODE, PARENT_LINK);
    given(parentChildLinkService.hasPendingRepresentativeOtherThan(CHILD_CODE, PARENT_CODE))
        .willReturn(true);

    sender.send(paidChild);

    verify(emailService)
        .newMandrillMessage("mari@example.com", TEMPLATE, mergeVars(true, false), TAGS);
  }

  @Test
  void recordsTheEmailAgainstTheChildSoEachChildsEmailCanBeTold() {
    startedBy(PARENT_CODE, PARENT_LINK);
    var message = new MandrillMessage();
    var response = mandrillResponse("message-id", "sent");
    given(emailService.newMandrillMessage(eq("mari@example.com"), eq(TEMPLATE), any(), any()))
        .willReturn(message);
    given(emailService.send(parent, message, TEMPLATE)).willReturn(Optional.of(response));

    sender.send(unpaidChild);

    verify(emailPersistenceService)
        .save(unpaidChild, "message-id", SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD, "sent");
  }

  @Test
  void sendsNothingWhenNoParentRepresentsTheChild() {
    given(parentChildLinkService.findFirstActiveRepresentative(CHILD_CODE))
        .willReturn(Optional.empty());

    sender.send(unpaidChild);

    verify(emailService, never()).newMandrillMessage(any(), any(), any(), any());
  }

  @Test
  void skipsAParentWithoutAnEmailAddress() {
    startedBy(PARENT_CODE, PARENT_LINK);
    given(userService.findByPersonalCode(PARENT_CODE))
        .willReturn(
            Optional.of(sampleUserNonMember().personalCode(PARENT_CODE).email(" ").build()));

    sender.send(unpaidChild);

    verify(emailService, never()).newMandrillMessage(any(), any(), any(), any());
  }

  private void startedBy(String parentCode, UUID accountId) {
    given(parentChildLinkService.findFirstActiveRepresentative(CHILD_CODE))
        .willReturn(Optional.of(parentCode));
    given(parentChildLinkService.findRepresentation(parentCode, CHILD_CODE, Set.of(ACTIVE)))
        .willReturn(Optional.of(accountId));
  }

  private Map<String, Object> mergeVars(boolean hasCoParent, boolean awaitingFirstPayment) {
    return Map.of(
        "fname", "Mari",
        "lname", "Tamm",
        "recipientName", "Kati Tamm",
        "recipientAccountId", PARENT_LINK.toString(),
        "savingsFundFee", "0,28",
        "hasCoParent", hasCoParent,
        "awaitingFirstPayment", awaitingFirstPayment);
  }

  private MandrillMessageStatus mandrillResponse(String id, String status) {
    var response = mock(MandrillMessageStatus.class);
    given(response.getId()).willReturn(id);
    given(response.getStatus()).willReturn(status);
    return response;
  }
}
