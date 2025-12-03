package ee.tuleva.onboarding.mandate.content.thymeleaf

import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.user.User
import org.thymeleaf.context.Context
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture

class ContextBuilderSpec extends Specification {

  def "Build: Building Context works"() {
    when:
    ContextBuilder.builder().build()
    then:
    true
  }

  def "Builder: Instantiating a builder"() {
    when:
    ContextBuilder.builder()
    then:
    true
  }

  def "User"() {
    given:
    User user = sampleUser().build()
    when:
    Context context = ContextBuilder.builder()
        .user(user)
        .build()
    then:
    context.getVariable("email") == user.email
    context.getVariable("firstName") == user.firstName
    context.getVariable("lastName") == user.lastName
    context.getVariable("idCode") == user.personalCode
    context.getVariable("phoneNumber") == user.phoneNumber
  }

  def "Mandate"() {
    when:
    Mandate sampleMandate = sampleMandate()
    Instant createdDate = Instant.ofEpochMilli(1000)
    sampleMandate.setCreatedDate(createdDate)

    Context context = ContextBuilder.builder()
        .mandate(sampleMandate)
        .build()
    then:
    context.getVariable("documentDate") == "1970-01-01"
    context.getVariable("documentDatePPKKAAAA") == "01.01.1970"
  }

  def "Funds"() {
    when:
    Context context = ContextBuilder.builder()
        .funds(sampleFunds())
        .build()
    then:
    List<Fund> funds = context.getVariable("funds")
    areFundsSortedByName(funds)
    Map<String, String> fundIsinNames = context.getVariable("fundIsinNames")
    fundIsinNames.get(sampleFunds().get(0).isin) == sampleFunds().get(0).nameEstonian
  }

  boolean areFundsSortedByName(List<Fund> funds) {
    return funds.get(0).nameEstonian == "LHV S" && funds.get(5).nameEstonian == "Tuleva maailma aktsiate pensionifond"
  }

  def "TransactionId"() {
    given:
    String sampleTransactionId = "123"
    when:
    Context context = ContextBuilder.builder()
        .transactionId(sampleTransactionId)
        .build()
    then:
    context.getVariable("transactionId") == sampleTransactionId
  }

  def "FutureContributionFundIsin"() {
    given:
    String selectedFundIsin = "123"
    when:
    Context context = ContextBuilder.builder()
        .futureContributionFundIsin(selectedFundIsin)
        .build()
    then:
    context.getVariable("selectedFundIsin") == selectedFundIsin
  }

  def "DocumentNumber"() {
    given:
    String documentNumber = "123"
    when:
    Context context = ContextBuilder.builder()
        .documentNumber(documentNumber)
        .build()
    then:
    context.getVariable("documentNumber") == documentNumber
  }

  def "applicationTypeToCancel"() {
    given:
    def applicationTypeToCancel = WITHDRAWAL
    when:
    Context context = ContextBuilder.builder()
        .applicationTypeToCancel(applicationTypeToCancel)
        .build()
    then:
    context.getVariable("applicationTypeToCancel") == applicationTypeToCancel
  }

  def "FundTransferExchanges"() {
    when:
    Context context = ContextBuilder.builder()
        .fundTransferExchanges(sampleMandate().fundTransferExchanges)
        .build()
    then:
    context.getVariable("fundTransferExchanges") == sampleMandate().fundTransferExchanges
  }

  def "ContactDetails"() {
    def dummyContactDetails = contactDetailsFixture()
    when:
    Context context = ContextBuilder.builder()
        .contactDetails(dummyContactDetails)
        .build()
    then:
    context.getVariable("contactDetails") == dummyContactDetails
    context.getVariable("email") == dummyContactDetails.email
  }

  def "ContactDetails don't overwrite User email"() {
    given:
    User user = sampleUser().email("expected@email.com").build()
    ContactDetails preferences = contactDetailsFixture()
    when:
    Context context = ContextBuilder.builder()
        .user(user)
        .contactDetails(preferences)
        .build()
    then:
    context.getVariable("email") == user.email
  }

  def "address"() {
    def address = countryFixture().build()
    when:
    Context context = ContextBuilder.builder()
        .address(address)
        .build()
    then:
    context.getVariable("countryCode") == address.countryCode
  }
}
