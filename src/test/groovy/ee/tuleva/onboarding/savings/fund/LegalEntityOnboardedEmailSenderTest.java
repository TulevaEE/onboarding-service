package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_COMPANY_ONBOARDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.kyb.CompanyDto;
import ee.tuleva.onboarding.kyb.LegalForm;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.kyb.SelfCertification;
import ee.tuleva.onboarding.kyb.survey.KybSurveyInputs;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    when(latestKybSurveyInputs.findByRegistryCode("12345678"))
        .thenReturn(
            new KybSurveyInputs(
                new PersonalCode("38888888888"), new SelfCertification(true, true, true)));
  }

  private LegalEntityOnboardedEvent event() {
    return new LegalEntityOnboardedEvent(this, company);
  }

  @Test
  void sendsTheCompanyOnboardedEmailToTheApplicant() {
    when(userService.findByPersonalCode("38888888888")).thenReturn(Optional.of(applicant));
    var message = new MandrillMessage();
    when(emailService.newMandrillMessage(
            eq("mari@example.com"), eq("savings_fund_company_onboarded_et"), any(), any(), any()))
        .thenReturn(message);
    when(emailService.send(eq(applicant), eq(message), eq("savings_fund_company_onboarded_et")))
        .thenReturn(Optional.empty());

    sender.onLegalEntityOnboarded(event());

    verify(emailService).send(applicant, message, "savings_fund_company_onboarded_et");
  }

  // The template greets the applicant and names their company, so both have to reach
  // Mandrill as merge variables.
  @Test
  void namesTheCompanyInTheMergeVariables() {
    when(userService.findByPersonalCode("38888888888")).thenReturn(Optional.of(applicant));
    when(emailService.newMandrillMessage(any(), any(), any(), any(), any()))
        .thenReturn(new MandrillMessage());

    sender.onLegalEntityOnboarded(event());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> mergeVars = ArgumentCaptor.forClass(Map.class);
    verify(emailService).newMandrillMessage(any(), any(), mergeVars.capture(), any(), any());
    assertThat(mergeVars.getValue()).containsEntry("recipientName", "Mesila OÜ");
  }

  @Test
  void doesNotSendWhenTheApplicantCannotBeResolved() {
    when(userService.findByPersonalCode("38888888888")).thenReturn(Optional.empty());

    sender.onLegalEntityOnboarded(event());

    verifyNoInteractions(emailService, emailPersistenceService);
  }

  @Test
  void doesNotSendWhenTheApplicantHasNoEmail() {
    when(userService.findByPersonalCode("38888888888"))
        .thenReturn(
            Optional.of(sampleUserNonMember().personalCode("38888888888").email(null).build()));

    sender.onLegalEntityOnboarded(event());

    verifyNoInteractions(emailService, emailPersistenceService);
  }

  // A company with no stored survey must not take the whole re-screening down.
  @Test
  void survivesAMissingSurvey() {
    when(latestKybSurveyInputs.findByRegistryCode("12345678"))
        .thenThrow(new IllegalStateException("No KYB survey found"));

    sender.onLegalEntityOnboarded(event());

    verifyNoInteractions(emailService, emailPersistenceService);
  }

  @Test
  void recordsTheSentEmail() {
    when(userService.findByPersonalCode("38888888888")).thenReturn(Optional.of(applicant));
    var message = new MandrillMessage();
    when(emailService.newMandrillMessage(any(), any(), any(), any(), any())).thenReturn(message);
    var status = mock(MandrillMessageStatus.class);
    when(status.getId()).thenReturn("msg_1");
    when(status.getStatus()).thenReturn("sent");
    when(emailService.send(any(), any(), any())).thenReturn(Optional.of(status));

    sender.onLegalEntityOnboarded(event());

    verify(emailPersistenceService)
        .save(applicant, "msg_1", SAVINGS_FUND_COMPANY_ONBOARDED, "sent");
  }
}
