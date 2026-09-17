package ee.tuleva.onboarding.savings.fund.gift;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import ee.tuleva.onboarding.payment.RedirectLink;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PublicGiftLinkController.class, GiftLinkController.class})
@Import(SecurityConfiguration.class)
class GiftLinkSecurityTest {

  private static final String TOKEN = "ABCDEFGH12345678";
  private static final String CHILD_CODE = "38888888888";
  private static final String PAYMENT_BODY =
      "{\"amount\":100,\"paymentChannel\":\"LHV\",\"message\":\"Happy birthday\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private GiftLinkService giftLinkService;
  @MockitoBean private GiftPaymentService giftPaymentService;
  @MockitoBean private ReceivedGiftService receivedGiftService;
  @MockitoBean private UserService userService;
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  @Test
  void anyoneMaySeeWhoAGiftLinkIsFor() throws Exception {
    given(giftLinkService.findOpenLink(TOKEN))
        .willReturn(GiftLink.builder().recipientPersonalCode(CHILD_CODE).build());
    given(userService.findByPersonalCode(CHILD_CODE))
        .willReturn(Optional.of(User.builder().firstName("Mari").lastName("Tamm").build()));

    mvc.perform(get("/v1/gift-links/" + TOKEN)).andExpect(status().isOk());
  }

  @Test
  void anyoneMayStartPayingAGift() throws Exception {
    given(giftPaymentService.startPayment(any(), any()))
        .willReturn(new RedirectLink("https://montonio.example/pay"));

    mvc.perform(
            post("/v1/gift-links/" + TOKEN + "/payments")
                .contentType(APPLICATION_JSON)
                .content(PAYMENT_BODY))
        .andExpect(status().isOk());
  }

  @Test
  void aStrangerMayNotOpenALink() throws Exception {
    mvc.perform(post("/v1/savings-fund/gift-links").contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(giftLinkService);
  }

  @Test
  void aStrangerMayNotReadTheGiftsAChildReceived() throws Exception {
    mvc.perform(get("/v1/savings-fund/gift-links/gifts")).andExpect(status().isForbidden());

    verifyNoInteractions(receivedGiftService);
  }

  @Test
  void aStrangerMayNotReplaceALink() throws Exception {
    mvc.perform(
            post("/v1/savings-fund/gift-links/" + UUID.randomUUID() + "/replace")
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(giftLinkService);
  }
}
