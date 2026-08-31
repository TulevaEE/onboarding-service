package ee.tuleva.onboarding.notification.email.firstpayment;

import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_PAYMENT_ARRIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThirdPillarPaymentArrivedEmailServiceTest {

  private static final String PERSONAL_CODE = TestPersonalCodes.withValidChecksum("3860101000");
  private static final LocalDate PAYMENT_DATE = LocalDate.parse("2026-08-16");
  private static final String SAVINGS_FUND_FEE = "0.30";

  private final ThirdPillarPaymentArrivedClaims claims =
      mock(ThirdPillarPaymentArrivedClaims.class);
  private final EmailService emailService = mock(EmailService.class);
  private final EmailPersistenceService emailPersistenceService =
      mock(EmailPersistenceService.class);
  private final SavingsFundFeeRates savingsFundFees = mock(SavingsFundFeeRates.class);

  private final ThirdPillarPaymentArrivedEmailService service =
      new ThirdPillarPaymentArrivedEmailService(
          claims, emailService, emailPersistenceService, savingsFundFees);

  @BeforeEach
  void setUp() {
    given(claims.claim(PERSONAL_CODE)).willReturn(true);
    given(savingsFundFees.ongoingChargesPercent(any(Locale.class))).willReturn(SAVINGS_FUND_FEE);
    given(emailService.newMandrillMessage(any(), any(), any(), any()))
        .willReturn(new MandrillMessage());
    given(emailService.send(any(), any(), any())).willReturn(Optional.empty());
  }

  private FirstThirdPillarPayment payment(
      boolean hasTulevaUser, boolean suggestSecondPillar, boolean suggestPaymentRate) {
    return new FirstThirdPillarPayment(
        PERSONAL_CODE,
        "First",
        "Last",
        "first.last@example.com",
        "EST",
        new BigDecimal("100.00"),
        PAYMENT_DATE,
        hasTulevaUser,
        suggestSecondPillar,
        suggestPaymentRate,
        true,
        false,
        false);
  }

  private Map<String, Object> expectedMergeVars(FirstThirdPillarPayment payment) {
    return Map.ofEntries(
        Map.entry("fname", "First"),
        Map.entry("lname", "Last"),
        Map.entry("paymentDate", "16.08.2026"),
        Map.entry("hasTulevaUser", payment.hasTulevaUser()),
        Map.entry("leftSecondPillar", payment.leftSecondPillar()),
        Map.entry("suggestSecondPillar", payment.suggestSecondPillar()),
        Map.entry("suggestPaymentRate", payment.suggestPaymentRate()),
        Map.entry("suggestMembership", payment.suggestMembership()),
        Map.entry("suggestSavingsFund", payment.suggestSavingsFund()),
        Map.entry("suggestThirdPillarRecurringPayment", true),
        Map.entry("suggestThirdPillarRaise", false),
        Map.entry("thirdPillarActive", true),
        Map.entry("suggestSavingsFundRecurringPayment", false),
        Map.entry("savingsFundFee", SAVINGS_FUND_FEE));
  }

  @Test
  void returnsTrueAndPersistsTheSentEmailWhenMandrillAccepts() {
    var payment = payment(true, true, true);
    var response = mock(MandrillMessageStatus.class);
    given(response.getId()).willReturn("mandrill-id");
    given(response.getStatus()).willReturn("sent");
    given(emailService.send(any(), any(), any())).willReturn(Optional.of(response));

    boolean result = service.send(payment);

    assertThat(result).isTrue();
    verify(emailPersistenceService)
        .save(payment, "mandrill-id", THIRD_PILLAR_PAYMENT_ARRIVED, "sent");
  }

  @Test
  void returnsFalseAndDoesNotPersistWhenMandrillFailsToSend() {
    var payment = payment(true, true, true);
    given(emailService.send(any(), any(), any())).willReturn(Optional.empty());

    boolean result = service.send(payment);

    assertThat(result).isFalse();
    verifyNoInteractions(emailPersistenceService);
  }

  @Test
  void tagsIncludeALoginNudgeWhenThePersonHasNoTulevaAccount() {
    var payment = payment(false, true, true);

    service.send(payment);

    verify(emailService)
        .newMandrillMessage(
            "first.last@example.com",
            "third_pillar_payment_arrived_et",
            expectedMergeVars(payment),
            List.of("third_pillar_payment_arrived", "nudge_log_in"));
  }

  @Test
  void tagsIncludeASecondPillarNudgeWhenSuggested() {
    var payment = payment(true, true, true);

    service.send(payment);

    verify(emailService)
        .newMandrillMessage(
            "first.last@example.com",
            "third_pillar_payment_arrived_et",
            expectedMergeVars(payment),
            List.of("third_pillar_payment_arrived", "nudge_second_pillar"));
  }

  @Test
  void tagsIncludeAPaymentRateNudgeWhenSecondPillarIsNotSuggestedButPaymentRateIs() {
    var payment = payment(true, false, true);

    service.send(payment);

    verify(emailService)
        .newMandrillMessage(
            "first.last@example.com",
            "third_pillar_payment_arrived_et",
            expectedMergeVars(payment),
            List.of("third_pillar_payment_arrived", "nudge_payment_rate"));
  }

  @Test
  void tagsFallBackToTheRecurringNudgeWhenNoOtherNudgeApplies() {
    var payment = payment(true, false, false);

    service.send(payment);

    verify(emailService)
        .newMandrillMessage(
            "first.last@example.com",
            "third_pillar_payment_arrived_et",
            expectedMergeVars(payment),
            List.of("third_pillar_payment_arrived", "nudge_third_pillar_recurring"));
  }
}
