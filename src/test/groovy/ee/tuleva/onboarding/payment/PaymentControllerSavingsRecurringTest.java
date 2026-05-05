package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.payment.savings.SavingsFundRecipientConfiguration;
import ee.tuleva.onboarding.payment.savings.recurring.SavingsFundRecurringPaymentLinkGenerator;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  private final SavingsFundRecipientConfiguration recipientConfiguration =
      new SavingsFundRecipientConfiguration();
  private final PaymentDateProvider paymentDateProvider = mock(PaymentDateProvider.class);
  private final SavingsFundRecurringPaymentLinkGenerator realSavingsRecurringGenerator =
      new SavingsFundRecurringPaymentLinkGenerator(recipientConfiguration, paymentDateProvider);

  @BeforeEach
  void delegateSavingsRecurringToRealGenerator() {
    recipientConfiguration.setRecipientName("Tuleva Täiendav Kogumisfond");
    recipientConfiguration.setRecipientIban("EE711010220306707220");
    given(paymentDateProvider.tenthDayOfMonth()).willReturn(LocalDate.of(2026, 5, 10));
  }

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
        new PrefilledLink(
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
        .andExpect(jsonPath("$.type").value("PREFILLED"))
        .andExpect(jsonPath("$.url").value(expectedUrl))
        .andExpect(jsonPath("$.recipientName").value("Tuleva Täiendav Kogumisfond"))
        .andExpect(jsonPath("$.recipientIban").value("EE711010220306707220"))
        .andExpect(jsonPath("$.description").value("38812121215"))
        .andExpect(jsonPath("$.amount").value("50"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SINGLE", "RECURRING", "SAVINGS"})
  void getPaymentLink_withoutPaymentChannel_forChannelRequiredType_returns400(String type)
      throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();

    given(paymentService.getLink(any(PaymentData.class), any(AuthenticatedPerson.class)))
        .willThrow(
            new ErrorsResponseException(
                ErrorsResponse.ofSingleError(
                    "payment.channel.required", "Payment channel is required.")));

    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    mvc.perform(
            get("/v1/payments/link"
                    + "?amount=10"
                    + "&currency=EUR"
                    + "&type="
                    + type
                    + "&recipientPersonalCode=38812121215")
                .with(authentication(auth))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("payment.channel.required"));
  }

  @Test
  void getPaymentLink_forSavingsRecurring_withoutAmount_buildsUrlWithoutAmountParam()
      throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();

    willAnswer(
            invocation ->
                realSavingsRecurringGenerator.getPaymentLink(
                    invocation.getArgument(0), invocation.getArgument(1)))
        .given(paymentService)
        .getLink(any(PaymentData.class), any(AuthenticatedPerson.class));

    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    mvc.perform(
            get("/v1/payments/link"
                    + "?currency=EUR"
                    + "&type=SAVINGS_RECURRING"
                    + "&paymentChannel=LHV"
                    + "&recipientPersonalCode=38812121215")
                .with(authentication(auth))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("PREFILLED"))
        .andExpect(jsonPath("$.amount").doesNotExist())
        .andExpect(jsonPath("$.recipientName").value("Tuleva Täiendav Kogumisfond"))
        .andExpect(jsonPath("$.recipientIban").value("EE711010220306707220"))
        .andExpect(jsonPath("$.url").value(not(containsString("i_amount="))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.001", "-1"})
  void getPaymentLink_withAmountBelowMinimum_returns400(String amount) throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));

    mvc.perform(
            get("/v1/payments/link"
                    + "?amount="
                    + amount
                    + "&currency=EUR"
                    + "&type=SAVINGS_RECURRING"
                    + "&paymentChannel=LHV"
                    + "&recipientPersonalCode=38812121215")
                .with(authentication(auth))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("DecimalMin"))
        .andExpect(jsonPath("$.errors[0].path").value("amount"));
  }

  @Test
  void getPaymentLink_forSavingsRecurring_withoutPaymentChannel_returnsNullUrlWithRecipientData()
      throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();
    var link =
        new PrefilledLink(
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
                    + "&recipientPersonalCode=38812121215")
                .with(authentication(auth))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("PREFILLED"))
        .andExpect(jsonPath("$.url").doesNotExist())
        .andExpect(jsonPath("$.recipientIban").value("EE711010220306707220"));
  }
}
