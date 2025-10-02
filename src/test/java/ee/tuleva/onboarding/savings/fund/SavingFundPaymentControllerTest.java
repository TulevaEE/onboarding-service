package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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

  @MockitoBean private SavingFundPaymentUpsertionService savingFundPaymentUpsertionService;

  @Test
  void cancelSavingsFundPayment_shouldReturnNoContent() throws Exception {
    UUID paymentId = UUID.randomUUID();

    var auth =
        new UsernamePasswordAuthenticationToken(
            AuthenticatedPerson.builder().userId(1L).build(),
            null,
            List.of(new SimpleGrantedAuthority(USER)));

    Mockito.doNothing()
        .when(savingFundPaymentUpsertionService)
        .cancelUserPayment(any(), eq(paymentId));

    mvc.perform(delete("/v1/savings/payments/" + paymentId).with(csrf()).with(authentication(auth)))
        .andExpect(status().isNoContent());
  }
}
