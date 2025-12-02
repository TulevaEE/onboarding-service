package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
  @MockitoBean private LocaleService localeService;
  @MockitoBean private ApplicationEventPublisher applicationEventPublisher;

  @Test
  void cancelSavingsFundPayment_shouldReturnNoContent() throws Exception {
    UUID paymentId = UUID.randomUUID();

    var auth =
        new UsernamePasswordAuthenticationToken(
            AuthenticatedPerson.builder().userId(1L).build(),
            null,
            List.of(new SimpleGrantedAuthority(USER)));

    var user = Mockito.mock(User.class);
    Mockito.when(userService.getByIdOrThrow(1L)).thenReturn(user);
    Mockito.when(localeService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
    Mockito.doNothing()
        .when(savingFundPaymentUpsertionService)
        .cancelUserPayment(any(), eq(paymentId));

    mvc.perform(delete("/v1/savings/payments/" + paymentId).with(csrf()).with(authentication(auth)))
        .andExpect(status().isNoContent());
  }

  @Test
  void getSavingsFundOnboardingStatus_shouldReturnCompleted() throws Exception {
    var auth =
        new UsernamePasswordAuthenticationToken(
            AuthenticatedPerson.builder().userId(1L).build(),
            null,
            List.of(new SimpleGrantedAuthority(USER)));

    var user = Mockito.mock(User.class);
    Mockito.when(userService.getByIdOrThrow(1L)).thenReturn(user);
    Mockito.when(savingsFundOnboardingService.getOnboardingStatus(user))
        .thenReturn(SavingsFundOnboardingStatus.COMPLETED);

    mvc.perform(get("/v1/savings/onboarding/status").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"COMPLETED\"}"));
  }

  @Test
  void getBankAccounts_shouldReturnListOfIbans() throws Exception {
    var auth =
        new UsernamePasswordAuthenticationToken(
            AuthenticatedPerson.builder().userId(1L).build(),
            null,
            List.of(new SimpleGrantedAuthority(USER)));

    var user = Mockito.mock(User.class);
    Mockito.when(user.getId()).thenReturn(1L);
    Mockito.when(userService.getByIdOrThrow(1L)).thenReturn(user);
    Mockito.when(savingFundPaymentRepository.findUserDepositBankAccountIbans(1L))
        .thenReturn(List.of("EE123456789012345678", "EE987654321098765432"));

    mvc.perform(get("/v1/savings/bank-accounts").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("[\"EE123456789012345678\",\"EE987654321098765432\"]"));
  }
}
