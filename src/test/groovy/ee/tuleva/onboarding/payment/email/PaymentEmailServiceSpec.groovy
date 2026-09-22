package ee.tuleva.onboarding.payment.email

import ee.tuleva.onboarding.mandate.MandateRepository
import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.nudge.NudgeDecision
import ee.tuleva.onboarding.notification.email.Email
import ee.tuleva.onboarding.notification.email.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notConverted
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getAttachments
import static ee.tuleva.onboarding.notification.email.EmailType.*
import ee.tuleva.onboarding.nudge.NudgeKey
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates

class PaymentEmailServiceSpec extends Specification {

  static final UUID CHILD_LINK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333")
  static final UUID COMPANY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444")

  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()

  MandateRepository mandateRepository = Mock()
  PaymentEmailService paymentEmailService = new PaymentEmailService(mandateRepository, emailService,
      emailPersistenceService)

  def "send third pillar payment success email"() {
    given:
    def user = sampleUser().build()
    def conversion = notConverted()
    def contactDetails = contactDetailsFixture()

    def decision = NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE)
    def payment = aNewSinglePayment()
    def message = new MandrillMessage()
    var mergeVars = [
        "fname"                : user.firstName,
        "lname"                : user.lastName,
        "amount"               : 10.00,
        "currency"             : EUR,
        "senderPersonalCode"   : user.personalCode,
        "recipientPersonalCode": payment.recipientPersonalCode,
    ] + decision.mergeVars(Locale.ENGLISH)
    def tags = ["pillar_3.1", "mandate", "payment", decision.tag()]
    def locale = Locale.ENGLISH
    def mandrillMessageId = "mandrillMessageId123"
    def mandate = new Mandate(mandate: new byte[0])
    mandateRepository.findById(42L) >> Optional.of(mandate)
    def mandateAttachments = getAttachments(user, mandate)

    emailPersistenceService.cancel(user, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE) >> [new Email(
        personalCode: user.personalCode, mandrillMessageId: mandrillMessageId,
        type: THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, mandateId: 42L
    )]
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, decision, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_success_mandate_en", mergeVars, tags, {
      it.size() == 1 && it.first.getName() == mandateAttachments.first.getName() && it.first.getContent() == mandateAttachments.first.getContent()
    }) >> message
    1 * emailService.send(user, message, "third_pillar_payment_success_mandate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE, mandrillResponse.status, decision.tag())
  }

  def "send savings fund payment emails"() {
    given:
    def user = sampleUser().build()
    def decision = NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE)
    def message = new MandrillMessage()
    var mergeVars = [
        "fname"              : user.firstName,
        "lname"              : user.lastName,
    ] + decision.mergeVars(Locale.ENGLISH)
    def tags = ["savings_fund", decision.tag()]
    def locale = Locale.ENGLISH

    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    paymentEmailService.sendSavingsFundPaymentEmail(user, email, decision, locale)

    then:
    1 * emailService.send(user, message, templateName) >> Optional.of(mandrillResponse)
    1 * emailService.newMandrillMessage(user.email, templateName, mergeVars + email.mergeVars(), tags) >> message
    1 * emailPersistenceService.save(user, mandrillResponse.id, email.emailType(), mandrillResponse.status, decision.tag())

    where:
    email                                               | templateName
    SavingsFundPaymentEmail.personSuccess()             | "savings_fund_payment_success_person_en"
    SavingsFundPaymentEmail.childSuccess("Kid Tester", CHILD_LINK_ID)  | "savings_fund_payment_success_child_en"
    SavingsFundPaymentEmail.companySuccess("Tuleva OÜ", COMPANY_ID) | "savings_fund_payment_success_company_en"
    SavingsFundPaymentEmail.failed()                    | "savings_fund_payment_failed_en"
    SavingsFundPaymentEmail.cancelled()                 | "savings_fund_payment_cancelled_en"
  }

  def "send savings fund payment email without a pillar suggestion"() {
    given:
    def user = sampleUser().build()
    def message = new MandrillMessage()
    var mergeVars = [
        "fname": user.firstName,
        "lname": user.lastName
    ]
    def tags = ["savings_fund"]
    def locale = Locale.ENGLISH

    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.failed(), locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "savings_fund_payment_failed_en", mergeVars, tags) >> message
    1 * emailService.send(user, message, "savings_fund_payment_failed_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, SAVINGS_FUND_PAYMENT_FAIL, mandrillResponse.status)
  }

  def "savings fund payment email includes the recipient name when present"() {
    given:
    def user = sampleUser().build()
    def decision = NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE)
    def message = new MandrillMessage()
    var mergeVars = [
        "fname"              : user.firstName,
        "lname"              : user.lastName,
        "recipientName"      : "Kid Tester",
        "recipientIsChild"   : true,
        "recipientAccountId" : CHILD_LINK_ID.toString(),
    ] + decision.mergeVars(Locale.ENGLISH)
    def tags = ["savings_fund", decision.tag()]
    def locale = Locale.ENGLISH
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.childSuccess("Kid Tester", CHILD_LINK_ID), decision, locale)

    then:
    1 * emailService.send(user, message, "savings_fund_payment_success_child_en") >> Optional.of(mandrillResponse)
    1 * emailService.newMandrillMessage(user.email, "savings_fund_payment_success_child_en", mergeVars, tags) >> message
    1 * emailPersistenceService.save(user, mandrillResponse.id, SAVINGS_FUND_PAYMENT_SUCCESS_CHILD, mandrillResponse.status, decision.tag())
  }
}
