package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
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
class PaymentControllerRecurringTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PaymentService paymentService;

  @Test
  void getPaymentLink_forRecurringLhv_serializesAsPrefilledWithDiscriminator() throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();
    var expectedUrl =
        "https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add"
            + "?i_receiver_name=AS%20Pensionikeskus"
            + "&i_receiver_account_no=EE547700771002908125"
            + "&i_payment_desc=30101119828%2c%20EE3600001707"
            + "&i_payment_clirefno=993432432"
            + "&i_amount=50"
            + "&i_currency_id=38"
            + "&i_interval_type=K"
            + "&i_date_first_payment=10.01.2020";
    var expectedLink =
        new PrefilledLink(
            expectedUrl,
            "AS Pensionikeskus",
            "EE547700771002908125",
            "30101119828, EE3600001707",
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
                    + "&type=RECURRING"
                    + "&paymentChannel=LHV"
                    + "&recipientPersonalCode=38812121215")
                .with(authentication(auth))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("PREFILLED"))
        .andExpect(jsonPath("$.url").value(expectedUrl))
        .andExpect(jsonPath("$.recipientName").value("AS Pensionikeskus"))
        .andExpect(jsonPath("$.recipientIban").value("EE547700771002908125"))
        .andExpect(jsonPath("$.description").value("30101119828, EE3600001707"))
        .andExpect(jsonPath("$.amount").value("50"));
  }

  @Test
  void getPaymentLink_forRecurringSwedbank_serializesAsRedirect() throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();
    var expectedLink =
        new RedirectLink("https://www.swedbank.ee/private/pensions/pillar3/orderp3p");

    given(paymentService.getLink(any(PaymentData.class), any(AuthenticatedPerson.class)))
        .willReturn(expectedLink);

    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    mvc.perform(
            get("/v1/payments/link"
                    + "?amount=50"
                    + "&currency=EUR"
                    + "&type=RECURRING"
                    + "&paymentChannel=SWEDBANK"
                    + "&recipientPersonalCode=38812121215")
                .with(authentication(auth))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("REDIRECT"))
        .andExpect(
            jsonPath("$.url").value("https://www.swedbank.ee/private/pensions/pillar3/orderp3p"))
        .andExpect(jsonPath("$.recipientName").doesNotExist())
        .andExpect(jsonPath("$.recipientIban").doesNotExist())
        .andExpect(jsonPath("$.description").doesNotExist())
        .andExpect(jsonPath("$.amount").doesNotExist());
  }
}
