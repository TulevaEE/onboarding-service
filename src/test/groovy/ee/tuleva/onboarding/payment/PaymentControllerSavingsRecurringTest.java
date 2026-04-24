package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc
@WithMockUser
class PaymentControllerSavingsRecurringTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PaymentService paymentService;

  @Test
  void getPaymentLink_forSavingsRecurring_returnsUrlFromService() throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();
    var expectedUrl =
        "https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add"
            + "?i_receiver_name=Tuleva%20T%C3%A4iendav%20Kogumisfond"
            + "&i_receiver_account_no=EE711010220306707220"
            + "&i_payment_desc=38812121215"
            + "&i_amount=50"
            + "&i_currency_id=38"
            + "&i_interval_type=K"
            + "&i_date_first_payment=10.02.2020";
    var expectedLink =
        new PaymentLink(
            expectedUrl,
            "Tuleva Täiendav Kogumisfond",
            "EE711010220306707220",
            "38812121215",
            "50");

    given(paymentService.getLink(any(PaymentData.class), any(AuthenticatedPerson.class)))
        .willReturn(expectedLink);

    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    mvc.perform(
            get("/v1/payments/link"
                    + "?amount=50"
                    + "&currency=EUR"
                    + "&type=SAVINGS_RECURRING"
                    + "&paymentChannel=LHV"
                    + "&recipientPersonalCode=38812121215")
                .with(authentication(auth))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.url", is(expectedUrl)))
        .andExpect(jsonPath("$.recipientName", is("Tuleva Täiendav Kogumisfond")))
        .andExpect(jsonPath("$.recipientIban", is("EE711010220306707220")))
        .andExpect(jsonPath("$.description", is("38812121215")))
        .andExpect(jsonPath("$.amount", is("50")));
  }

  @Test
  void getPaymentLink_forSavingsRecurring_withOtherChannel_returnsNullUrlWithRecipientData()
      throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();
    var link =
        new PaymentLink(
            null, "Tuleva Täiendav Kogumisfond", "EE711010220306707220", "38812121215", "50");

    given(paymentService.getLink(any(PaymentData.class), any(AuthenticatedPerson.class)))
        .willReturn(link);

    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    mvc.perform(
            get("/v1/payments/link"
                    + "?amount=50"
                    + "&currency=EUR"
                    + "&type=SAVINGS_RECURRING"
                    + "&paymentChannel=OTHER"
                    + "&recipientPersonalCode=38812121215")
                .with(authentication(auth))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").doesNotExist())
        .andExpect(jsonPath("$.recipientIban", is("EE711010220306707220")));
  }
}
