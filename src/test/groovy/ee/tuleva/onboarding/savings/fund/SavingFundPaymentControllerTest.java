package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SavingFundPaymentController.class)
@AutoConfigureMockMvc
@WithMockUser
class SavingFundPaymentControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private UserService userService;
  @MockitoBean private SavingFundPaymentRepository savingFundPaymentRepository;
  @MockitoBean private SavingFundPaymentUpsertionService savingFundPaymentUpsertionService;
  @MockitoBean private SavingsFundOnboardingService savingsFundOnboardingService;

  @MockitoBean
  private LegalEntitySavingsFundOnboardingService legalEntitySavingsFundOnboardingService;

  @MockitoBean private LocaleService localeService;
  @MockitoBean private ApplicationEventPublisher applicationEventPublisher;

  @Test
  void cancelSavingsFundPayment_whenActingAsLegalEntity_shouldReturnNoContent() throws Exception {
    UUID paymentId = UUID.randomUUID();
    var person = sampleAuthenticatedPersonLegalEntity().build();

    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    var user = mock(User.class);
    when(userService.findByPersonalCode(person.getPersonalCode())).thenReturn(Optional.of(user));
    when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
    doNothing()
        .when(savingFundPaymentUpsertionService)
        .cancelPayment(eq(PartyId.from(person.getRole())), eq(paymentId));

    mvc.perform(delete("/v1/savings/payments/" + paymentId).with(csrf()).with(authentication(auth)))
        .andExpect(status().isNoContent());
  }

  @Test
  void cancelSavingsFundPayment_shouldReturnNoContent() throws Exception {
    UUID paymentId = UUID.randomUUID();
    var person = sampleAuthenticatedPersonAndMember().build();

    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    var user = mock(User.class);
    when(userService.findByPersonalCode(person.getPersonalCode())).thenReturn(Optional.of(user));
    when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
    doNothing()
        .when(savingFundPaymentUpsertionService)
        .cancelPayment(eq(PartyId.from(person.getRole())), eq(paymentId));

    mvc.perform(delete("/v1/savings/payments/" + paymentId).with(csrf()).with(authentication(auth)))
        .andExpect(status().isNoContent());
  }

  @Test
  void getSavingsFundOnboardingStatus_shouldReturnCompleted() throws Exception {
    var person = sampleAuthenticatedPersonAndMember().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    when(savingsFundOnboardingService.getOnboardingStatus(person.toPartyId()))
        .thenReturn(COMPLETED);

    mvc.perform(get("/v1/savings/onboarding/status").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"COMPLETED\"}"));
  }

  @Test
  void getSavingsFundOnboardingStatus_shouldReturnNull() throws Exception {
    var person = sampleAuthenticatedPersonAndMember().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    when(savingsFundOnboardingService.getOnboardingStatus(person.toPartyId())).thenReturn(null);

    mvc.perform(get("/v1/savings/onboarding/status").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":null}"));
  }

  @Test
  void getSavingsFundOnboardingStatus_shouldReturnCompletedForLegalEntity() throws Exception {
    var person = sampleAuthenticatedPersonLegalEntity().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    when(savingsFundOnboardingService.getOnboardingStatus(person.toPartyId()))
        .thenReturn(COMPLETED);

    mvc.perform(get("/v1/savings/onboarding/status").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"COMPLETED\"}"));
  }

  @Test
  void getLegalEntityOnboardingStatus_shouldReturnCompleted() throws Exception {
    var person = sampleAuthenticatedPersonAndMember().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    when(legalEntitySavingsFundOnboardingService.getOnboardingStatus(
            person.getPersonalCode(), "12345678"))
        .thenReturn(Optional.of(COMPLETED));

    mvc.perform(
            get("/v1/savings/onboarding/status/legal-entity")
                .param("registry-code", "12345678")
                .with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"COMPLETED\"}"));
  }

  @Test
  void getLegalEntityOnboardingStatus_shouldReturnNullStatus() throws Exception {
    var person = sampleAuthenticatedPersonAndMember().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    when(legalEntitySavingsFundOnboardingService.getOnboardingStatus(
            person.getPersonalCode(), "12345678"))
        .thenReturn(Optional.empty());

    mvc.perform(
            get("/v1/savings/onboarding/status/legal-entity")
                .param("registry-code", "12345678")
                .with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":null}"));
  }

  @Test
  void getBankAccounts_shouldReturnListOfIbans() throws Exception {
    var person = sampleAuthenticatedPersonAndMember().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    when(savingFundPaymentRepository.findDepositBankAccountIbans(PartyId.from(person.getRole())))
        .thenReturn(List.of("EE123456789012345678", "EE987654321098765432"));

    mvc.perform(get("/v1/savings/bank-accounts").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("[\"EE123456789012345678\",\"EE987654321098765432\"]"));
  }
}
