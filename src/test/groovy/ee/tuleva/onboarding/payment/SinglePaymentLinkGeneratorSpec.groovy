package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentLinkGenerator
import ee.tuleva.onboarding.payment.recurring.CoopPankPaymentLinkGenerator
import ee.tuleva.onboarding.payment.PaymentDateProvider
import org.springframework.context.i18n.LocaleContextHolder
import spock.lang.Specification

import java.util.Locale

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.config.JsonMapperFixture.jsonMapper
import static ee.tuleva.onboarding.epis.contact.ContactDetailsServiceStub.stubContactDetailsService
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP_WEB
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
import static ee.tuleva.onboarding.payment.recurring.ThirdPillarRecipientConfigurationFixture.thirdPillarRecipientConfiguration
import static ee.tuleva.onboarding.time.TestClockHolder.clock

class SinglePaymentLinkGeneratorSpec extends Specification {

  def contactDetailsService = stubContactDetailsService()
  def paymentDateProvider = new PaymentDateProvider(clock)
  def objectMapper = jsonMapper()
  def localeService = new LocaleService()
  def thirdPillarConfig = thirdPillarRecipientConfiguration()
  CoopPankPaymentLinkGenerator coopPankPaymentLinkGenerator =
      new CoopPankPaymentLinkGenerator(contactDetailsService, objectMapper, localeService, paymentDateProvider, thirdPillarConfig)
  MontonioPaymentLinkGenerator paymentProviderLinkGenerator = Mock()
  SinglePaymentLinkGenerator singlePaymentLinkGenerator =
      new SinglePaymentLinkGenerator(coopPankPaymentLinkGenerator, paymentProviderLinkGenerator)

  def setup() {
    LocaleContextHolder.setLocale(Locale.ENGLISH)
  }

  def cleanup() {
    LocaleContextHolder.resetLocaleContext()
  }


  def "can get a single payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap { type = SINGLE }
    def link = new RedirectLink("https://single.payment.url")
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
    returnedLink instanceof PrefilledLink
    returnedLink.url() == """{"accountNumber":"EE362200221067235244","recipientName":"AS Pensionikeskus","amount":null,"currency":null,"description":"30101119828, EE3600001707","reference":"993432432","interval":"MONTHLY","firstPaymentDate":"2020-01-10"}"""
    (returnedLink as PrefilledLink).recipientName() == "AS Pensionikeskus"
    (returnedLink as PrefilledLink).recipientIban() == "EE362200221067235244"
    (returnedLink as PrefilledLink).description() == "30101119828, EE3600001707"
    (returnedLink as PrefilledLink).amount() == null
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
    returnedLink instanceof PrefilledLink
    def url = returnedLink.url()
    url == "i/payments/new?bname=AS%20Pensionikeskus&bacc=EE362200221067235244&cur=EUR&desc=30101119828%2C%20EE3600001707&ref=993432432&lang=en"
    ["SaajaNimi", "SaajaKonto", "MaksePohjus", "ViiteNumber", "MakseSumma", "MuutMakseSumma", "whatform", "newpmt", "-eng"].each { legacy ->
      assert !url.contains(legacy), "URL should not contain legacy key '${legacy}': ${url}"
    }
    (returnedLink as PrefilledLink).recipientName() == "AS Pensionikeskus"
    (returnedLink as PrefilledLink).recipientIban() == "EE362200221067235244"
    (returnedLink as PrefilledLink).description() == "30101119828, EE3600001707"
    (returnedLink as PrefilledLink).amount() == null
  }

  def "rejects single payment without payment channel as 400"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap {
      type = SINGLE
      paymentChannel = null
    }

    when:
    singlePaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    def exception = thrown(ErrorsResponseException)
    exception.errorsResponse.errors[0].code == "payment.channel.required"
  }

}
