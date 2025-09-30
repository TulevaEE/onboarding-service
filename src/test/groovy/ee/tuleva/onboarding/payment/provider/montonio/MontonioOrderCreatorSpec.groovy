package ee.tuleva.onboarding.payment.provider.montonio

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.PaymentData
import ee.tuleva.onboarding.payment.PaymentFixture
import ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Clock

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewMemberPaymentData
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData

class MontonioOrderCreatorSpec extends Specification {

  MontonioOrderCreator montonioOrderCreator
  Clock clock = TestClockHolder.clock
  PaymentInternalReferenceService paymentInternalReferenceService = Mock()
  MontonioPaymentChannelConfiguration montonioPaymentChannelConfiguration = Mock()
  LocaleService localeService = Mock()

  Boolean useFakeNotificationsUrl = false
  String apiUrl = "https://api.example.com"
  BigDecimal memberFee = new BigDecimal("10.00")
  String memberFeeTestPersonalCode = "12345678901"

  Person aPerson = sampleAuthenticatedPersonAndMember().build()

  void setup() {
    montonioOrderCreator = new MontonioOrderCreator(
        clock,
        paymentInternalReferenceService,
        montonioPaymentChannelConfiguration,
        localeService,
    )

    montonioOrderCreator.useFakeNotificationsUrl = useFakeNotificationsUrl
    montonioOrderCreator.apiUrl = apiUrl
    montonioOrderCreator.memberFee = memberFee
    montonioOrderCreator.memberFeeTestPersonalCode = memberFeeTestPersonalCode
  }

  def "should create MontonioOrder for MEMBER_FEE payment type"() {
    given:
    def aPaymentData = aNewMemberPaymentData()
    MontonioPaymentChannel aPaymentChannel = PaymentFixture.aMontonioPaymentChannel()
    String aReference = "aReference"
    Locale aLocale = Locale.ENGLISH
    montonioPaymentChannelConfiguration.getPaymentProviderChannel(aPaymentData.getPaymentChannel()) >> aPaymentChannel
    paymentInternalReferenceService.getPaymentReference(aPerson, aPaymentData, _) >> aReference
    localeService.getCurrentLocale() >> aLocale

    when:
    MontonioOrder order = montonioOrderCreator.getOrder(aPaymentData, aPerson)

    then:
    order.accessKey == aPaymentChannel.accessKey
    order.merchantReference == aReference
    order.returnUrl == apiUrl + "/payments/member-success"
    order.notificationUrl == apiUrl + "/payments/notifications"
    order.grandTotal == memberFee
    order.currency == Currency.EUR
    order.exp == clock.instant().epochSecond + 600
    order.locale == aLocale.toLanguageTag()
    order.payment.amount == memberFee
    order.payment.currency == Currency.EUR
    order.payment.methodOptions.preferredProvider == aPaymentChannel.bic
    order.payment.methodOptions.preferredLocale == aLocale.toLanguageTag()
    order.payment.methodOptions.paymentDescription == "member:${aPaymentData.recipientPersonalCode}"
    order.billingAddress.firstName == aPerson.firstName
    order.billingAddress.lastName == aPerson.lastName
  }

  def "should create MontonioOrder for SINGLE payment type"() {
    given:
    def aPaymentData = aPaymentData()
    MontonioPaymentChannel aPaymentChannel = PaymentFixture.aMontonioPaymentChannel()
    String aReference = "aReference"
    Locale aLocale = Locale.ENGLISH
    montonioPaymentChannelConfiguration.getPaymentProviderChannel(aPaymentData.getPaymentChannel()) >> aPaymentChannel
    paymentInternalReferenceService.getPaymentReference(aPerson, aPaymentData, _) >> aReference
    localeService.getCurrentLocale() >> aLocale

    when:
    MontonioOrder order = montonioOrderCreator.getOrder(aPaymentData, aPerson)

    then:
    order.accessKey == aPaymentChannel.accessKey
    order.merchantReference == aReference
    order.returnUrl == apiUrl + "/payments/success"
    order.notificationUrl == apiUrl + "/payments/notifications"
    order.grandTotal == memberFee
    order.currency == Currency.EUR
    order.exp == clock.instant().epochSecond + 600
    order.locale == aLocale.toLanguageTag()
    order.payment.amount == memberFee
    order.payment.currency == Currency.EUR
    order.payment.methodOptions.preferredProvider == aPaymentChannel.bic
    order.payment.methodOptions.preferredLocale == aLocale.toLanguageTag()
    order.payment.methodOptions.paymentDescription == "30101119828, IK:${aPaymentData.recipientPersonalCode}, EE3600001707"
    order.billingAddress.firstName == aPerson.firstName
    order.billingAddress.lastName == aPerson.lastName
  }

  def "should use fake notification URL when configured"() {
    given:
    montonioOrderCreator.useFakeNotificationsUrl = true
    def paymentData = PaymentFixture.aNewMemberPaymentData().tap {
      it.recipientPersonalCode = "11111111111"
    }
    def paymentChannel = PaymentFixture.aMontonioPaymentChannel().tap {
      it.accessKey = "access-key"
      it.bic = "bic"
    }
    montonioPaymentChannelConfiguration.getPaymentProviderChannel(paymentData.getPaymentChannel()) >> paymentChannel
    paymentInternalReferenceService.getPaymentReference(aPerson, paymentData, _) >> "reference"
    localeService.getCurrentLocale() >> Locale.ENGLISH

    when:
    MontonioOrder order = montonioOrderCreator.getOrder(paymentData, aPerson)

    then:
    order.notificationUrl == "https://tuleva.ee/fake-notification-url"
  }

  def "should throw exception if payment amount is null for non-member fee payment"() {
    given:
    def paymentData = PaymentFixture.aPaymentData().tap {
      it.type = PaymentData.PaymentType.SINGLE
      it.recipientPersonalCode = "22222222222"
      it.amount = null
    }

    when:
    montonioOrderCreator.getOrder(paymentData, aPerson)

    then:
    thrown(IllegalArgumentException)
  }

  def "should return correct member fee for test personal code"() {
    given:
    def paymentData = PaymentFixture.aNewMemberPaymentData().tap {
      it.recipientPersonalCode = memberFeeTestPersonalCode
    }
    def paymentChannel = PaymentFixture.aMontonioPaymentChannel().tap {
      it.accessKey = "access-key"
      it.bic = "bic"
    }
    montonioPaymentChannelConfiguration.getPaymentProviderChannel(paymentData.getPaymentChannel()) >> paymentChannel
    paymentInternalReferenceService.getPaymentReference(aPerson, paymentData, _) >> "reference"
    localeService.getCurrentLocale() >> Locale.ENGLISH

    when:
    MontonioOrder order = montonioOrderCreator.getOrder(paymentData, aPerson)

    then:
    order.grandTotal == BigDecimal.ONE
    order.payment.amount == BigDecimal.ONE
  }

  def "should throw exception if member fee is null"() {
    given:
    PaymentData paymentData = aNewMemberPaymentData()
    montonioOrderCreator.memberFee = null

    when:
    montonioOrderCreator.getOrder(paymentData, aPerson)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "Member fee must not be null"
  }


  def "should return correct payment amount for member fee"() {
    given:
    PaymentData paymentData = aNewMemberPaymentData()
    paymentData.setRecipientPersonalCode("differentPersonalCode")

    when:
    BigDecimal amount = montonioOrderCreator.getPaymentAmount(paymentData)

    then:
    amount == memberFee
  }
}
