package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_COMPLETED_PERSON;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdultAccountOpenedEmailSenderTest {

  private static final String TEMPLATE = "savings_fund_onboarding_completed_person_et";
  private static final List<String> TAGS = List.of("savings_fund", "onboarding_completed");
  private static final String CODE = "38812121215";

  private final EmailService emailService = mock(EmailService.class);
  private final EmailPersistenceService emailPersistenceService =
      mock(EmailPersistenceService.class);
  private final SavingsFundFees savingsFundFees = mock(SavingsFundFees.class);
  private final AccountOpenedEmailClaims claims = mock(AccountOpenedEmailClaims.class);

  private final AdultAccountOpenedEmailSender sender =
      new AdultAccountOpenedEmailSender(
          emailService, emailPersistenceService, savingsFundFees, claims);

  private final OpenedAccount unpaid = account("mari@example.com", false, false, false);
  private final OpenedAccount paid = account("mari@example.com", false, false, true);

  @BeforeEach
  void setUp() {
    given(savingsFundFees.ongoingChargesPercent(Locale.of("et"))).willReturn("0,28");
    given(emailService.newMandrillMessage(any(), any(), any(), any()))
        .willReturn(new MandrillMessage());
    given(claims.claim(CODE)).willReturn(true);
  }

  @Test
  void invitesTheFirstPaymentWhenNoneHasArrived() {
    sender.send(unpaid);

    verify(emailService).newMandrillMessage("mari@example.com", TEMPLATE, mergeVars(true), TAGS);
  }

  @Test
  void leavesOutTheFirstPaymentCallWhenThePaymentHasAlreadyArrived() {
    sender.send(paid);

    verify(emailService).newMandrillMessage("mari@example.com", TEMPLATE, mergeVars(false), TAGS);
  }

  @Test
  void recordsTheEmailAgainstTheAccountHolder() {
    var message = new MandrillMessage();
    var response = mandrillResponse("message-id", "sent");
    given(emailService.newMandrillMessage(eq("mari@example.com"), eq(TEMPLATE), any(), any()))
        .willReturn(message);
    given(emailService.send(unpaid, message, TEMPLATE)).willReturn(Optional.of(response));

    sender.send(unpaid);

    verify(emailPersistenceService)
        .save(unpaid, "message-id", SAVINGS_FUND_ONBOARDING_COMPLETED_PERSON, "sent");
  }

  @Test
  void sendsNothingWhenTheEmailWasAlreadyClaimed() {
    given(claims.claim(CODE)).willReturn(false);

    sender.send(unpaid);

    verify(emailService, never()).send(any(), any(), any());
  }

  @Test
  void keepsTheClaimWhenRecordingTheSentEmailFailsSoItIsNotSentAgain() {
    var message = new MandrillMessage();
    var response = mandrillResponse("message-id", "sent");
    given(emailService.newMandrillMessage(eq("mari@example.com"), eq(TEMPLATE), any(), any()))
        .willReturn(message);
    given(emailService.send(unpaid, message, TEMPLATE)).willReturn(Optional.of(response));
    given(emailPersistenceService.save(any(), any(), any(), any()))
        .willThrow(new RuntimeException("database is down"));

    assertThatCode(() -> sender.send(unpaid)).doesNotThrowAnyException();
    verify(claims).claim(CODE);
  }

  @Test
  void neitherClaimsNorSendsWithoutAnEmailAddress() {
    sender.send(account(null, false, false, false));

    verifyNoInteractions(claims, emailService);
  }

  private static OpenedAccount account(
      String email, boolean prefersEnglish, boolean represented, boolean paid) {
    return new OpenedAccount(CODE, "MARI", "TAMM", email, prefersEnglish, represented, paid);
  }

  private Map<String, Object> mergeVars(boolean awaitingFirstPayment) {
    return Map.of(
        "fname", "Mari",
        "lname", "Tamm",
        "savingsFundFee", "0,28",
        "awaitingFirstPayment", awaitingFirstPayment);
  }

  private MandrillMessageStatus mandrillResponse(String id, String status) {
    var response = mock(MandrillMessageStatus.class);
    given(response.getId()).willReturn(id);
    given(response.getStatus()).willReturn(status);
    return response;
  }
}
