package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.common.Utils
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.EpisAccountOverviewProvider
import ee.tuleva.onboarding.comparisons.overview.Transaction
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.fund.Fund
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.account.AccountStatementFixture.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class AccountStatementServiceSpec extends Specification {

    def episService = Mock(EpisService)
    def fundBalanceConverter = Mock(FundBalanceDtoToFundBalanceConverter)
    def episAccountOverviewProvider = Mock(EpisAccountOverviewProvider)

    def service = new AccountStatementService(episService, fundBalanceConverter, episAccountOverviewProvider)

    def "returns an account statement"() {
        given:
        def person = samplePerson()
        def fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()
        def fundBalance = sampleConvertedFundBalanceWithActiveTulevaFund.first()

        episService.getAccountStatement(person) >> [fundBalanceDto]
        fundBalanceConverter.convert(fundBalanceDto) >> fundBalance

        when:
        List<FundBalance> accountStatement = service.getAccountStatement(person)

        then:
        accountStatement == [fundBalance]
    }

    def "returns an account statement with properly calculated contributions when calculateContribuition is set to true"() {
        given:
            def person = samplePerson()
            def fundBalanceDto = FundBalanceDto.builder().currency("EUR").pillar(2).isin("someIsin2ndPillar").build()
            def fundBalance = sampleConvertedFundBalanceWithActiveTulevaFund.first()

            //FundBalance fundBalance = new FundBalance(Mock(Fund), new BigDecimal(12000.00), "EUR", 2, true, new BigDecimal(0.00))

            List< Transaction> transactions = [new Transaction(new BigDecimal(1000.00), Utils.parseInstant("2007-07-07")),
                                               new Transaction(new BigDecimal(9000.00), Utils.parseInstant("2008-08-08"))]

            AccountOverview accountOverview = new AccountOverview(transactions, new BigDecimal(10000.00), new BigDecimal(12000.00), Utils.parseInstant("2007-07-07"), Instant.now(), 2)

            episService.getAccountStatement(person) >> [fundBalanceDto]
            fundBalanceConverter.convert(fundBalanceDto) >> fundBalance
            1 * episAccountOverviewProvider.getAccountOverview(person , _, _) >> accountOverview


        when:
            List<FundBalance> accountStatement = service.getAccountStatement(person, true)

        then:
            accountStatement == [fundBalance]
            accountStatement.first().contributionSum == new BigDecimal(10000.00)
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
            0 * fundBalanceConverter.convert(fundBalanceDto)
    }

    def "handles fundBalanceDto to fundBalance conversion exceptions"() {
        given:
        def person = samplePerson()
        def fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()

        episService.getAccountStatement(person) >> [fundBalanceDto]
        fundBalanceConverter.convert(fundBalanceDto) >> {
            throw new IllegalArgumentException()
        }

        when:
        service.getAccountStatement(person)

        then:
        thrown(IllegalStateException)
    }

    def "start date is per pillar"(){
        when:
            def start_2nd = AccountStatementService.getStartTimeForPillar(2)
            def start_3rd = AccountStatementService.getStartTimeForPillar(3)
        then:
            start_2nd == AccountStatementService.START_TIME_2ND_PILLAR
            start_3rd == AccountStatementService.START_TIME_3RD_PILLAR
    }

    def "Exception is thrown when unknow pillar start time is asked"() {

        when:
            AccountStatementService.getStartTimeForPillar(777777)

        then:
            thrown(RuntimeException)
    }
}
