package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture

class FundBalanceDtoToFundBalanceConverterSpec extends Specification {

    def fundRepository = Mock(FundRepository)
    def cashFlowService = Mock(CashFlowService)
    def converter = new FundBalanceDtoToFundBalanceConverter(fundRepository, cashFlowService)

    def "converts FundBalanceDtos to FundBalance instances"() {
        given:
        def isin = "someIsin"
        def fundBalanceDto = FundBalanceDto.builder()
            .isin(isin)
            .value(123.0)
            .units(345.0)
            .currency("EUR")
            .pillar(3)
            .activeContributions(true)
            .build()

        fundRepository.findByIsin(isin) >> Fund.builder().isin(isin).build()

        when:
        FundBalance fundBalance = converter.convert(fundBalanceDto)

        then:
        fundBalance.fund.isin == fundBalanceDto.isin
        fundBalance.value == fundBalanceDto.value
        fundBalance.units == fundBalanceDto.units
        fundBalance.currency == fundBalanceDto.currency
        fundBalance.pillar == fundBalanceDto.pillar
        fundBalance.activeContributions == fundBalanceDto.activeContributions
        fundBalance.contributionSum == null
    }

    def "handles missing funds by throwing an exception"() {
        given:
        def isin = "someIsin"
        def fundBalanceDto = FundBalanceDto.builder()
            .isin(isin)
            .build()

        fundRepository.findByIsin(isin) >> null

        when:
        converter.convert(fundBalanceDto)

        then:
        thrown(IllegalArgumentException)
    }

    def "calculates the contribution sum if the person is specified"() {
        given:
        def isin = "someIsin"
        def person = samplePerson()
        def fundBalanceDto = FundBalanceDto.builder()
            .isin(isin)
            .value(123.0)
            .units(345.0)
            .currency("EUR")
            .pillar(3)
            .activeContributions(true)
            .build()

        fundRepository.findByIsin(isin) >> Fund.builder().isin(isin).build()
        cashFlowService.getCashFlowStatement(person) >> cashFlowFixture()

        when:
        FundBalance fundBalance = converter.convert(fundBalanceDto, person)

        then:
        fundBalance.contributionSum == -145.0
    }
}