package ee.tuleva.onboarding.payment.recurring


import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.PaymentData
import ee.tuleva.onboarding.payment.PaymentDateProvider
import ee.tuleva.onboarding.payment.PrefilledLink
import ee.tuleva.onboarding.payment.RedirectLink
import org.springframework.context.i18n.LocaleContextHolder
import spock.lang.Specification

import java.util.Locale

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.config.JsonMapperFixture.jsonMapper
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.contact.ContactDetailsServiceStub.stubContactDetailsService
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.*
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.RECURRING
import static ee.tuleva.onboarding.payment.recurring.ThirdPillarRecipientConfigurationFixture.thirdPillarRecipientConfiguration
import static ee.tuleva.onboarding.time.TestClockHolder.clock

class RecurringPaymentLinkGeneratorSpec extends Specification {

  def contactDetailsService = stubContactDetailsService()
  def paymentDateProvider = new PaymentDateProvider(clock)
  def objectMapper = jsonMapper()
  def localeService = new LocaleService()
  def thirdPillarConfig = thirdPillarRecipientConfiguration()
  def coopPankPaymentLinkGenerator = new CoopPankPaymentLinkGenerator(contactDetailsService, objectMapper, localeService, paymentDateProvider, thirdPillarConfig)
  def recurringPaymentLinkGenerator = new RecurringPaymentLinkGenerator(contactDetailsService, paymentDateProvider, coopPankPaymentLinkGenerator, thirdPillarConfig)

  def setup() {
    LocaleContextHolder.setLocale(Locale.ENGLISH)
  }

  def cleanup() {
    LocaleContextHolder.resetLocaleContext()
  }

  def "can get a recurring payment link"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, 12.34, EUR, RECURRING, paymentChannel)

    when:
    def link = recurringPaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    link.url() == url
    expectedType.isInstance(link)

    where:
    paymentChannel | expectedType  | url
    SWEDBANK       | RedirectLink  | "https://www.swedbank.ee/private/pensions/pillar3/orderp3p"
    LHV            | PrefilledLink | "https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add?i_receiver_name=AS%20Pensionikeskus" +
        "&i_receiver_account_no=EE547700771002908125&i_payment_desc=30101119828%2c%20EE3600001707&i_payment_clirefno=993432432" +
        "&i_amount=12.34&i_currency_id=38&i_interval_type=K&i_date_first_payment=10.01.2020"
    SEB            | RedirectLink  | "https://e.seb.ee/web/ipank?act=PENSION3_STPAYM&saajakonto=EE141010220263146225&saajanimi=" +
        "AS%20Pensionikeskus&selgitus=30101119828%2C%20EE3600001707&viitenr=993432432&summa=12.34&alguskuup=10.01.2020&sagedus=M"
    LUMINOR        | RedirectLink  | "https://luminor.ee/auth/#/web/view/autopilot/newpayment"
    COOP           | PrefilledLink | "https://i.cooppank.ee/i/standing-orders/new?bname=AS%20Pensionikeskus&bacc=EE362200221067235244&amt=12.34&cur=EUR&desc=30101119828%2C%20EE3600001707&ref=993432432&date=10.01.2020&freq=2&lang=en"
    COOP_WEB       | PrefilledLink | "i/standing-orders/new?bname=AS%20Pensionikeskus&bacc=EE362200221067235244&amt=12.34&cur=EUR&desc=30101119828%2C%20EE3600001707&ref=993432432&date=10.01.2020&freq=2&lang=en"
    PARTNER        | PrefilledLink | """{"accountNumber":"EE362200221067235244","recipientName":"AS Pensionikeskus","amount":12.34,"currency":"EUR","description":"30101119828, EE3600001707","reference":"993432432","interval":"MONTHLY","firstPaymentDate":"2020-01-10"}"""
  }

  def "LHV recurring link populates recipient details for the prefilled card"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, 12.34, EUR, RECURRING, LHV)

    when:
    def link = recurringPaymentLinkGenerator.getPaymentLink(paymentData, person) as PrefilledLink

    then:
    link.recipientName() == "AS Pensionikeskus"
    link.recipientIban() == "EE547700771002908125"
    link.description() == "30101119828, EE3600001707"
    link.amount() == "12.34"
  }

  def "renders #channel amount as plain decimal even when BigDecimal would print scientific"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, new BigDecimal("1E2"), EUR, RECURRING, channel)

    when:
    def link = recurringPaymentLinkGenerator.getPaymentLink(paymentData, person) as PrefilledLink

    then:
    link.url().contains(urlFragment)
    !link.url().contains("1E+2")
    !link.url().contains("1E2")
    link.amount() == "100"

    where:
    channel  | urlFragment
    LHV      | "&i_amount=100"
    COOP     | "&amt=100"
    COOP_WEB | "&amt=100"
  }

  def "Coop recurring URL contains no legacy keys from the old platform"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, 12.34, EUR, RECURRING, channel)

    when:
    def link = recurringPaymentLinkGenerator.getPaymentLink(paymentData, person) as PrefilledLink

    then:
    def url = link.url()
    ["SaajaNimi", "SaajaKonto", "MaksePohjus", "ViiteNumber", "MakseSumma", "MuutMakseSumma", "MakseSagedus", "MakseEsimene", "whatform", "newpmt", "-eng"].each { legacy ->
      assert !url.contains(legacy), "URL should not contain legacy key '${legacy}': ${url}"
    }

    where:
    channel << [COOP, COOP_WEB]
  }

  def "renders SEB recurring amount as plain decimal even when BigDecimal would print scientific"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, new BigDecimal("1E2"), EUR, RECURRING, SEB)

    when:
    def link = recurringPaymentLinkGenerator.getPaymentLink(paymentData, person) as RedirectLink

    then:
    link.url().contains("&summa=100")
    !link.url().contains("1E+2")
    !link.url().contains("1E2")
  }

  def "PARTNER recurring serializes amount as plain decimal not scientific"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, new BigDecimal("1E2"), EUR, RECURRING, PARTNER)

    when:
    def link = recurringPaymentLinkGenerator.getPaymentLink(paymentData, person) as PrefilledLink

    then:
    link.url().contains('"amount":100')
    !link.url().contains("1E+2")
    !link.url().contains('"amount":"100"') // numeric, not stringified
  }

  def "accepts recurring #channel with amount exactly at MIN_AMOUNT"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, new BigDecimal("0.01"), EUR, RECURRING, channel)

    when:
    def link = recurringPaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    noExceptionThrown()
    link != null

    where:
    channel << [SEB, LHV, COOP, COOP_WEB, PARTNER]
  }

  def "rejects recurring payments for TULUNDUSUHISTU as 400"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, 12.34, EUR, RECURRING, TULUNDUSUHISTU)

    when:
    recurringPaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    def exception = thrown(ErrorsResponseException)
    exception.errorsResponse.errors[0].code == "payment.channel.not.supported"
  }

  def "rejects recurring #channel without amount as 400 instead of building null URL"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, null, EUR, RECURRING, channel)

    when:
    recurringPaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    def exception = thrown(ErrorsResponseException)
    exception.errorsResponse.errors[0].code == "payment.amount.required"

    where:
    channel << [SEB, LHV]
  }

  def "rejects recurring #channel with amount below MIN_AMOUNT as 400"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, amount, EUR, RECURRING, channel)

    when:
    recurringPaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    def exception = thrown(ErrorsResponseException)
    exception.errorsResponse.errors[0].code == "payment.amount.invalid"

    where:
    channel  | amount
    SEB      | 0.00
    SEB      | -1
    SEB      | 0.001
    LHV      | 0.00
    LHV      | -1
    LHV      | 0.001
    COOP     | 0.00
    COOP_WEB | -1
    PARTNER  | 0.001
  }

  def "rejects recurring #channel without amount as 400"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, null, EUR, RECURRING, channel)

    when:
    recurringPaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    def exception = thrown(ErrorsResponseException)
    exception.errorsResponse.errors[0].code == "payment.amount.required"

    where:
    channel << [COOP]
  }

  def "builds #channel recurring link without amount so the Coop form collects the sum"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, null, null, RECURRING, channel)

    when:
    def link = recurringPaymentLinkGenerator.getPaymentLink(paymentData, person) as PrefilledLink

    then:
    link.url() == url
    link.amount() == null

    where:
    channel  | url
    COOP_WEB | "i/standing-orders/new?bname=AS%20Pensionikeskus&bacc=EE362200221067235244&cur=EUR&desc=30101119828%2C%20EE3600001707&ref=993432432&date=10.01.2020&freq=2&lang=en"
    PARTNER  | """{"accountNumber":"EE362200221067235244","recipientName":"AS Pensionikeskus","amount":null,"currency":null,"description":"30101119828, EE3600001707","reference":"993432432","interval":"MONTHLY","firstPaymentDate":"2020-01-10"}"""
  }

  def "rejects recurring payment without payment channel as 400"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, 12.34, EUR, RECURRING, null)

    when:
    recurringPaymentLinkGenerator.getPaymentLink(paymentData, person)

    then:
    def exception = thrown(ErrorsResponseException)
    exception.errorsResponse.errors[0].code == "payment.channel.required"
  }
}
