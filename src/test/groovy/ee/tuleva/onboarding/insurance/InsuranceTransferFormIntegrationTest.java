package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.signature.smartid.SmartIdSigner;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class InsuranceTransferFormIntegrationTest {

  private static final String PENSION_ACCOUNT_NUMBER = "993432432";
  private static final String VERIFICATION_CODE = "4321";

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private InsuranceTransferFormRepository formRepository;
  @Autowired private DigiDocFacade digiDocFacade;
  @Autowired private Configuration digiDocConfiguration;

  @MockitoBean private EpisService episService;
  @MockitoBean private SmartIdSigner smartIdSigner;
  @MockitoSpyBean private EmailService emailService;

  private final List<User> createdUsers = new ArrayList<>();
  private User ownerUser;
  private Authentication owner;
  private Authentication someoneElse;

  @BeforeEach
  void setUp() {
    ownerUser = user("38001085718", "Jaan", "Õunapuu", "jaan@example.com");
    owner = authenticationOf(ownerUser);
    someoneElse = authenticationOf(user("39901019992", "Mari", "Maasikas", "mari@example.com"));
    var contactDetails = contactDetailsFixture();
    contactDetails.setPensionAccountNumber(PENSION_ACCOUNT_NUMBER);
    given(episService.getContactDetails(any())).willReturn(contactDetails);
  }

  @AfterEach
  void cleanUp() {
    formRepository.deleteAll();
    userRepository.deleteAll(createdUsers);
  }

  @Test
  void createsACompensaFormWithThePensionAccountNumberFrozenIntoIt() throws Exception {
    long id = createCompensaForm();

    assertThat(formRepository.findById(id))
        .get()
        .extracting(InsuranceTransferForm::getPensionAccountNumber)
        .isEqualTo(PENSION_ACCOUNT_NUMBER);
  }

  @Test
  void onlyTheOwnerCanPreviewTheFilledForm() throws Exception {
    long id = createCompensaForm();

    mvc.perform(get("/v1/insurance-transfer-forms/{id}/preview", id).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    mvc.perform(
            get("/v1/insurance-transfer-forms/{id}/preview", id).with(authentication(someoneElse)))
        .andExpect(status().isNotFound());
  }

  @Test
  void signingWithSmartIdEmailsTheSignedFormOnceAndLetsTheOwnerDownloadIt() throws Exception {
    long id = createCompensaForm();
    stubSmartIdSigningAroundARealContainer();
    willReturn(Optional.of(new MandrillMessageStatus()))
        .given(emailService)
        .send(any(), any(), any());

    var start =
        mvc.perform(
                put("/v1/insurance-transfer-forms/{id}/signature/smart-id", id)
                    .with(authentication(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.challengeCode").value(VERIFICATION_CODE))
            .andReturn();
    Cookie session = start.getResponse().getCookie("SESSION");
    pollSmartIdStatus(id, session);
    pollSmartIdStatus(id, session);

    then(emailService)
        .should()
        .send(
            any(),
            argThat(
                message ->
                    message.getTo().getFirst().getEmail().equals(ownerUser.getEmail())
                        && message
                            .getAttachments()
                            .getFirst()
                            .getName()
                            .equals("Tuleva_Compensa_avaldus.asice")),
            eq("insurance_transfer_form_et"));
    mvc.perform(get("/v1/insurance-transfer-forms/{id}", id).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SIGNED"))
        .andExpect(jsonPath("$.emailStatus").value("SENT"));

    var signedFile =
        mvc.perform(
                get("/v1/insurance-transfer-forms/{id}/signed-file", id)
                    .with(authentication(owner)))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"Tuleva_Compensa_avaldus.asice\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    Container container =
        ContainerBuilder.aContainer()
            .withConfiguration(digiDocConfiguration)
            .fromStream(new ByteArrayInputStream(signedFile))
            .build();
    assertThat(container.getDataFiles())
        .extracting(DataFile::getMediaType)
        .containsExactly("application/pdf");
    try (PDDocument pdf = Loader.loadPDF(container.getDataFiles().getFirst().getBytes())) {
      assertThat(new PDFTextStripper().getText(pdf))
          .contains(
              ownerUser.getFirstName() + " " + ownerUser.getLastName(),
              ownerUser.getPersonalCode(),
              "UP000123",
              "Tuleva III Samba Pensionifond",
              "EE3600001707",
              PENSION_ACCOUNT_NUMBER);
      assertThat(pdf.getDocumentCatalog().getAcroForm()).isNull();
    }
  }

  private long createCompensaForm() throws Exception {
    var response =
        mvc.perform(
                post("/v1/insurance-transfer-forms")
                    .with(authentication(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "insurer": "COMPENSA",
                          "policyNumber": " UP000123 ",
                          "endDate": null,
                          "address": {
                            "street": "Tamme tn 1-2",
                            "city": "Tallinn",
                            "postalCode": "10111",
                            "countryCode": "EE"
                          },
                          "usPerson": false
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.insurer").value("COMPENSA"))
            .andExpect(jsonPath("$.insurerEmail").value("info@compensalife.ee"))
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.emailStatus").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(response).get("id").asLong();
  }

  private void pollSmartIdStatus(long id, Cookie session) throws Exception {
    mvc.perform(
            get("/v1/insurance-transfer-forms/{id}/signature/smart-id/status", id)
                .cookie(session)
                .with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode").value("SIGNATURE"));
  }

  private void stubSmartIdSigningAroundARealContainer() {
    given(smartIdSigner.startSign(any(), any()))
        .willAnswer(
            invocation -> {
              List<SignatureFile> files = invocation.getArgument(0);
              AuthenticatedPerson signer = invocation.getArgument(1);
              var session = new SmartIdSignatureSession(signer.getPersonalCode(), files);
              session.setVerificationCode(VERIFICATION_CODE);
              return session;
            });
    given(smartIdSigner.getSignedFile(any()))
        .willAnswer(
            invocation -> {
              SmartIdSignatureSession session = invocation.getArgument(0);
              return IOUtils.toByteArray(
                  digiDocFacade.buildContainer(session.getFiles()).saveAsStream());
            });
  }

  private User user(String personalCode, String firstName, String lastName, String email) {
    return userRepository
        .findByPersonalCode(personalCode)
        .orElseGet(
            () -> {
              User user =
                  userRepository.save(
                      User.builder()
                          .personalCode(personalCode)
                          .firstName(firstName)
                          .lastName(lastName)
                          .email(email)
                          .phoneNumber("+37255555555")
                          .active(true)
                          .build());
              createdUsers.add(user);
              return user;
            });
  }

  private static Authentication authenticationOf(User user) {
    var person =
        AuthenticatedPerson.builder()
            .userId(user.getId())
            .personalCode(user.getPersonalCode())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .build();
    return new UsernamePasswordAuthenticationToken(
        person, null, List.of(new SimpleGrantedAuthority(USER)));
  }
}
