package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.SENT;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.arteaRequest;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.compensaRequest;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.savedForm;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.signedForm;
import static ee.tuleva.onboarding.insurance.Insurer.ARTEA;
import static ee.tuleva.onboarding.insurance.Insurer.COMPENSA;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.error.NotFoundException;
import ee.tuleva.onboarding.signature.FinishIdCardSignCommand;
import ee.tuleva.onboarding.signature.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.StartIdCardSignCommand;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@WebMvcTest({InsuranceTransferFormController.class, InsuranceTransferFormSigningController.class})
@AutoConfigureMockMvc
class InsuranceTransferFormControllerTest {

  private static final String FORMS = "/v1/insurance-transfer-forms";

  private static final String ADDRESS =
      """
      "address": {"street": "Tamme tn 1-2", "city": "Tallinn", "postalCode": "10111", "countryCode": "EE"}
      """;

  @Autowired private MockMvc mvc;

  @MockitoBean private InsuranceTransferFormService formService;
  @MockitoBean private InsuranceTransferFormSignatureService signatureService;

  private final Long userId = sampleUser().build().getId();
  private final AuthenticatedPerson person =
      authenticatedPersonFromUser(sampleUser().build()).build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          person, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void fillsInTheFormFromTheTrimmedAnswers() throws Exception {
    given(formService.create(person, compensaRequest()))
        .willReturn(savedForm(1L, userId, COMPENSA));

    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"insurer": "COMPENSA", "policyNumber": " UP000123 ", "endDate": null, %s}
                    """
                        .formatted(ADDRESS)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"id": 1, "insurer": "COMPENSA", "insurerEmail": "info@compensalife.ee",
                     "status": "CREATED", "emailStatus": null}
                    """,
                    STRICT));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"insurer\": \"COMPENSA\", \"policyNumber\": \"  \", %s}",
        "{\"insurer\": \"COMPENSA\", \"policyNumber\": \"UP000123\"}",
        "{\"insurer\": \"ERGO\", \"policyNumber\": \"70-512-345678\", %s, \"citizenship\": [\"EE\"],"
            + " \"countryOfBirth\": \"EE\", \"taxResidency\": \"EE\"}",
        "{\"insurer\": \"ERGO\", \"policyNumber\": \"70-512-345678\", %s, \"citizenship\": [],"
            + " \"countryOfBirth\": \"EE\", \"taxResidency\": \"EE\", \"pep\": false}",
        "{\"insurer\": \"ERGO\", \"policyNumber\": \"70-512-345678\", %s, \"citizenship\": [\"EE\"],"
            + " \"countryOfBirth\": \"XX\", \"taxResidency\": \"EE\", \"pep\": false}",
        "{\"insurer\": \"ERGO\", \"policyNumber\": \"12345\", %s, \"citizenship\": [\"EE\"],"
            + " \"countryOfBirth\": \"EE\", \"taxResidency\": \"EE\", \"pep\": false}",
        "{\"insurer\": \"ARTEA\", \"policyNumber\": \"SP000123\", %s, \"citizenship\": [\"EE\"],"
            + " \"countryOfBirth\": \"EE\", \"taxResidency\": \"EE\", \"pep\": false}"
      })
  void refusesAnswersTheInsurersFormNeedsButDoesNotHave(String body) throws Exception {
    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(ADDRESS)))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"occupation\": {\"status\": \"EMPLOYED\", \"position\": \"Spetsialist\"},"
            + " \"incomeSources\": [{\"source\": \"SALARY\"}], \"premiumAccountIban\":"
            + " \"EE442200221092874625\"",
        "\"occupation\": {\"status\": \"OTHER\"}, \"incomeSources\": [{\"source\": \"SALARY\"}],"
            + " \"premiumAccountIban\": \"EE442200221092874625\"",
        "\"occupation\": {\"status\": \"RETIRED\"}, \"incomeSources\": [{\"source\":"
            + " \"DIVIDENDS\"}], \"premiumAccountIban\": \"EE442200221092874625\"",
        "\"occupation\": {\"status\": \"RETIRED\"}, \"incomeSources\": [], \"premiumAccountIban\":"
            + " \"EE442200221092874625\"",
        "\"occupation\": {\"status\": \"RETIRED\"}, \"incomeSources\": [{\"source\": \"SALARY\"}],"
            + " \"premiumAccountIban\": \"EE000000000000000000\""
      })
  void refusesAnIncompleteCustomerQuestionnaire(String answers) throws Exception {
    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"insurer": "ARTEA", "policyNumber": "SP000123", %s, "citizenship": ["EE"],
                     "countryOfBirth": "EE", "taxResidency": "EE", "pep": false,
                     "artea": {"idDocument": {"type": "ID_CARD", "number": "AB0000000",
                       "issuedOn": "2024-01-01", "validUntil": "2029-01-01", "issuingCountry": "EE"},
                       "monthlyNetIncome": "UP_TO_600", %s}}
                    """
                        .formatted(ADDRESS, answers)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void acceptsACompleteCustomerQuestionnaireAndReadsTheContractNumberAsTheInsurerPrintsIt()
      throws Exception {
    given(formService.create(person, arteaRequest())).willReturn(savedForm(1L, userId, ARTEA));

    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeArteaRequest(" sp-000123 ")))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"UP000123", "SP1234567", "XX123456", "12345678"})
  void refusesAContractNumberOutsideTheSeriesTheInsurerIssues(String policyNumber)
      throws Exception {
    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeArteaRequest(policyNumber)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].path").value("policyNumberInTheFormatTheFormHolds"));
  }

  @Test
  void refusesAPersonActingForSomeoneElse() throws Exception {
    given(formService.create(any(), any())).willThrow(new NotActingAsSelfException());

    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"insurer\": \"COMPENSA\", \"policyNumber\": \"UP000123\", %s}"
                        .formatted(ADDRESS)))
        .andExpect(status().isForbidden());
  }

  @Test
  void saysThePensionAccountIsNotReadyWhenItsNumberIsNotKnownYet() throws Exception {
    given(formService.create(any(), any())).willThrow(new PensionAccountNotReadyException());

    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"insurer\": \"COMPENSA\", \"policyNumber\": \"UP000123\", %s}"
                        .formatted(ADDRESS)))
        .andExpect(status().isConflict())
        .andExpect(content().json("{\"error\": \"PENSION_ACCOUNT_NOT_READY\"}"));
  }

  @Test
  void refusesAnEndDateThatHasPassed() throws Exception {
    given(formService.create(any(), any()))
        .willThrow(
            new EndDateInThePastException(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 10, 18)));

    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"insurer\": \"COMPENSA\", \"policyNumber\": \"UP000123\","
                        + " \"endDate\": \"2024-01-01\", %s}".formatted(ADDRESS)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("insurance.transfer.form.end.date.passed"));
  }

  @Test
  void refusesAnIdentityDocumentIssuedAfterToday() throws Exception {
    given(formService.create(any(), any()))
        .willThrow(
            new IdentityDocumentIssuedInTheFutureException(
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 18)));

    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeArteaRequest("SP000123")))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[0].code")
                .value("insurance.transfer.form.id.document.issued.in.future"));
  }

  @Test
  void refusesAnIdentityDocumentThatHasExpired() throws Exception {
    given(formService.create(any(), any()))
        .willThrow(
            new IdentityDocumentExpiredException(
                LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 18)));

    mvc.perform(
            as(post(FORMS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeArteaRequest("SP000123")))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[0].code").value("insurance.transfer.form.id.document.expired"));
  }

  @Test
  void reportsTheSignedStateAndHowTheEmailEnded() throws Exception {
    var form = signedForm(1L, userId, COMPENSA);
    form.recordEmailStatus(SENT);
    given(formService.summary(1L, person)).willReturn(form.summary());

    mvc.perform(as(get(FORMS + "/1")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"id": 1, "insurer": "COMPENSA", "insurerEmail": "info@compensalife.ee",
                     "status": "SIGNED", "emailStatus": "SENT"}
                    """));
  }

  @Test
  void hidesAnotherPersonsForm() throws Exception {
    given(formService.summary(1L, person)).willThrow(new NotFoundException("not found"));

    mvc.perform(as(get(FORMS + "/1"))).andExpect(status().isNotFound());
  }

  @Test
  void showsTheFilledFormAsAPdf() throws Exception {
    given(formService.get(1L, person)).willReturn(savedForm(1L, userId, COMPENSA));

    mvc.perform(as(get(FORMS + "/1/preview")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(
            header()
                .string("Content-Disposition", "inline; filename=\"Tuleva_Compensa_avaldus.pdf\""))
        .andExpect(content().bytes(new byte[] {1, 2, 3}));
  }

  @Test
  void downloadsTheSignedContainer() throws Exception {
    given(formService.get(1L, person)).willReturn(signedForm(1L, userId, COMPENSA));

    mvc.perform(as(get(FORMS + "/1/signed-file")))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/vnd.etsi.asic-e+zip"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"Tuleva_Compensa_avaldus.asice\""))
        .andExpect(content().bytes(new byte[] {4, 5, 6}));
  }

  @Test
  void hasNoSignedContainerBeforeSigning() throws Exception {
    given(formService.get(1L, person)).willReturn(savedForm(1L, userId, COMPENSA));

    mvc.perform(as(get(FORMS + "/1/signed-file"))).andExpect(status().isNotFound());
  }

  @Test
  void signsWithSmartId() throws Exception {
    given(signatureService.startSmartIdSignature(1L, person))
        .willReturn(new MobileSignatureResponse("1234"));
    given(signatureService.getSmartIdSignatureStatus(1L, person))
        .willReturn(new MobileSignatureStatusResponse(OUTSTANDING_TRANSACTION, "1234"));

    mvc.perform(as(put(FORMS + "/1/signature/smart-id")))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"challengeCode\": \"1234\"}"));
    mvc.perform(as(get(FORMS + "/1/signature/smart-id/status")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"statusCode\": \"OUTSTANDING_TRANSACTION\", \"challengeCode\": \"1234\"}"));
  }

  @Test
  void signsWithMobileId() throws Exception {
    given(signatureService.startMobileIdSignature(1L, person))
        .willReturn(new MobileSignatureResponse("5678"));
    given(signatureService.getMobileIdSignatureStatus(1L, person))
        .willReturn(new MobileSignatureStatusResponse(SIGNATURE, "5678"));

    mvc.perform(as(put(FORMS + "/1/signature/mobile-id")))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"challengeCode\": \"5678\"}"));
    mvc.perform(as(get(FORMS + "/1/signature/mobile-id/status")))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"statusCode\": \"SIGNATURE\", \"challengeCode\": \"5678\"}"));
  }

  @Test
  void signsWithIdCard() throws Exception {
    given(
            signatureService.startIdCardSignature(
                1L, person, new StartIdCardSignCommand("certificate", List.of("SHA-256"))))
        .willReturn(new IdCardSignatureResponse("hash", "SHA-256"));
    given(
            signatureService.persistIdCardSignature(
                1L, new FinishIdCardSignCommand("signature"), person))
        .willReturn(new IdCardSignatureStatusResponse(SIGNATURE));
    given(signatureService.getIdCardSignatureStatus(1L, person))
        .willReturn(new IdCardSignatureStatusResponse(SIGNATURE));

    mvc.perform(
            as(put(FORMS + "/1/signature/id-card"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"certificate\": \"certificate\", \"supportedHashFunctions\": [\"SHA-256\"]}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"hash\": \"hash\", \"hashFunction\": \"SHA-256\"}"));
    mvc.perform(
            as(put(FORMS + "/1/signature/id-card/signature"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signature\": \"signature\"}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"statusCode\": \"SIGNATURE\"}"));
    mvc.perform(as(get(FORMS + "/1/signature/id-card/status")))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"statusCode\": \"SIGNATURE\"}"));
  }

  private static String completeArteaRequest(String policyNumber) {
    return """
        {"insurer": "ARTEA", "policyNumber": "%s", %s, "citizenship": ["EE", "FI"],
         "countryOfBirth": "EE", "taxResidency": "EE", "pep": false,
         "artea": {"idDocument": {"type": "ID_CARD", "number": "AB0000000",
           "issuedOn": "2024-01-01", "validUntil": "2029-01-01", "issuingCountry": "EE"},
           "occupation": {"status": "EMPLOYED", "position": "Spetsialist",
             "employer": "Näidis OÜ", "employerAddress": "Tallinn", "description": null},
           "incomeSources": [{"source": "SALARY", "details": null},
             {"source": "DIVIDENDS", "details": "Näidis AS"}],
           "monthlyNetIncome": "FROM_1201_TO_3000",
           "premiumAccountIban": "EE442200221092874625"}}
        """
        .formatted(policyNumber, ADDRESS);
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
    return request.with(csrf()).with(authentication(authentication));
  }
}
