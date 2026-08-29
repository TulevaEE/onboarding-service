package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_COMPANY_ONBOARDED;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.kyb.CompanyDto;
import ee.tuleva.onboarding.kyb.LegalForm;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.kyb.SelfCertification;
import ee.tuleva.onboarding.kyb.survey.KybSurveyInputs;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegalEntityOnboardedEmailSenderTest {

  private final EmailService emailService = mock(EmailService.class);
  private final EmailPersistenceService emailPersistenceService =
      mock(EmailPersistenceService.class);
  private final UserService userService = mock(UserService.class);
  private final LatestKybSurveyInputs latestKybSurveyInputs = mock(LatestKybSurveyInputs.class);

  private final LegalEntityOnboardedEmailSender sender =
      new LegalEntityOnboardedEmailSender(
          emailService, emailPersistenceService, userService, latestKybSurveyInputs);

  private final CompanyDto company =
      new CompanyDto(new RegistryCode("12345678"), "Mesila OÜ", "62011", LegalForm.OÜ);

  private final User applicant =
      sampleUserNonMember().personalCode("38888888888").email("mari@example.com").build();

  @BeforeEach
  void setUp() {
    given(latestKybSurveyInputs.findByRegistryCode("12345678"))
        .willReturn(
            new KybSurveyInputs(
                new PersonalCode("38888888888"), new SelfCertification(true, true, true)));
  }

  private LegalEntityOnboardedEvent event() {
    return new LegalEntityOnboardedEvent(this, company);
  }

  @Test
  void sendsTheCompanyOnboardedEmailToTheApplicant() {
    given(userService.findByPersonalCode("38888888888")).willReturn(Optional.of(applicant));
    var message = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                eq("mari@example.com"),
                eq("savings_fund_company_onboarded_et"),
                any(),
                any(),
                any()))
        .willReturn(message);
    given(emailService.send(eq(applicant), eq(message), eq("savings_fund_company_onboarded_et")))
        .willReturn(Optional.empty());

    sender.onLegalEntityOnboarded(event());

    verify(emailService).send(applicant, message, "savings_fund_company_onboarded_et");
  }

  // The template greets the applicant and names their company, so both have to reach
  // Mandrill as merge variables.
  @Test
  void namesTheCompanyInTheMergeVariables() {
    given(userService.findByPersonalCode("38888888888")).willReturn(Optional.of(applicant));
    given(emailService.newMandrillMessage(any(), any(), any(), any(), any()))
        .willReturn(new MandrillMessage());

    sender.onLegalEntityOnboarded(event());

    Map<String, Object> expectedMergeVars =
        Map.of(
            "fname",
            applicant.getFirstName(),
            "lname",
            applicant.getLastName(),
            "recipientName",
            "Mesila OÜ");
    verify(emailService)
        .newMandrillMessage(
            "mari@example.com",
            "savings_fund_company_onboarded_et",
            expectedMergeVars,
            List.of("savings_fund"),
            null);
  }

  @Test
  void doesNotSendWhenTheApplicantCannotBeResolved() {
    given(userService.findByPersonalCode("38888888888")).willReturn(Optional.empty());

    sender.onLegalEntityOnboarded(event());

    verifyNoInteractions(emailService, emailPersistenceService);
  }

  @Test
  void doesNotSendWhenTheApplicantHasNoEmail() {
    given(userService.findByPersonalCode("38888888888"))
        .willReturn(
            Optional.of(sampleUserNonMember().personalCode("38888888888").email(null).build()));

    sender.onLegalEntityOnboarded(event());

    verifyNoInteractions(emailService, emailPersistenceService);
  }

  // A company with no stored survey must not take the whole re-screening down.
  @Test
  void survivesAMissingSurvey() {
    given(latestKybSurveyInputs.findByRegistryCode("12345678"))
        .willThrow(new IllegalStateException("No KYB survey found"));

    sender.onLegalEntityOnboarded(event());

    verifyNoInteractions(emailService, emailPersistenceService);
  }

  // A failing send must not abort the after-commit synchronizations of the other companies
  // onboarded in the same batch.
  @Test
  void survivesAFailingSend() {
    given(userService.findByPersonalCode("38888888888")).willReturn(Optional.of(applicant));
    given(emailService.newMandrillMessage(any(), any(), any(), any(), any()))
        .willReturn(new MandrillMessage());
    given(emailService.send(any(), any(), any()))
        .willThrow(new IllegalStateException("Mandrill is down"));

    assertThatCode(() -> sender.onLegalEntityOnboarded(event())).doesNotThrowAnyException();

    verifyNoInteractions(emailPersistenceService);
  }

  // Merge variables are built with Map.of, which rejects a null name.
  @Test
  void survivesAnApplicantWithoutAName() {
    given(userService.findByPersonalCode("38888888888"))
        .willReturn(
            Optional.of(
                sampleUserNonMember()
                    .personalCode("38888888888")
                    .email("mari@example.com")
                    .firstName(null)
                    .lastName(null)
                    .build()));

    assertThatCode(() -> sender.onLegalEntityOnboarded(event())).doesNotThrowAnyException();

    verifyNoInteractions(emailPersistenceService);
  }

  @Test
  void recordsTheSentEmail() {
    given(userService.findByPersonalCode("38888888888")).willReturn(Optional.of(applicant));
    var message = new MandrillMessage();
    given(emailService.newMandrillMessage(any(), any(), any(), any(), any())).willReturn(message);
    var status = mock(MandrillMessageStatus.class);
    given(status.getId()).willReturn("msg_1");
    given(status.getStatus()).willReturn("sent");
    given(emailService.send(any(), any(), any())).willReturn(Optional.of(status));

    sender.onLegalEntityOnboarded(event());

    verify(emailPersistenceService)
        .save(applicant, "msg_1", SAVINGS_FUND_COMPANY_ONBOARDED, "sent");
  }
}
