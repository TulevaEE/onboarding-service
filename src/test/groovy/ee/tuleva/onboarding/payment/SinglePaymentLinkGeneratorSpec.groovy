package ee.tuleva.onboarding.payment

import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentLinkGenerator
import ee.tuleva.onboarding.payment.recurring.CoopPankPaymentLinkGenerator
import ee.tuleva.onboarding.payment.recurring.PaymentDateProvider
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.contact.ContactDetailsServiceStub.stubContactDetailsService
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP_WEB
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
import static ee.tuleva.onboarding.time.TestClockHolder.clock

class SinglePaymentLinkGeneratorSpec extends Specification {

  def contactDetailsService = stubContactDetailsService()
  def paymentDateProvider = new PaymentDateProvider(clock)
  def objectMapper = JsonMapper.builder().build()
  def localeService = new LocaleService()
  CoopPankPaymentLinkGenerator coopPankPaymentLinkGenerator =
      new CoopPankPaymentLinkGenerator(contactDetailsService, objectMapper, localeService, paymentDateProvider)
  MontonioPaymentLinkGenerator paymentProviderLinkGenerator = Mock()
  SinglePaymentLinkGenerator singlePaymentLinkGenerator =
      new SinglePaymentLinkGenerator(coopPankPaymentLinkGenerator, paymentProviderLinkGenerator)


  def "can get a single payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap { type = SINGLE }
    def link = new PaymentLink("https://single.payment.url")
    paymentProviderLinkGenerator.getPaymentLink(paymentData, person) >> link

    when:
    def returnedLink = singlePaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    returnedLink == link
  }

  def "can get a partner payment json"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap {
      paymentChannel = PARTNER
      amount = null
      currency = null
    }
    when:
    def returnedLink = singlePaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    returnedLink == new PaymentLink("""{"accountNumber":"EE362200221067235244","recipientName":"AS Pensionikeskus","amount":null,"currency":null,"description":"30101119828, EE3600001707","reference":"993432432","interval":"MONTHLY","firstPaymentDate":"2020-01-10"}""")
  }

  def "can get a coop web payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap {
      paymentChannel = COOP_WEB
      amount = null
      currency = null
    }

    when:
    def returnedLink = singlePaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    returnedLink == new PaymentLink("newpmt-eng?SaajaNimi=AS%20Pensionikeskus&SaajaKonto=EE362200221067235244&MaksePohjus=30101119828%2c%20EE3600001707&ViiteNumber=993432432")
  }

}
