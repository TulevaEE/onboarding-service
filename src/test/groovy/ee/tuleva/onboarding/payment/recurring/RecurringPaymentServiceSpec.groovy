package ee.tuleva.onboarding.payment.recurring


import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import ee.tuleva.onboarding.payment.PaymentData
import spock.lang.Specification

import java.time.LocalDate

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.contact.ContactDetailsServiceStub.stubContactDetailsService
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.*
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.RECURRING
import static ee.tuleva.onboarding.time.TestClockHolder.clock

class RecurringPaymentServiceSpec extends Specification {

  def contactDetailsService = stubContactDetailsService()
  def objectMapper = JsonMapper.builder()
      .addModule(new JavaTimeModule())
      .disable(WRITE_DATES_AS_TIMESTAMPS)
      .build()
  def recurringPaymentService = new RecurringPaymentService(contactDetailsService, objectMapper, clock)

  def "can get a recurring payment link"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, 12.34, EUR, RECURRING, paymentChannel)

    when:
    def link = recurringPaymentService.getPaymentLink(paymentData, person)

    then:
    link.url() == url

    where:
    paymentChannel | url
    SWEDBANK       | "https://www.swedbank.ee/private/pensions/pillar3/orderp3p"
    LHV            | "https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add?i_receiver_name=AS%20Pensionikeskus" +
        "&i_receiver_account_no=EE547700771002908125&i_payment_desc=30101119828%2c%20EE3600001707&i_payment_clirefno=993432432" +
        "&i_amount=12.34&i_currency_id=38&i_interval_type=K&i_date_first_payment=10.01.2020"
    SEB            | "https://e.seb.ee/web/ipank?act=PENSION3_STPAYM&saajakonto=EE141010220263146225&saajanimi=" +
        "AS%20Pensionikeskus&selgitus=30101119828%2C%20EE3600001707&viitenr=993432432&summa=12.34&alguskuup=10.01.2020&sagedus=M"
    LUMINOR        | "https://luminor.ee/auth/#/web/view/autopilot/newpayment"
    COOP           | "https://i.cooppank.ee/newpmt?whatform=PermPaymentNew&SaajaNimi=AS%20Pensionikeskus&SaajaKonto=EE362200221067235244&MakseSumma=12.34&MaksePohjus=30101119828%2c%20EE3600001707&ViiteNumber=993432432&MakseSagedus=3&MakseEsimene=10.01.2020"
    COOP_WEB       | "https://i.cooppank.ee/newpmt?whatform=PermPaymentNew&SaajaNimi=AS%20Pensionikeskus&SaajaKonto=EE362200221067235244&MakseSumma=12.34&MaksePohjus=30101119828%2c%20EE3600001707&ViiteNumber=993432432&MakseSagedus=3&MakseEsimene=10.01.2020"
    PARTNER        | """{"accountNumber":"EE362200221067235244","recipientName":"AS Pensionikeskus","amount":12.34,"currency":"EUR","description":"30101119828, EE3600001707","reference":"993432432","interval":"MONTHLY","firstPaymentDate":"2020-01-10"}"""
  }

  def "works with no amount and currency"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, null, null, RECURRING, PARTNER)

    when:
    def link = recurringPaymentService.getPaymentLink(paymentData, person)

    then:
    link.url() == """{"accountNumber":"EE362200221067235244","recipientName":"AS Pensionikeskus","amount":null,"currency":null,"description":"30101119828, EE3600001707","reference":"993432432","interval":"MONTHLY","firstPaymentDate":"2020-01-10"}"""
  }

  def "rejects recurring payments for TULUNDUSUHISTU"() {
    given:
    def person = samplePerson
    def paymentData = new PaymentData(samplePerson.personalCode, 12.34, EUR, RECURRING, TULUNDUSUHISTU)

    when:
    recurringPaymentService.getPaymentLink(paymentData, person)

    then:
    thrown(IllegalArgumentException)
  }

  def "chooses the 10th day of month for recurring payment"() {
    when:
    def date = recurringPaymentService.tenthDayOfMonth(now)
    def formattedDate = recurringPaymentService.format(date)
    then:
    formattedDate == expectedPaymentDate
    where:
    now                       | expectedPaymentDate
    LocalDate.of(2022, 3, 9)  | "10.03.2022"
    LocalDate.of(2022, 3, 10) | "10.03.2022"
    LocalDate.of(2022, 3, 11) | "10.04.2022"
  }
}
