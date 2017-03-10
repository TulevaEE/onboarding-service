package ee.tuleva.onboarding.account

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType
import ee.eesti.xtee6.kpr.PersonalSelectionResponseType
import ee.tuleva.domain.fund.Fund
import ee.tuleva.domain.fund.FundRepository
import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.kpr.KPRClient
import spock.lang.Ignore
import spock.lang.Specification

class AccountStatementServiceSpec extends Specification {

    AccountStatementService service

    def setup() {
        service = new AccountStatementService(xRoadClient, fundRepository)

        personalSelection = new PersonalSelectionResponseType()
        personalSelection.setPensionAccount(new PersonalSelectionResponseType.PensionAccount())
        personalSelection.getPensionAccount().setSecurityName("Very active fund")
    }


    KPRClient xRoadClient = Mock(KPRClient)
    FundRepository fundRepository = Mock(FundRepository)

    PensionAccountBalanceResponseType resp = Mock(PensionAccountBalanceResponseType)
    PensionAccountBalanceResponseType.Units units = Mock(PensionAccountBalanceResponseType.Units)
    PersonalSelectionResponseType personalSelection



    @Ignore
    def "GetMyPensionAccountStatement"() {
        given:
        1 * xRoadClient.pensionAccountBalance(*_) >> resp
        1 * resp.getUnits() >> units
        1 * units.getBalance() >> twoFundBalanceFromKPR()
        2 * fundRepository.findByNameIgnoreCase("LHV Fund") >> Fund.builder().name("LHV Fund").isin("LV0987654321").build()
        1 * xRoadClient.personalSelection(*_) >> personalSelection
        1 * fundRepository.findByNameIgnoreCase(*_) >> Fund.builder().name("Very active fund").isin("LV123123123123").build()

        when:
        List<FundBalance> fundBalances = service.getMyPensionAccountStatement(UserFixture.sampleUser())
        then:
        fundBalances != null


    }

    List<PensionAccountBalanceResponseType.Units.Balance> twoFundBalanceFromKPR() {
        PensionAccountBalanceResponseType.Units.Balance balance = new PensionAccountBalanceResponseType.Units.Balance()
        balance.setSecurityName("LHV Fund")
        balance.setAmount(new BigDecimal("15883.071"))
        balance.setNav(new BigDecimal("1.58812"))

        [balance, balance]
    }
}
