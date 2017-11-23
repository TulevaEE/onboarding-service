package ee.tuleva.onboarding.mandate.content.thymeleaf

import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.user.User
import org.thymeleaf.context.Context
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserPreferences
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

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
        context.getVariables().get("email") == user.email
        context.getVariables().get("firstName") == user.firstName
        context.getVariables().get("lastName") == user.lastName
        context.getVariables().get("idCode") == user.personalCode
        context.getVariables().get("phoneNumber") == user.phoneNumber
    }

    def "Mandate"() {
        when:
        Mandate sampleMandate = sampleMandate();
        Instant createdDate = Instant.ofEpochMilli(1000)
        sampleMandate.setCreatedDate(createdDate)

        Context context = ContextBuilder.builder()
                .mandate(sampleMandate)
                .build()
        then:
        context.getVariables().get("documentDate") == "1970-01-01"
        context.getVariables().get("documentDatePPKKAAAA") == "01.01.1970"
    }

    def "Funds"() {
        when:
        Context context = ContextBuilder.builder()
                .funds(sampleFunds())
                .build()
        then:
        List<Fund> funds = context.getVariables().get("funds")
        areFundsSortedByName(funds)
        Map<String, String> fundIsinNames = context.getVariables().get("fundIsinNames")
        fundIsinNames.get(sampleFunds().get(0).isin) == sampleFunds().get(0).name
    }

    boolean areFundsSortedByName(List<Fund> funds) {
        return funds.get(0).name == "LHV S" && funds.get(5).name == "Tuleva maailma aktsiate pensionifond"
    }

    def "TransactionId"() {
        given:
        String sampleTransactionId = "123"
        when:
        Context context = ContextBuilder.builder()
                .transactionId(sampleTransactionId)
                .build()
        then:
        context.getVariables().get("transactionId") == sampleTransactionId
    }

    def "FutureContributionFundIsin"() {
        given:
        String selectedFundIsin = "123"
        when:
        Context context = ContextBuilder.builder()
                .futureContributionFundIsin(selectedFundIsin)
                .build()
        then:
        context.getVariables().get("selectedFundIsin") == selectedFundIsin
    }

    def "DocumentNumber"() {
        given:
        String documentNumber = "123"
        when:
        Context context = ContextBuilder.builder()
                .documentNumber(documentNumber)
                .build()
        then:
        context.getVariables().get("documentNumber") == documentNumber
    }

    def "FundTransferExchanges"() {
        when:
        Context context = ContextBuilder.builder()
                .fundTransferExchanges(sampleMandate().fundTransferExchanges)
                .build()
        then:
        context.getVariables().get("fundTransferExchanges") == sampleMandate().fundTransferExchanges
    }

    def "GroupedFundTransferExchanges"() {
        when:
        Context context = ContextBuilder.builder()
                .groupedTransferExchanges(sampleMandate().fundTransferExchanges)
                .build()
        then:
        context.getVariables().get("groupedFundTransferExchanges").size() == 2
    }

    def "UserPreferences"() {
        def dummyUserPreferences = sampleUserPreferences().build()
        when:
        Context context = ContextBuilder.builder()
                .userPreferences(dummyUserPreferences)
                .build()
        then:
        UserPreferences userPreferences = context.getVariables().get("userPreferences")
        userPreferences.country == dummyUserPreferences.country
        context.getVariables().get("addressLine1") == dummyUserPreferences.addressRow1
        context.getVariables().get("addressLine2") == dummyUserPreferences.addressRow2
        context.getVariables().get("settlement") == dummyUserPreferences.addressRow2
        context.getVariables().get("countryCode") == dummyUserPreferences.country
        context.getVariables().get("postCode") == dummyUserPreferences.postalIndex
        context.getVariables().get("districtCode") == dummyUserPreferences.districtCode
        context.getVariables().get("email") == dummyUserPreferences.email
    }

    def "UserPreferences don't overwrite User email"() {
        given:
        User user = sampleUser().email("expected@email.com").build()
        UserPreferences preferences = sampleUserPreferences().email("other@email.com").build()
        when:
        Context context = ContextBuilder.builder()
                .user(user)
                .userPreferences(preferences)
                .build()
        then:
        context.getVariables().get("email") == user.email
    }
}
