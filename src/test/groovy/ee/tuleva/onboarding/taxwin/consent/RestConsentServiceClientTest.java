package ee.tuleva.onboarding.taxwin.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.json.JsonCompareMode.LENIENT;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.taxwin.ConsentAlreadyApproved;
import ee.tuleva.onboarding.taxwin.ConsentDocument;
import ee.tuleva.onboarding.taxwin.ConsentRejectedException;
import ee.tuleva.onboarding.taxwin.ConsentServiceUnavailableException;
import ee.tuleva.onboarding.taxwin.TaxHeadroomProperties;
import java.util.Base64;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestConsentServiceClientTest {

  private static final String CONSENT_API =
      "http://xroad.test/r1/ee-dev/GOV/70006317/consent/consent-stage/api/consent";
  private static final String CONFIRM_REFERENCE = "5b1c4f2e-7d3a-4a1e-9c2f-0d8e6b7a1c3d";
  private static final byte[] PDF = "%PDF-1.7 Nousolek".getBytes();
  private static final TaxHeadroomProperties PROPERTIES = properties("isiku_maksuandmed");

  private MockRestServiceServer server;
  private RestConsentServiceClient client = client(PROPERTIES);

  @AfterEach
  void verifyAllRequestsWereSent() {
    server.verify();
  }

  @Test
  void asksForAConsentDocumentForThePersonToSignInTheirLanguage() {
    server
        .expect(requestTo(CONSENT_API + "/third-party"))
        .andExpect(method(POST))
        .andExpect(header("X-Road-Client", "ee-dev/COM/14118923/tuleva-fund-management"))
        .andExpect(
            content()
                .json(
                    """
                    {"idCode":"38888888888","purposeDeclarationBusinessIdentifiers":["isiku_maksuandmed"],
                     "language":"en","fileType":"GENERATED_PDF"}
                    """,
                    STRICT))
        .andRespond(
            withSuccess(
                """
                [{"consentConfirmReference":"%s","idCode":"38888888888","validTo":"2027-11-18",
                  "files":[{"fileType":"GENERATED_PDF","fileContent":"%s"}]}]
                """
                    .formatted(CONFIRM_REFERENCE, Base64.getEncoder().encodeToString(PDF)),
                APPLICATION_JSON));

    var request = client.requestConsent("38888888888", Locale.ENGLISH);

    assertThat(request).isInstanceOf(ConsentDocument.class);
    var document = (ConsentDocument) request;
    assertThat(document.confirmReference()).isEqualTo(CONFIRM_REFERENCE);
    assertThat(document.pdf()).isEqualTo(PDF);
  }

  @Test
  void aLanguageTheConsentServiceDoesNotSpeakFallsBackToEstonian() {
    server
        .expect(requestTo(CONSENT_API + "/third-party"))
        .andExpect(content().json("{\"language\":\"et\"}", LENIENT))
        .andRespond(
            withSuccess(
                "[{\"consentConfirmReference\":\"%s\",\"files\":[{\"fileType\":\"GENERATED_PDF\",\"fileContent\":\"\"}]}]"
                    .formatted(CONFIRM_REFERENCE),
                APPLICATION_JSON));

    client.requestConsent("38888888888", Locale.FRENCH);
  }

  @Test
  void aPersonWhoAlreadyApprovedTheConsentHasNothingToSign() {
    server
        .expect(requestTo(CONSENT_API + "/third-party"))
        .andRespond(
            withStatus(INTERNAL_SERVER_ERROR)
                .contentType(APPLICATION_JSON)
                .body("{\"errorCode\":\"ALL_REQUESTED_CONSENTS_HAVE_ALREADY_BEEN_APPROVED\"}"));

    assertThat(client.requestConsent("38888888888", Locale.of("et")))
        .isEqualTo(new ConsentAlreadyApproved());
  }

  @Test
  void withoutAPurposeDeclarationTheConsentCannotBeAskedFor() {
    client = client(properties(null));

    assertThatThrownBy(() -> client.requestConsent("38888888888", Locale.of("et")))
        .isInstanceOf(ConsentServiceUnavailableException.class);
  }

  @Test
  void sendsTheSignedContainerForApproval() {
    server
        .expect(requestTo(CONSENT_API + "/third-party/container"))
        .andExpect(method(POST))
        .andExpect(
            content()
                .json(
                    "[{\"consentConfirmReference\":\"%s\",\"file\":\"%s\"}]"
                        .formatted(CONFIRM_REFERENCE, Base64.getEncoder().encodeToString(PDF)),
                    STRICT))
        .andRespond(
            withSuccess(
                "[{\"consentConfirmReference\":\"%s\",\"status\":\"OK\"}]"
                    .formatted(CONFIRM_REFERENCE),
                APPLICATION_JSON));

    client.approve(CONFIRM_REFERENCE, PDF);
  }

  @Test
  void aContainerSignedByAnotherPersonIsRejected() {
    server
        .expect(requestTo(CONSENT_API + "/third-party/container"))
        .andRespond(
            withSuccess(
                "[{\"consentConfirmReference\":\"%s\",\"status\":\"ERROR\",\"errorCode\":\"CONSENT_SIGNER_MISMATCH\"}]"
                    .formatted(CONFIRM_REFERENCE),
                APPLICATION_JSON));

    assertThatThrownBy(() -> client.approve(CONFIRM_REFERENCE, PDF))
        .isInstanceOf(ConsentRejectedException.class);
  }

  @Test
  void anInvalidSignatureIsRejected() {
    server
        .expect(requestTo(CONSENT_API + "/third-party/container"))
        .andRespond(
            withStatus(BAD_REQUEST)
                .contentType(APPLICATION_JSON)
                .body("{\"errorCode\":\"CONSENT_SIGNATURE_INVALID\"}"));

    assertThatThrownBy(() -> client.approve(CONFIRM_REFERENCE, PDF))
        .isInstanceOf(ConsentRejectedException.class);
  }

  @Test
  void aContainerTheConsentServiceFailedToProcessCanBeSentAgainLater() {
    server
        .expect(requestTo(CONSENT_API + "/third-party/container"))
        .andRespond(
            withSuccess(
                "[{\"consentConfirmReference\":\"%s\",\"status\":\"ERROR\",\"errorCode\":\"CONSENT_SIGNED_CONTAINER_PROCESSING_FAILED\"}]"
                    .formatted(CONFIRM_REFERENCE),
                APPLICATION_JSON));

    assertThatThrownBy(() -> client.approve(CONFIRM_REFERENCE, PDF))
        .isInstanceOf(ConsentServiceUnavailableException.class);
  }

  @Test
  void approvalIsNotRetriedSinceTheConsentServiceMayHaveApprovedItAlready() {
    server
        .expect(requestTo(CONSENT_API + "/third-party/container"))
        .andRespond(withStatus(INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> client.approve(CONFIRM_REFERENCE, PDF))
        .isInstanceOf(ConsentServiceUnavailableException.class);
  }

  @Test
  void findsTheReferenceOfAnApprovedConsent() {
    server
        .expect(requestTo(CONSENT_API + "/reference"))
        .andExpect(method(POST))
        .andExpect(
            content()
                .json(
                    "{\"idCode\":\"38888888888\",\"purposeDeclarationBusinessIdentifiers\":[\"isiku_maksuandmed\"]}",
                    STRICT))
        .andRespond(
            withSuccess("{\"isiku_maksuandmed\":\"consent-reference-1\"}", APPLICATION_JSON));

    assertThat(client.findReference("38888888888")).contains("consent-reference-1");
  }

  @Test
  void findsNoReferenceOnceTheConsentIsWithdrawnOrExpired() {
    server
        .expect(requestTo(CONSENT_API + "/reference"))
        .andRespond(
            withStatus(NOT_FOUND)
                .contentType(APPLICATION_JSON)
                .body("{\"errorCode\":\"HTTP_NOT_FOUND\"}"));

    assertThat(client.findReference("38888888888")).isEmpty();
  }

  @Test
  void aNotFoundFromSomethingOtherThanTheConsentServiceIsNotTakenAsAWithdrawal() {
    server.expect(requestTo(CONSENT_API + "/reference")).andRespond(withStatus(NOT_FOUND));
    server.expect(requestTo(CONSENT_API + "/reference")).andRespond(withStatus(NOT_FOUND));

    assertThatThrownBy(() -> client.findReference("38888888888"))
        .isInstanceOf(ConsentServiceUnavailableException.class);
  }

  @Test
  void looksForTheReferenceOnceMoreWhenTheConsentServiceFailsAndThenGivesUp() {
    server
        .expect(requestTo(CONSENT_API + "/reference"))
        .andRespond(withStatus(INTERNAL_SERVER_ERROR));
    server
        .expect(requestTo(CONSENT_API + "/reference"))
        .andRespond(withStatus(INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> client.findReference("38888888888"))
        .isInstanceOf(ConsentServiceUnavailableException.class);
  }

  private RestConsentServiceClient client(TaxHeadroomProperties properties) {
    var configuration = new ConsentServiceConfiguration();
    var builder = configuration.consentServiceRestClient(RestClient.builder(), properties).mutate();
    server = MockRestServiceServer.bindTo(builder).build();
    return new RestConsentServiceClient(builder.build(), properties);
  }

  private static TaxHeadroomProperties properties(String purposeDeclarationId) {
    return new TaxHeadroomProperties(
        "http://xroad.test",
        "ee-dev/COM/14118923/tuleva-fund-management",
        "ee-dev/GOV/70000349/mkrliides/TaxCertificate/v2",
        "ee-dev/GOV/70006317/consent/consent-stage",
        purposeDeclarationId);
  }
}
