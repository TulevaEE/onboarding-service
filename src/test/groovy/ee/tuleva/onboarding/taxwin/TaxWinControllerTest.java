package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.RECENT_MONTHS;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeSource.ESTIMATED;
import static ee.tuleva.onboarding.taxwin.PaymentSuggestion.SINGLE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.AWAITING_SIGNATURE;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.signature.MobileSignatureStatusResponse;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest({
  TaxHeadroomController.class,
  TaxDataConsentController.class,
  TaxDataConsentSigningController.class
})
@AutoConfigureMockMvc
class TaxWinControllerTest {

  private static final String HEADROOM = "/v1/third-pillar/tax-headroom";
  private static final String CONSENTS = "/v1/tax-data-consents";
  private static final TaxHeadroomResponse HEADROOM_RESPONSE =
      new TaxHeadroomResponse(
          2026,
          new BigDecimal("25000.00"),
          YearMonth.of(2026, 10),
          List.of(
              new ExpectedMonthlyIncome(
                  YearMonth.of(2026, 11), new BigDecimal("2500.00"), ESTIMATED),
              new ExpectedMonthlyIncome(
                  YearMonth.of(2026, 12), new BigDecimal("2500.00"), ESTIMATED)),
          RECENT_MONTHS,
          new BigDecimal("3260.00"),
          2,
          4500,
          LimitCertainty.ESTIMATED,
          1240,
          272,
          375,
          SINGLE);

  @Autowired private MockMvc mvc;

  @MockitoBean private TaxHeadroomService headroomService;
  @MockitoBean private TaxDataConsentService consentService;
  @MockitoBean private TaxDataConsentSignatureService signatureService;
  @MockitoBean private LocaleService localeService;

  private final AuthenticatedPerson person =
      authenticatedPersonFromUser(sampleUser().build()).build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          person, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void showsTheHeadroomWithMonthsWrittenAsYearAndMonth() throws Exception {
    given(headroomService.headroom(person)).willReturn(Optional.of(HEADROOM_RESPONSE));

    mvc.perform(as(get(HEADROOM)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"year":2026,"knownIncome":25000.00,"incomeKnownThrough":"2026-10",
                     "expectedIncome":[{"month":"2026-11","amount":2500.00,"source":"ESTIMATED"},
                                       {"month":"2026-12","amount":2500.00,"source":"ESTIMATED"}],
                     "expectedIncomeBasis":"RECENT_MONTHS","paid":3260.00,"monthsLeft":2,
                     "limit":4500,"limitCertainty":"ESTIMATED","remaining":1240,"refund":272,
                     "monthlyPayment":375,"paymentSuggestion":"SINGLE"}
                    """));
  }

  @Test
  void aPersonWithoutAValidConsentHasNoHeadroomToShow() throws Exception {
    given(headroomService.headroom(person)).willReturn(Optional.empty());

    mvc.perform(as(get(HEADROOM))).andExpect(status().isNoContent());
  }

  @Test
  void saysSoWhenTheTaxDataCannotBeRefreshedAndNothingRecentIsStored() throws Exception {
    given(headroomService.headroom(person)).willThrow(new TaxHeadroomUnavailableException(1L));

    mvc.perform(as(get(HEADROOM))).andExpect(status().isServiceUnavailable());
  }

  @Test
  void recalculatesWithTheIncomeThePersonExpectsForAMonth() throws Exception {
    given(
            headroomService.correctExpectedIncome(
                person, YearMonth.of(2026, 12), new BigDecimal("12500.50")))
        .willReturn(Optional.of(HEADROOM_RESPONSE));

    mvc.perform(
            as(put(HEADROOM + "/expected-income/2026-12"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":12500.50}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"remaining\":1240}"));
  }

  @Test
  void refusesExpectedIncomeForAMonthThatCannotBeCorrected() throws Exception {
    given(headroomService.correctExpectedIncome(person, YearMonth.of(2026, 10), BigDecimal.ONE))
        .willThrow(new InvalidExpectedIncomeException(YearMonth.of(2026, 10)));

    mvc.perform(
            as(put(HEADROOM + "/expected-income/2026-10"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void expectedIncomeNeedsAnAmount() throws Exception {
    mvc.perform(
            as(put(HEADROOM + "/expected-income/2026-12"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void expectedIncomeNeedsAValidConsent() throws Exception {
    given(headroomService.correctExpectedIncome(person, YearMonth.of(2026, 12), BigDecimal.TEN))
        .willReturn(Optional.empty());

    mvc.perform(
            as(put(HEADROOM + "/expected-income/2026-12"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void goesBackToTheEstimateForAMonth() throws Exception {
    given(headroomService.removeCorrection(person, YearMonth.of(2026, 12)))
        .willReturn(Optional.of(HEADROOM_RESPONSE));

    mvc.perform(as(delete(HEADROOM + "/expected-income/2026-12")))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"remaining\":1240}"));
  }

  @Test
  void asksForAConsentInThePersonsLanguage() throws Exception {
    given(localeService.getCurrentLocale()).willReturn(Locale.ENGLISH);
    given(consentService.create(person, Locale.ENGLISH))
        .willReturn(new TaxDataConsentResponse(7L, AWAITING_SIGNATURE));

    mvc.perform(as(post(CONSENTS)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"id\":7,\"status\":\"AWAITING_SIGNATURE\"}", STRICT));
  }

  @Test
  void showsTheConsentDocumentBeforeItIsSigned() throws Exception {
    byte[] pdf = "%PDF-1.7".getBytes();
    given(consentService.document(7L, person)).willReturn(pdf);

    mvc.perform(as(get(CONSENTS + "/7/document")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(pdf));
  }

  @Test
  void keepsPollingWhileTheConsentServiceApprovesTheSignature() throws Exception {
    given(signatureService.getSmartIdSignatureStatus(7L, person))
        .willReturn(new MobileSignatureStatusResponse(OUTSTANDING_TRANSACTION, "1234"));

    mvc.perform(as(get(CONSENTS + "/7/signature/smart-id/status")))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"statusCode\":\"OUTSTANDING_TRANSACTION\"}"));
  }

  @Test
  void reportsASignatureTheConsentServiceRejected() throws Exception {
    given(signatureService.getMobileIdSignatureStatus(7L, person))
        .willThrow(new TaxDataConsentRejectedException(7L));

    mvc.perform(as(get(CONSENTS + "/7/signature/mobile-id/status")))
        .andExpect(status().isUnprocessableContent());
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
    return request.with(csrf()).with(authentication(authentication));
  }
}
