package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.generic.GenericMandateService
import ee.tuleva.onboarding.signature.SignatureFile
import ee.tuleva.onboarding.signature.IdCardSignatureSession
import ee.tuleva.onboarding.signature.MobileIdSignatureSession
import ee.tuleva.onboarding.signature.SmartIdSignatureSession
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER
import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE
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
  LocaleService localeService = Mock(LocaleService)
  GenericMandateService genericMandateService = Mock(GenericMandateService)

  MandateController controller =
      new MandateController(mandateRepository, mandateService, genericMandateService, sessionStore, signatureFileArchiver, mandateFileService,
          localeService)
  AuthenticatedPerson authenticatedPerson = AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember()
      .attributes(Map.of(PHONE_NUMBER, "5555555"))
      .build()
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
    def session = MobileIdSignatureSession.builder().verificationCode("1234").build()
    mandateService.mobileIdSign(1L, authenticatedPerson.getUserId(), authenticatedPerson.getAttribute(PHONE_NUMBER)) >>
        session
    1 * sessionStore.save(session)

    then:
    mvc
        .perform(put("/v1/mandates/1/signature/mobile-id")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.challengeCode', is("1234")))
  }

  def "get mobile ID signature status returns the status and challenge code"() {
    when:
    def session = MobileIdSignatureSession.builder().verificationCode("1234").build()
    sessionStore.get(MobileIdSignatureSession) >> Optional.of(session)
    localeService.getCurrentLocale() >> ENGLISH
    mandateService.finalizeMobileIdSignature(_ as Long, 1L, session, ENGLISH) >> "SIGNATURE"

    then:
    mvc
        .perform(get("/v1/mandates/1/signature/mobile-id/status"))
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
        .perform(put("/v1/mandates/1/signature/smart-id")
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
    1 * localeService.getCurrentLocale() >> ENGLISH
    1 * mandateService.finalizeSmartIdSignature(_, 1L, session, ENGLISH) >> "SIGNATURE"

    then:
    mvc
        .perform(get("/v1/mandates/1/signature/smart-id/status"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.statusCode', is("SIGNATURE")))
        .andExpect(jsonPath('$.challengeCode', is("1234")))
  }

  def "id card signature start returns the hash to sign and its hash function"() {
    when:
    def session = IdCardSignatureSession.builder().hashToSign("asdfg").hashFunction("SHA-256").build()
    mandateService.idCardSign(1L, _, "certificate") >> session
    1 * sessionStore.save(session)

    then:
    mvc
        .perform(put("/v1/mandates/1/signature/id-card")
            .content(mapper.writeValueAsString(sampleStartIdCardSignCommand("certificate")))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.hash', is("asdfg")))
        .andExpect(jsonPath('$.hashFunction', is("SHA-256")))
  }

  def "persisting the id card signature returns the processing status"() {
    when:
    def session = IdCardSignatureSession.builder().build()
    sessionStore.get(IdCardSignatureSession) >> Optional.of(session)
    mandateService.persistIdCardSignature(_ as Long, 1L, session, "signature") >> OUTSTANDING_TRANSACTION

    then:
    mvc
        .perform(put("/v1/mandates/1/signature/id-card/signature")
            .content(mapper.writeValueAsString(sampleFinishIdCardSignCommand("signature")))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.statusCode', is("OUTSTANDING_TRANSACTION")))
  }

  def "id card signature status returns the processing status"() {
    when:
    localeService.getCurrentLocale() >> ENGLISH
    mandateService.getIdCardSignatureStatus(_ as Long, 1L, ENGLISH) >> SIGNATURE

    then:
    mvc
        .perform(get("/v1/mandates/1/signature/id-card/status"))
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
    result.getResponse().committed
  }

  def "getMandateFile throws exception if mandate is not signed"() {
    given:
    1 * mandateRepository
        .findByIdAndUserId(sampleMandate().id, _ as Long) >> sampleUnsignedMandate()

    when:
    mvc
        .perform(get("/v1/mandates/" + sampleMandate().id + "/file"))

    then:
    def exception = thrown(Exception)
    exception.cause.class == RuntimeException
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
    result.getResponse().committed

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
