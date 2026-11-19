package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.RECENT_MONTHS;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeSource.CORRECTED;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeSource.ESTIMATED;
import static ee.tuleva.onboarding.taxwin.PaymentSuggestion.SINGLE;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.ws.test.client.RequestMatchers.soapHeader;
import static org.springframework.ws.test.client.RequestMatchers.xpath;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.conversion.ThirdPillarContributionsThisYear;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.taxwin.consent.ConsentServiceConfiguration;
import ee.tuleva.onboarding.taxwin.consent.RestConsentServiceClient;
import ee.tuleva.onboarding.taxwin.income.SoapTaxCertificateClient;
import ee.tuleva.onboarding.taxwin.income.TaxDataConfiguration;
import ee.tuleva.onboarding.time.MutableClock;
import ee.tuleva.onboarding.user.User;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.ws.test.client.MockWebServiceServer;

@DataJpaTest
class TaxHeadroomFlowTest {

  private static final String PERSONAL_CODE = "38001085718";
  private static final String CLIENT_ID = "ee-dev/COM/14118923/tuleva-fund-management";
  private static final String CONSENT_SERVICE = "ee-dev/GOV/70006317/consent/consent-stage";
  private static final String CONSENT_API =
      "http://xroad.test/r1/" + CONSENT_SERVICE + "/api/consent";
  private static final String CONFIRM_REFERENCE = "5b1c4f2e-7d3a-4a1e-9c2f-0d8e6b7a1c3d";
  private static final String CONSENT_REFERENCE = "consent-reference-1";
  private static final byte[] CONSENT_PDF = "%PDF-1.7 Nousolek".getBytes();
  private static final byte[] SIGNED_CONTAINER = "signed asice".getBytes();
  private static final String XROAD_NAMESPACE = "http://x-road.eu/xsd/xroad.xsd";

  private static final TaxHeadroomProperties PROPERTIES =
      new TaxHeadroomProperties(
          "http://xroad.test",
          CLIENT_ID,
          "ee-dev/GOV/70000349/mkrliides/TaxCertificate/v2",
          CONSENT_SERVICE,
          "isiku_maksuandmed");

  @Autowired private TestEntityManager entityManager;
  @Autowired private TaxDataConsentRepository consentRepository;
  @Autowired private TaxIncomeMonthRepository incomeMonthRepository;
  @Autowired private TaxIncomeCorrectionRepository correctionRepository;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));
  private final SignatureService signatureService = mock(SignatureService.class);
  private final GenericSessionStore sessionStore = mock(GenericSessionStore.class);
  private final ThirdPillarContributionsThisYear contributionsThisYear =
      mock(ThirdPillarContributionsThisYear.class);

  private MockRestServiceServer consentServer;
  private MockWebServiceServer taxDataServer;
  private TaxDataConsentService consentService;
  private TaxDataConsentSignatureService signingService;
  private TaxHeadroomService headroomService;
  private AuthenticatedPerson person;

  @BeforeEach
  void setUp() {
    var consentConfiguration = new ConsentServiceConfiguration();
    var restClientBuilder =
        consentConfiguration.consentServiceRestClient(RestClient.builder(), PROPERTIES).mutate();
    consentServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    var consentClient = new RestConsentServiceClient(restClientBuilder.build(), PROPERTIES);

    var taxDataConfiguration = new TaxDataConfiguration();
    var webServiceTemplate = taxDataConfiguration.taxDataWebServiceTemplate(PROPERTIES);
    taxDataServer = MockWebServiceServer.createServer(webServiceTemplate);
    var taxDataClient = new SoapTaxCertificateClient(webServiceTemplate, PROPERTIES);

    var incomeStore = new TaxIncomeStore(incomeMonthRepository, correctionRepository);
    consentService =
        new TaxDataConsentService(consentRepository, consentClient, incomeStore, clock);
    signingService =
        new TaxDataConsentSignatureService(consentService, signatureService, sessionStore);
    headroomService =
        new TaxHeadroomService(
            consentRepository,
            incomeStore,
            taxDataClient,
            consentClient,
            contributionsThisYear,
            clock);

    person = authenticatedPersonFromUser(user()).build();
    given(contributionsThisYear.forPerson(person)).willReturn(new BigDecimal("3260.00"));
  }

  @AfterEach
  void verifyAllRemoteCallsWereMade() {
    consentServer.verify();
    taxDataServer.verify();
  }

  @Test
  void personWhoSignsTheConsentSeesHowMuchMoreTheyCanPutIntoTheirThirdPillarWithATaxWin()
      throws IOException {
    expectConsentDocument();
    expectNoConsentReferenceYet();
    expectSignedContainerApproved();
    expectConsentReference();
    expectConsentReference();
    expectTaxCertificate();
    Long id = consentService.create(person, Locale.of("et")).id();

    var signable = new SignableEntity("Tax data consent", id);
    var session = new SmartIdSignatureSession(signable, PERSONAL_CODE, List.of());
    given(
            signatureService.startSmartIdSign(
                eq(signable),
                argThat(
                    files ->
                        files.size() == 1
                            && files.getFirst().getName().equals("Nousolek.pdf")
                            && files.getFirst().getMimeType().equals("application/pdf")
                            && Arrays.equals(files.getFirst().getContent(), CONSENT_PDF)),
                eq(person)))
        .willReturn(session);
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(session));
    given(signatureService.getSignedFile(session, signable)).willReturn(SIGNED_CONTAINER);

    signingService.startSmartIdSignature(id, person);
    assertThat(signingService.getSmartIdSignatureStatus(id, person).getStatusCode())
        .isEqualTo(SIGNATURE);

    assertThat(headroomService.headroom(person))
        .get()
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
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
                SINGLE));

    assertThat(
            headroomService.correctExpectedIncome(
                person, YearMonth.of(2026, 12), new BigDecimal("12500")))
        .get()
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new TaxHeadroomResponse(
                2026,
                new BigDecimal("25000.00"),
                YearMonth.of(2026, 10),
                List.of(
                    new ExpectedMonthlyIncome(
                        YearMonth.of(2026, 11), new BigDecimal("2500.00"), ESTIMATED),
                    new ExpectedMonthlyIncome(
                        YearMonth.of(2026, 12), new BigDecimal("12500.00"), CORRECTED)),
                RECENT_MONTHS,
                new BigDecimal("3260.00"),
                2,
                6000,
                LimitCertainty.ESTIMATED,
                2740,
                602,
                500,
                SINGLE));
  }

  @Test
  void withdrawingTheConsentAtEestiEeRemovesTheIncomeAndTheHeadroom() throws IOException {
    expectConsentDocument();
    expectNoConsentReferenceYet();
    expectSignedContainerApproved();
    expectConsentReference();
    expectConsentReference();
    expectTaxCertificate();
    expectNoConsentReferenceYet();
    Long id = consentService.create(person, Locale.of("et")).id();
    consentService.markSigned(id, person, SIGNED_CONTAINER);
    consentService.completeApproval(id, person);
    assertThat(headroomService.headroom(person)).isPresent();

    clock.tick(25, HOURS);

    assertThat(headroomService.headroom(person)).isEmpty();
    assertThat(incomeMonthRepository.findAllByUserId(person.getUserIdOrThrow())).isEmpty();
    assertThat(correctionRepository.findAllByUserId(person.getUserIdOrThrow())).isEmpty();
  }

  private void expectConsentDocument() {
    consentServer
        .expect(requestTo(CONSENT_API + "/third-party"))
        .andExpect(method(POST))
        .andExpect(header("X-Road-Client", CLIENT_ID))
        .andExpect(jsonPath("$.idCode").value(PERSONAL_CODE))
        .andExpect(
            jsonPath("$.purposeDeclarationBusinessIdentifiers[0]").value("isiku_maksuandmed"))
        .andExpect(jsonPath("$.language").value("et"))
        .andExpect(jsonPath("$.fileType").value("GENERATED_PDF"))
        .andRespond(
            withSuccess(
                """
                [{"consentConfirmReference":"%s","idCode":"%s","validFrom":"2026-11-18",\
                "validTo":"2027-11-18","files":[{"fileType":"GENERATED_PDF","fileContent":"%s"}]}]
                """
                    .formatted(CONFIRM_REFERENCE, PERSONAL_CODE, base64(CONSENT_PDF)),
                APPLICATION_JSON));
  }

  private void expectNoConsentReferenceYet() {
    consentServer
        .expect(requestTo(CONSENT_API + "/reference"))
        .andExpect(method(POST))
        .andRespond(
            withStatus(NOT_FOUND)
                .contentType(APPLICATION_JSON)
                .body("{\"errorCode\":\"HTTP_NOT_FOUND\"}"));
  }

  private void expectSignedContainerApproved() {
    consentServer
        .expect(requestTo(CONSENT_API + "/third-party/container"))
        .andExpect(method(POST))
        .andExpect(jsonPath("$[0].consentConfirmReference").value(CONFIRM_REFERENCE))
        .andExpect(jsonPath("$[0].file").value(base64(SIGNED_CONTAINER)))
        .andRespond(
            withSuccess(
                "[{\"consentConfirmReference\":\"%s\",\"status\":\"OK\"}]"
                    .formatted(CONFIRM_REFERENCE),
                APPLICATION_JSON));
  }

  private void expectConsentReference() {
    consentServer
        .expect(requestTo(CONSENT_API + "/reference"))
        .andExpect(method(POST))
        .andExpect(jsonPath("$.idCode").value(PERSONAL_CODE))
        .andRespond(
            withSuccess(
                "{\"isiku_maksuandmed\":\"%s\"}".formatted(CONSENT_REFERENCE), APPLICATION_JSON));
  }

  private void expectTaxCertificate() throws IOException {
    taxDataServer
        .expect(soapHeader(new QName(XROAD_NAMESPACE, "client")))
        .andExpect(soapHeader(new QName(XROAD_NAMESPACE, "service")))
        .andExpect(xpath("//code").evaluatesTo(PERSONAL_CODE))
        .andExpect(xpath("//consentReference").evaluatesTo(CONSENT_REFERENCE))
        .andExpect(xpath("//periodStart").evaluatesTo("11.2025"))
        .andExpect(xpath("//periodEnd").evaluatesTo("10.2026"))
        .andRespond(withPayload(new ClassPathResource("taxwin/tax-certificate-response.xml")));
  }

  private User user() {
    User user = new User();
    user.setPersonalCode(PERSONAL_CODE);
    user.setFirstName("Näidis");
    user.setLastName("Isik");
    return entityManager.persist(user);
  }

  private static String base64(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }
}
