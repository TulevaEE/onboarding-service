package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import spock.lang.Specification

class FundBalanceDtoToFundBalanceConverterSpec extends Specification {

    def fundRepository = Mock(FundRepository)
    def converter = new FundBalanceDtoToFundBalanceConverter(fundRepository)

    def "converts FundBalanceDtos to FundBalance instances"() {
        given:
        def isin = "someIsin"
        FundBalanceDto fundBalanceDto = FundBalanceDto.builder()
                .isin(isin)
                .value(123)
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
        fundBalance.currency == fundBalanceDto.currency
        fundBalance.pillar == fundBalanceDto.pillar
        fundBalance.activeContributions == fundBalanceDto.activeContributions
    }
}