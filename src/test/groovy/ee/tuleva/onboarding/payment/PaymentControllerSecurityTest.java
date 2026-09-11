package ee.tuleva.onboarding.payment;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfiguration.class)
class PaymentControllerSecurityTest {

  private static final String ORDER_TOKEN = "header.payload.signature";
  private static final String NOTIFICATION_BODY = "{\"orderToken\":\"" + ORDER_TOKEN + "\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private PaymentService paymentService;
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  @Test
  void savingsPaymentWebhookIsAcceptedWithoutAuthentication() throws Exception {
    given(paymentService.processSavingsPaymentToken(ORDER_TOKEN)).willReturn(true);

    mvc.perform(
            post("/v1/payments/savings/notifications")
                .contentType(APPLICATION_JSON)
                .content(NOTIFICATION_BODY))
        .andExpect(status().isOk());

    verify(paymentService).processSavingsPaymentToken(ORDER_TOKEN);
  }

  @Test
  void thirdPillarPaymentWebhookIsAcceptedWithoutAuthentication() throws Exception {
    given(paymentService.processToken(ORDER_TOKEN)).willReturn(Optional.empty());

    mvc.perform(
            post("/v1/payments/notifications")
                .contentType(APPLICATION_JSON)
                .content(NOTIFICATION_BODY))
        .andExpect(status().isOk());

    verify(paymentService).processToken(ORDER_TOKEN);
  }
}
