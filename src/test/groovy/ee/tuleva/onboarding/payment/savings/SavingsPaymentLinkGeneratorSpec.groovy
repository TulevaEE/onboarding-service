package ee.tuleva.onboarding.payment.savings

import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.PaymentData
import ee.tuleva.onboarding.payment.PaymentFixture
import ee.tuleva.onboarding.payment.PaymentLink
import ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrderClient
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentChannelConfiguration
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.TULUNDUSUHISTU
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SAVINGS
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE

class SavingsPaymentLinkGeneratorSpec extends Specification {

    def clock = Clock.fixed(Instant.parse("2020-01-01T10:00:00Z"), ZoneOffset.UTC)
    def orderClient = Mock(MontonioOrderClient)
    def savingsChannelConfiguration = new SavingsChannelConfiguration(
        returnUrl: "http://success.url",
        notificationUrl: "http://notification.url",
        accessKey: "test-access-key",
    )
    def paymentChannelConfiguration = Mock(MontonioPaymentChannelConfiguration)
    def paymentInternalReferenceService = Mock(PaymentInternalReferenceService)
    def localeService = Mock(LocaleService)

    def generator = new SavingsPaymentLinkGenerator(
        clock,
        orderClient,
        savingsChannelConfiguration,
        paymentChannelConfiguration,
        paymentInternalReferenceService,
        localeService
    )

    def "generates payment link successfully"() {
        given:
        def person = samplePerson
        def paymentData = new PaymentData("38812121215", new BigDecimal("10.00"), EUR, SAVINGS, LHV)
        def expectedUrl = "https://payment.url"

        def channel = PaymentFixture.aMontonioPaymentChannel()
        def reference = "REF123456"

        paymentChannelConfiguration.getPaymentProviderChannel(LHV) >> channel
        paymentInternalReferenceService.getPaymentReference(person, paymentData) >> reference
        localeService.getCurrentLocale() >> Locale.ENGLISH

        def order
        orderClient.getPaymentUrl(_ as MontonioOrder, _ as SavingsChannelConfiguration) >> { args ->
          order = args[0]
          return expectedUrl
        }

        when:
        def result = generator.getPaymentLink(paymentData, person)

        then:
        result.url == expectedUrl
        order != null
        order.accessKey == "test-access-key"
        order.accessKey == "test-access-key"
        order.merchantReference == "REF123456"
        order.returnUrl == "http://success.url"
        order.notificationUrl == "http://notification.url"
        order.grandTotal == new BigDecimal("10.00")
        order.currency == EUR
        order.exp == 1577873400
        order.locale == "en"
        order.payment.amount == new BigDecimal("10.00")
        order.payment.currency == EUR
        order.payment.methodOptions.preferredProvider == channel.bic
        order.payment.methodOptions.preferredLocale == "en"
        order.payment.methodOptions.paymentDescription == "38812121215"
        order.billingAddress.firstName == "Jordan"
        order.billingAddress.lastName == "Valdma"
    }


    def "throws exception when payment channel has no BIC"() {
        given:
        def person = samplePerson
        def paymentData = new PaymentData("38812121215", new BigDecimal("10.00"), EUR, SAVINGS, LHV)

        def channel = PaymentFixture.aMontonioPaymentChannel().tap({ it -> it.bic = null})
        paymentChannelConfiguration.getPaymentProviderChannel(LHV) >> channel

        when:
        generator.getPaymentLink(paymentData, person)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Invalid payment channel: LHV"
    }
}
