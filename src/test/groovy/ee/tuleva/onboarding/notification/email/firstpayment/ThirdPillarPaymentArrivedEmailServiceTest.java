package ee.tuleva.onboarding.notification.email.firstpayment;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_PAYMENT_ARRIVED;
import static ee.tuleva.onboarding.notification.email.SecondPillarLetterScheduler.Trigger.PAYMENT_ARRIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.SecondPillarLetterScheduler;
import ee.tuleva.onboarding.nudge.NudgeContext;
import ee.tuleva.onboarding.nudge.NudgeDecision;
import ee.tuleva.onboarding.nudge.NudgeDecisionService;
import ee.tuleva.onboarding.nudge.NudgeKey;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThirdPillarPaymentArrivedEmailServiceTest {

  private static final String PERSONAL_CODE = TestPersonalCodes.withValidChecksum("3860101000");
  private static final LocalDate PAYMENT_DATE = LocalDate.parse("2026-08-16");

  private final ThirdPillarPaymentArrivedClaims claims =
      mock(ThirdPillarPaymentArrivedClaims.class);
  private final EmailService emailService = mock(EmailService.class);
  private final EmailPersistenceService emailPersistenceService =
      mock(EmailPersistenceService.class);
  private final UserService userService = mock(UserService.class);
  private final NudgeDecisionService nudgeDecisionService = mock(NudgeDecisionService.class);
  private final SecondPillarLetterScheduler secondPillarLetter =
      mock(SecondPillarLetterScheduler.class);
  private final User user = sampleUser().personalCode(PERSONAL_CODE).build();

  private final ThirdPillarPaymentArrivedEmailService service =
      new ThirdPillarPaymentArrivedEmailService(
          claims,
          emailService,
          emailPersistenceService,
          userService,
          nudgeDecisionService,
          secondPillarLetter);

  @BeforeEach
  void setUp() {
    given(claims.claim(PERSONAL_CODE)).willReturn(true);
    given(emailService.newMandrillMessage(any(), any(), any(), any()))
        .willReturn(new MandrillMessage());
    given(emailService.send(any(), any(), any())).willReturn(Optional.empty());
    given(userService.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.of(user));
  }

  private static FirstThirdPillarPayment payment(boolean hasTulevaUser) {
    return new FirstThirdPillarPayment(
        PERSONAL_CODE,
        "First",
        "Last",
        "first.last@example.com",
        "EST",
        new BigDecimal("100.00"),
        PAYMENT_DATE,
        hasTulevaUser);
  }

  private static Map<String, Object> baseMergeVars(boolean hasTulevaUser) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("fname", "First");
    vars.put("lname", "Last");
    vars.put("paymentDate", "16.08.2026");
    vars.put("hasTulevaUser", hasTulevaUser);
    return vars;
  }

  @Test
  void returnsTrueAndPersistsTheSentEmailWithTheNudgeWhenMandrillAccepts() {
    given(nudgeDecisionService.decideOffline(user, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willReturn(NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE));
    var response = mock(MandrillMessageStatus.class);
    given(response.getId()).willReturn("mandrill-id");
    given(response.getStatus()).willReturn("sent");
    given(emailService.send(any(), any(), any())).willReturn(Optional.of(response));
    var payment = payment(true);

    boolean result = service.send(payment);

    assertThat(result).isTrue();
    verify(emailPersistenceService)
        .save(payment, "mandrill-id", THIRD_PILLAR_PAYMENT_ARRIVED, "sent", "nudge_payment_rate");
  }

  @Test
  void returnsFalseAndDoesNotPersistWhenMandrillFailsToSend() {
    given(nudgeDecisionService.decideOffline(any(), any()))
        .willReturn(NudgeDecision.of(NudgeKey.NONE));

    boolean result = service.send(payment(true));

    assertThat(result).isFalse();
    verifyNoInteractions(emailPersistenceService);
  }

  @Test
  void doesNotSendWhenTheClaimIsAlreadyTaken() {
    given(claims.claim(PERSONAL_CODE)).willReturn(false);

    assertThat(service.send(payment(true))).isFalse();
    verifyNoInteractions(emailService, nudgeDecisionService);
  }

  @Test
  void rendersTheDecidedNudgeForAnAccountHolder() {
    given(nudgeDecisionService.decideOffline(user, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willReturn(NudgeDecision.of(NudgeKey.SECOND_PILLAR_TRANSFER));
    Map<String, Object> expected = baseMergeVars(true);
    expected.putAll(NudgeDecision.of(NudgeKey.SECOND_PILLAR_TRANSFER).mergeVars(Locale.of("et")));

    service.send(payment(true));

    verify(emailService)
        .newMandrillMessage(
            "first.last@example.com",
            "third_pillar_payment_arrived_et",
            expected,
            List.of("third_pillar_payment_arrived", "nudge_second_pillar"));
  }

  @Test
  void asksThePersonWithoutAnAccountToLogInInstead() {
    service.send(payment(false));

    verify(emailService)
        .newMandrillMessage(
            "first.last@example.com",
            "third_pillar_payment_arrived_et",
            baseMergeVars(false),
            List.of("third_pillar_payment_arrived", "nudge_log_in"));
    verifyNoInteractions(nudgeDecisionService);
  }

  private void acceptedByMandrill() {
    var response = mock(MandrillMessageStatus.class);
    given(response.getId()).willReturn("mandrill-id");
    given(response.getStatus()).willReturn("sent");
    given(emailService.send(any(), any(), any())).willReturn(Optional.of(response));
  }

  @Test
  void handsARegistryOnlyPayerWhoseSecondPillarIsElsewhereToTheLetterScheduler() {
    acceptedByMandrill();
    var payment = payment(false);
    given(
            nudgeDecisionService.decideForRegistryOnly(
                payment, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willReturn(NudgeDecision.secondPillarTransfer(null));

    assertThat(service.send(payment)).isTrue();

    verify(secondPillarLetter).schedule(payment, Locale.of("et"), PAYMENT_ARRIVED);
  }

  @Test
  void doesNotScheduleTheSecondPillarLetterForARegistryOnlyPayerWhoHasNoSecondPillar() {
    acceptedByMandrill();
    var payment = payment(false);
    given(
            nudgeDecisionService.decideForRegistryOnly(
                payment, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willReturn(NudgeDecision.of(NudgeKey.SECOND_PILLAR_START));

    service.send(payment);

    verifyNoInteractions(secondPillarLetter);
  }

  @Test
  void leavesTheSecondPillarLetterToTheMandateFlowForAnAccountHolder() {
    acceptedByMandrill();
    given(nudgeDecisionService.decideOffline(user, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willReturn(NudgeDecision.of(NudgeKey.NONE));

    service.send(payment(true));

    verify(nudgeDecisionService, never()).decideForRegistryOnly(any(), any());
    verifyNoInteractions(secondPillarLetter);
  }

  @Test
  void stillReportsTheArrivedEmailAsSentWhenTheLetterDecisionFails() {
    acceptedByMandrill();
    var payment = payment(false);
    given(
            nudgeDecisionService.decideForRegistryOnly(
                payment, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willThrow(new IllegalStateException("registry unavailable"));

    assertThat(service.send(payment)).isTrue();
    verify(emailPersistenceService)
        .save(payment, "mandrill-id", THIRD_PILLAR_PAYMENT_ARRIVED, "sent", "nudge_log_in");
  }

  @Test
  void sendsWithoutANudgeWhenTheAccountCannotBeLoaded() {
    given(userService.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.empty());

    service.send(payment(true));

    verify(emailService)
        .newMandrillMessage(
            "first.last@example.com",
            "third_pillar_payment_arrived_et",
            baseMergeVars(true),
            List.of("third_pillar_payment_arrived", "nudge_none"));
  }

  @Test
  void sendsWithoutANudgeWhenTheDecisionFailsSoTheClaimIsNotWasted() {
    given(nudgeDecisionService.decideOffline(user, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willThrow(new IllegalStateException("EPIS down"));

    service.send(payment(true));

    verify(emailService)
        .newMandrillMessage(
            "first.last@example.com",
            "third_pillar_payment_arrived_et",
            baseMergeVars(true),
            List.of("third_pillar_payment_arrived", "nudge_none"));
  }
}
