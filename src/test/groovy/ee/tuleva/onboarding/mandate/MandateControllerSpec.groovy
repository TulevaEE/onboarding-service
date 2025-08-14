package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.generic.GenericMandateService
import ee.tuleva.onboarding.signature.SignatureFile
import ee.tuleva.onboarding.signature.idcard.IdCardSignatureSession
import ee.tuleva.onboarding.signature.mobileid.MobileIdSignatureSession
import ee.tuleva.onboarding.signature.smartid.SmartIdSignatureSession
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.web.servlet.LocaleResolver

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER
import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static java.util.Locale.ENGLISH
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class MandateControllerSpec extends BaseControllerSpec {

  MandateRepository mandateRepository = Mock(MandateRepository)
  MandateService mandateService = Mock(MandateService)
  GenericSessionStore sessionStore = Mock(GenericSessionStore)
  SignatureFileArchiver signatureFileArchiver = Mock(SignatureFileArchiver)
  MandateFileService mandateFileService = Mock(MandateFileService)
  LocaleResolver localeResolver = Mock(LocaleResolver)
  GenericMandateService genericMandateService = Mock(GenericMandateService)

  MandateController controller =
      new MandateController(mandateRepository, mandateService, genericMandateService, sessionStore, signatureFileArchiver, mandateFileService,
          localeResolver)
  AuthenticatedPerson authenticatedPerson = AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember().build()
  MockMvc mvc = mockMvcWithAuthenticationPrincipal(authenticatedPerson, controller)

  def "save a mandate"() {
    when:
    def mandate = sampleMandate()
    mandateService.save(_ as AuthenticatedPerson, _ as CreateMandateCommand) >> mandate
    then:
    mvc
        .perform(post("/v1/mandates")
            .content(mapper.writeValueAsString(sampleCreateMandateCommand()))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.futureContributionFundIsin', is(mandate.futureContributionFundIsin.get())))
        .andExpect(jsonPath('$.pillar', is(mandate.pillar)))
        .andExpect(jsonPath('$.address.countryCode', is(mandate.address.countryCode)))
        .andExpect(
            jsonPath('$.fundTransferExchanges[0].sourceFundIsin', is(mandate.fundTransferExchanges[0].sourceFundIsin)))
        .andExpect(
            jsonPath('$.fundTransferExchanges[0].targetFundIsin', is(mandate.fundTransferExchanges[0].targetFundIsin)))
        .andExpect(
            jsonPath('$.fundTransferExchanges[0].amount', is(mandate.fundTransferExchanges[0].amount.doubleValue())))
  }

  def "mobile id signature start returns the mobile id challenge code"() {
    when:
    mandateService.mobileIdSign(1L, authenticatedPerson.getUserId(), authenticatedPerson.getAttribute(PHONE_NUMBER)) >>
        MobileIdSignatureSession.builder().verificationCode("1234").build()

    then:
    mvc
        .perform(put("/v1/mandates/1/signature/mobileId")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.challengeCode', is("1234")))
  }

  def "get mobile ID signature status returns the status and challenge code"() {
    when:
    def session = MobileIdSignatureSession.builder().verificationCode("1234").build()
    sessionStore.get(MobileIdSignatureSession) >> Optional.of(session)
    localeResolver.resolveLocale(_) >> ENGLISH
    mandateService.finalizeMobileIdSignature(_ as Long, 1L, session, ENGLISH) >> "SIGNATURE"

    then:
    mvc
        .perform(get("/v1/mandates/1/signature/mobileId/status"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.statusCode', is("SIGNATURE")))
        .andExpect(jsonPath('$.challengeCode', is("1234")))
  }

  def "smart id signature start returns null challenge code"() {
    when:
    def session = new SmartIdSignatureSession("certSessionId", "personalCode", [])
    1 * mandateService.smartIdSign(1L, _) >> session
    1 * sessionStore.save(session)

    then:
    mvc
        .perform(put("/v1/mandates/1/signature/smartId")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.challengeCode', is(null)))
  }

  def "get smart id signature status returns the status and challenge code"() {
    when:
    def session = new SmartIdSignatureSession("certSessionId", "personalCode", [])
    session.verificationCode = "1234"
    1 * sessionStore.get(SmartIdSignatureSession) >> Optional.of(session)
    1 * localeResolver.resolveLocale(_) >> ENGLISH
    1 * mandateService.finalizeSmartIdSignature(_, 1L, session, ENGLISH) >> "SIGNATURE"

    then:
    mvc
        .perform(get("/v1/mandates/1/signature/smartId/status"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.statusCode', is("SIGNATURE")))
        .andExpect(jsonPath('$.challengeCode', is("1234")))
  }

  def "id card signature start returns the hash to be signed by the client"() {
    when:
    mandateService.idCardSign(1L, _, "clientCertificate") >>
        IdCardSignatureSession.builder().hashToSignInHex("asdfg").build()

    then:
    mvc
        .perform(put("/v1/mandates/1/signature/idCard")
            .content(mapper.writeValueAsString(sampleStartIdCardSignCommand("clientCertificate")))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.hash', is("asdfg")))
  }

  def "put ID card signature status returns the status code"() {
    when:
    def session = IdCardSignatureSession.builder().build()
    sessionStore.get(IdCardSignatureSession) >> Optional.of(session)
    localeResolver.resolveLocale(_) >> ENGLISH
    mandateService.finalizeIdCardSignature(_ as Long, 1L, session, "signedHash", ENGLISH) >> "SIGNATURE"

    then:
    mvc
        .perform(put("/v1/mandates/1/signature/idCard/status")
            .content(mapper.writeValueAsString(sampleFinishIdCardSignCommand("signedHash")))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.statusCode', is("SIGNATURE")))
  }

  def "getMandateFile returns mandate file"() {
    when:
    1 * mandateRepository
        .findByIdAndUserId(sampleMandate().id, _ as Long) >> sampleMandate()

    then:
    MvcResult result = mvc
        .perform(get("/v1/mandates/" + sampleMandate().id + "/file"))
        .andExpect(status().isOk())
        .andReturn()

    result.getResponse().getHeader("Content-Disposition") == "attachment; filename=Tuleva_avaldus.bdoc"
  }

  def "getMandateFile throws exception if mandate is not signed"() {
    given:
    1 * mandateRepository
        .findByIdAndUserId(sampleMandate().id, _ as Long) >> sampleUnsignedMandate()

    when:
    mvc
        .perform(get("/v1/mandates/" + sampleMandate().id + "/file"))

    then:
    thrown Exception
  }

  def "getMandateFilePreview: returns mandate preview file"() {
    when:

    List<SignatureFile> files = [new SignatureFile("filename", "text/html", "content".getBytes())]

    1 * mandateFileService.getMandateFiles(sampleMandate().id, _ as Long) >> files
    1 * signatureFileArchiver.writeSignatureFilesToZipOutputStream(files, _ as OutputStream)

    then:
    MvcResult result = mvc
        .perform(get("/v1/mandates/" + sampleMandate().id + "/file/preview"))
        .andExpect(status().isOk())
        .andReturn()

    result.getResponse().getHeader("Content-Disposition") == "attachment; filename=Tuleva_avaldus.zip"

  }

  def "getMandateFile returns not found on non existing mandate file"() {
    when:
    1 * mandateRepository
        .findByIdAndUserId(sampleMandate().id, _ as Long) >> null

    then:
    mvc
        .perform(get("/v1/mandates/" + sampleMandate().id + "/file"))
        .andExpect(status().isNotFound())
  }
}
