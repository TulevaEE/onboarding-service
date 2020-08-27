package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.fund.Fund
import spock.lang.Specification

import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva2ndPillarFundBalance
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static java.math.BigDecimal.ONE
import static java.math.BigDecimal.ZERO

class AccountStatementServiceSpec extends Specification {

    def episService = Mock(EpisService)
    def fundBalanceConverter = Mock(FundBalanceDtoToFundBalanceConverter)

    def service = new AccountStatementService(episService, fundBalanceConverter)

    def "returns an account statement"() {
        given:
        def person = samplePerson()
        def fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()
        def fundBalance = activeTuleva2ndPillarFundBalance.first()

        episService.getAccountStatement(person) >> [fundBalanceDto]
        fundBalanceConverter.convert(fundBalanceDto, person) >> fundBalance

        when:
        List<FundBalance> accountStatement = service.getAccountStatement(person)

        then:
        accountStatement == [fundBalance]
    }

    def "fundBalanceDto with no Isin code are filtered out and will not try to convert"() {
        given:
            def person = samplePerson()
            def fundBalanceDto = FundBalanceDto.builder().isin(null).build()
            episService.getAccountStatement(person) >> [fundBalanceDto]

        when:
            List<FundBalance> accountStatement = service.getAccountStatement(person)

        then:
            accountStatement.isEmpty()
            0 * fundBalanceConverter.convert(fundBalanceDto, person)
    }

    def "filters non-active out zero balance funds"() {
        given:
        def person = samplePerson()
        def nonActiveZeroFund = FundBalanceDto.builder().isin("1").value(ZERO).activeContributions(false).build()
        def nonActiveNonZeroFund = FundBalanceDto.builder().isin("2").value(ONE).activeContributions(false).build()
        def activeZeroFund = FundBalanceDto.builder().isin("3").value(ZERO).activeContributions(true).build()
        def activeNonZeroFund = FundBalanceDto.builder().isin("4").value(ONE).activeContributions(true).build()

        episService.getAccountStatement(person) >> [nonActiveZeroFund, nonActiveNonZeroFund, activeZeroFund, activeNonZeroFund]
        fundBalanceConverter.convert(_, person) >> { FundBalanceDto fundBalanceDto, _ ->
            FundBalance.builder().fund(Fund.builder().isin(fundBalanceDto.isin).build()).build()
        }

        when:
        List<FundBalance> accountStatement = service.getAccountStatement(person)

        then:
        with(accountStatement.get(0)) {
            isin == nonActiveNonZeroFund.isin
        }
        with(accountStatement.get(1)) {
            isin == activeZeroFund.isin
        }
        with(accountStatement.get(2)) {
            isin == activeNonZeroFund.isin
        }
        accountStatement.size() == 3
    }

    def "handles fundBalanceDto to fundBalance conversion exceptions"() {
        given:
        def person = samplePerson()
        def fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()

        episService.getAccountStatement(person) >> [fundBalanceDto]
        fundBalanceConverter.convert(fundBalanceDto, person) >> {
            throw new IllegalArgumentException()
        }

        when:
        service.getAccountStatement(person)

        then:
        thrown(IllegalStateException)
    }
}
