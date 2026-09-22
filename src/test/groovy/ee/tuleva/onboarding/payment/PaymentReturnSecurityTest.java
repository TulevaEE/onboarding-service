package ee.tuleva.onboarding.payment;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import ee.tuleva.onboarding.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "frontend.url=https://frontend.url")
class PaymentReturnSecurityTest {

  private static final String FRONTEND_URL = "https://frontend.url";
  private static final String GIFT_LINK_TOKEN = "ABCDEFGH12345678";
  private static final String ORDER_TOKEN = "an-order-token";

  @Autowired private MockMvc mvc;
  @MockitoBean private PaymentService paymentService;
  @MockitoBean private UserService userService;
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  @Test
  void aStrangerReturningFromTheBankReachesTheThankYouPage() throws Exception {
    given(paymentService.processSavingsPaymentToken(ORDER_TOKEN))
        .willReturn(new SavingsPaymentOutcome(true, GIFT_LINK_TOKEN));

    mvc.perform(get("/v1/payments/savings/callback").param("order-token", ORDER_TOKEN))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(FRONTEND_URL + "/kingitus/" + GIFT_LINK_TOKEN + "/tehtud"));
  }

  @Test
  void aStrangerWhoseGiftPaymentDidNotGoThroughIsSentBackToTheGiftPage() throws Exception {
    given(paymentService.processSavingsPaymentToken(ORDER_TOKEN))
        .willReturn(new SavingsPaymentOutcome(false, GIFT_LINK_TOKEN));

    mvc.perform(get("/v1/payments/savings/callback").param("order-token", ORDER_TOKEN))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(FRONTEND_URL + "/kingitus/" + GIFT_LINK_TOKEN));
  }

  @Test
  void aStrangerMayNotAskForAPaymentLink() throws Exception {
    mvc.perform(get("/v1/payments/link")).andExpect(status().isForbidden());

    verifyNoInteractions(paymentService);
  }
}
