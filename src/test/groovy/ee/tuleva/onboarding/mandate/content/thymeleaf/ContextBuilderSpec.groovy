package ee.tuleva.onboarding.mandate.content.thymeleaf

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateFixture
import ee.tuleva.onboarding.user.UserPreferences
import org.thymeleaf.context.Context
import spock.lang.Specification

import java.time.Instant

class ContextBuilderSpec extends Specification {

    def "Build: Building Context works"() {
        when:
        Context context = ContextBuilder.builder().build()
        then:
        true
    }

    def "Builder: Instantiating a builder"() {
        when:
        ContextBuilder contextBuilder = ContextBuilder.builder()
        then:
        true
    }

    def "User"() {
        when:
        Context context = ContextBuilder.builder()
                .user(UserFixture.sampleUser())
                .build()
        then:
        context.getVariables().get("email") == UserFixture.sampleUser().email
        context.getVariables().get("firstName") == UserFixture.sampleUser().firstName
        context.getVariables().get("lastName") == UserFixture.sampleUser().lastName
        context.getVariables().get("idCode") == UserFixture.sampleUser().personalCode
        context.getVariables().get("phoneNumber") == UserFixture.sampleUser().phoneNumber
    }

    def "Mandate"() {
        when:
        Mandate sampleMandate = MandateFixture.sampleMandate();
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
                .funds(MandateFixture.sampleFunds())
                .build()
        then:
        List<Fund> funds = context.getVariables().get("funds")
        areFundsSortedByName(funds)
        Map<String, String> fundIsinNames = context.getVariables().get("fundIsinNames")
        fundIsinNames.get(MandateFixture.sampleFunds().get(0).isin) == MandateFixture.sampleFunds().get(0).name
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
                .fundTransferExchanges(MandateFixture.sampleMandate().fundTransferExchanges)
                .build()
        then:
        context.getVariables().get("fundTransferExchanges") == MandateFixture.sampleMandate().fundTransferExchanges
    }

    def "UserPreferences"() {
        when:
        Context context = ContextBuilder.builder()
                .userPreferences(UserFixture.sampleUserPreferences())
                .build()
        then:
        UserPreferences userPreferences = context.getVariables().get("userPreferences")
        userPreferences.country == UserFixture.sampleUserPreferences().country
        context.getVariables().get("addressLine1") == UserFixture.sampleUserPreferences().addressRow1
        context.getVariables().get("addressLine2") == UserFixture.sampleUserPreferences().addressRow2
        context.getVariables().get("settlement") == UserFixture.sampleUserPreferences().addressRow2
        context.getVariables().get("countryCode") == UserFixture.sampleUserPreferences().country
        context.getVariables().get("postCode") == UserFixture.sampleUserPreferences().postalIndex
        context.getVariables().get("districtCode") == UserFixture.sampleUserPreferences().districtCode
    }
}
