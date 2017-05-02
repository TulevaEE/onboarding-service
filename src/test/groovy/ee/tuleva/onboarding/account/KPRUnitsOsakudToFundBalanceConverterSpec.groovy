package ee.tuleva.onboarding.account

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

class KPRUnitsOsakudToFundBalanceConverterSpec extends Specification {
    FundRepository fundRepository = Mock(FundRepository)
    KPRUnitsOsakudToFundBalanceConverter service = new KPRUnitsOsakudToFundBalanceConverter(fundRepository)

    def "Convert: converts EPIS response to fund balances"() {
        given:
        Fund sampleFund = MandateFixture.sampleFunds().first()
        PensionAccountBalanceResponseType.Units.Balance sampleBalance =
                new PensionAccountBalanceResponseType.Units.Balance()
        sampleBalance.setSecurityName(sampleFund.name)
        sampleBalance.setAmount(123)
        sampleBalance.setNav(432)
        sampleBalance.setCurrency("EUR")

        fundRepository.findByNameIgnoreCase(sampleFund.name) >> sampleFund

        when:
        FundBalance result = service.convert(sampleBalance)

        then:
        result.fund == sampleFund
        result.value == sampleBalance.amount * sampleBalance.nav
        result.currency == sampleBalance.currency
    }

    def "Convert: exceptions on unknown fund"() {
        given:
        PensionAccountBalanceResponseType.Units.Balance sampleBalance =
                new PensionAccountBalanceResponseType.Units.Balance()
        sampleBalance.setSecurityName("Unknown fund")

        fundRepository.findByNameIgnoreCase(_ as String) >> null

        when:
        FundBalance result = service.convert(sampleBalance)

        then:
        thrown RuntimeException
    }

    def "Convert: converts EPIS response, which denotes booked funds, to fund balances"() {
        given:
        Fund sampleFund = MandateFixture.sampleFunds().first()
        String actualFundName = sampleFund.name
        sampleFund.name = sampleFund.name + " - Broneeritud"

        PensionAccountBalanceResponseType.Units.Balance sampleBalance =
                new PensionAccountBalanceResponseType.Units.Balance()
        sampleBalance.setSecurityName(sampleFund.name)
        sampleBalance.setAmount(123)
        sampleBalance.setNav(432)
        sampleBalance.setCurrency("EUR")

        fundRepository.findByNameIgnoreCase(actualFundName) >> sampleFund

        when:
        FundBalance result = service.convert(sampleBalance)

        then:
        result.fund == sampleFund
        result.value == sampleBalance.amount * sampleBalance.nav
        result.currency == sampleBalance.currency
    }

}
