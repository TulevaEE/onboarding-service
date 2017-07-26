package ee.tuleva.onboarding.account

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType
import ee.eesti.xtee6.kpr.PensionAccountBalanceType
import ee.eesti.xtee6.kpr.PersonalSelectionResponseType
import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.kpr.KPRClient
import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

class AccountStatementServiceSpec extends Specification {

    AccountStatementService service

    KPRUnitsOsakudToFundBalanceConverter kprUnitsOsakudToFundBalanceConverter = Mock(KPRUnitsOsakudToFundBalanceConverter)
    FundRepository fundRepository = Mock(FundRepository)
    KPRClient kprClient = Mock(KPRClient)

    PensionAccountBalanceResponseType.Units units = Mock(PensionAccountBalanceResponseType.Units) {
        getBalance() >> twoFundBalanceFromKPR()
    }
    PersonalSelectionResponseType personalSelection
    PensionAccountBalanceResponseType resp = Mock(PensionAccountBalanceResponseType) {
        getUnits()  >> units
    }

    def setup() {
        service = new AccountStatementService(kprClient, fundRepository,
                kprUnitsOsakudToFundBalanceConverter)

        personalSelection = new PersonalSelectionResponseType()
        personalSelection.setPensionAccount(new PersonalSelectionResponseType.PensionAccount())
    }

    def "GetMyPensionAccountStatement: Flag active fund balance"() {
        given:
        Fund activeFund = MandateFixture.sampleFunds().get(0)
        personalSelection.getPensionAccount().setSecurityName(activeFund.name)
        BigDecimal activeFundBalanceValue = new BigDecimal(100000)
        FundBalance sampleFundBalance = sampleFundBalance(activeFund, activeFundBalanceValue)

        1 * kprClient.pensionAccountBalance(_ as PensionAccountBalanceType, PersonFixture.samplePerson().getPersonalCode()) >> resp
        2 * kprUnitsOsakudToFundBalanceConverter.convert(_ as PensionAccountBalanceResponseType.Units.Balance) >> sampleFundBalance
        1 * kprClient.personalSelection(PersonFixture.samplePerson().getPersonalCode()) >> personalSelection

        when:
        List<FundBalance> fundBalances = service.getMyPensionAccountStatement(PersonFixture.samplePerson())
        then:
        fundBalances.size() == 2

        FundBalance activeFundBalance = fundBalances.stream().find({ fb -> fb.activeContributions })
        activeFundBalance.fund.name == activeFund.name
        activeFundBalance.fund.isin == activeFund.isin
        activeFundBalance.value == activeFundBalanceValue
    }

    def "GetMyPensionAccountStatement: Handles missing second pillar"() {
        given:

        PensionAccountBalanceResponseType resp = Mock(PensionAccountBalanceResponseType) {
            getFaultCode()  >> "40351"
        }

        Fund activeFund = MandateFixture.sampleFunds().get(0)
        personalSelection.setFaultCode("40351")

        1 * kprClient.pensionAccountBalance(_ as PensionAccountBalanceType, PersonFixture.samplePerson().getPersonalCode()) >> resp
        1 * kprClient.personalSelection(PersonFixture.samplePerson().getPersonalCode()) >> personalSelection

        when:
        List<FundBalance> fundBalances = service.getMyPensionAccountStatement(PersonFixture.samplePerson())
        then:
        fundBalances.size() == 0
    }

    def "GetMyPensionAccountStatement: Handle get statement exceptions"() {
        given:
        Fund activeFund = MandateFixture.sampleFunds().get(0)
        personalSelection.getPensionAccount().setSecurityName(activeFund.name)
        BigDecimal activeFundBalanceValue = new BigDecimal(100000)
        FundBalance sampleFundBalance = sampleFundBalance(activeFund, activeFundBalanceValue)

        kprClient
                .pensionAccountBalance(_ as PensionAccountBalanceType, PersonFixture.samplePerson().getPersonalCode()) >> new SocketTimeoutException()

        when:
        List<FundBalance> fundBalances = service.getMyPensionAccountStatement(PersonFixture.samplePerson())
        then:
        thrown PensionRegistryAccountStatementConnectionException
    }

    def "GetMyPensionAccountStatement: Handle get personal information exceptions"() {
        given:
        Fund activeFund = MandateFixture.sampleFunds().get(0)
        personalSelection.getPensionAccount().setSecurityName(activeFund.name)
        BigDecimal activeFundBalanceValue = new BigDecimal(100000)
        FundBalance sampleFundBalance = sampleFundBalance(activeFund, activeFundBalanceValue)

        kprClient.pensionAccountBalance(_ as PensionAccountBalanceType, PersonFixture.samplePerson().getPersonalCode()) >> resp
        kprUnitsOsakudToFundBalanceConverter.convert(_ as PensionAccountBalanceResponseType.Units.Balance) >> sampleFundBalance
        kprClient.personalSelection(PersonFixture.samplePerson().getPersonalCode()) >> new SocketTimeoutException()

        when:
        List<FundBalance> fundBalances = service.getMyPensionAccountStatement(PersonFixture.samplePerson())
        then:
        thrown PensionRegistryAccountStatementConnectionException
    }

    def "GetMyPensionAccountStatement: Flag active fund balance, even if names are in different case"() {
        given:
        Fund activeFund = MandateFixture.sampleFunds().get(0)
        String activeFundNameDifferentCase = activeFund.name.toUpperCase()
        personalSelection.getPensionAccount().setSecurityName(activeFundNameDifferentCase)
        BigDecimal activeFundBalanceValue = new BigDecimal(100000)
        FundBalance sampleFundBalance = sampleFundBalance(activeFund, activeFundBalanceValue)

        1 * kprClient.pensionAccountBalance(_ as PensionAccountBalanceType, PersonFixture.samplePerson().getPersonalCode()) >> resp
        2 * kprUnitsOsakudToFundBalanceConverter.convert(_ as PensionAccountBalanceResponseType.Units.Balance) >> sampleFundBalance
        1 * kprClient.personalSelection(PersonFixture.samplePerson().getPersonalCode()) >> personalSelection

        when:
        List<FundBalance> fundBalances = service.getMyPensionAccountStatement(PersonFixture.samplePerson())
        then:
        fundBalances.size() == 2

        FundBalance activeFundBalance = fundBalances.stream().find({ fb -> fb.activeContributions })
        activeFundBalance.fund.name == activeFund.name
        activeFundBalance.fund.isin == activeFund.isin
        activeFundBalance.value == activeFundBalanceValue
    }

    def "GetMyPensionAccountStatement: When active fund doesn't have a balance, include a balance row with zero value"() {
        given:
        String activeFundName = "Active Fund"
        personalSelection.getPensionAccount().setSecurityName(activeFundName)

        1 * kprClient.pensionAccountBalance(_ as PensionAccountBalanceType, PersonFixture.samplePerson().getPersonalCode()) >> resp
        FundBalance sampleFundBalance = sampleFundBalance(MandateFixture.sampleFunds().get(0), BigDecimal.ONE)

        2 * kprUnitsOsakudToFundBalanceConverter.convert(_ as PensionAccountBalanceResponseType.Units.Balance) >> sampleFundBalance
        1 * kprClient.personalSelection(PersonFixture.samplePerson().getPersonalCode()) >> personalSelection

        String activeFundIsin = "LV0987654321"

        Fund activeFund = Fund.builder()
                .name(activeFundName)
                .isin(activeFundIsin)
                .build()

        1 * fundRepository.findByNameIgnoreCase(activeFundName) >> activeFund

        when:
        List<FundBalance> fundBalances = service.getMyPensionAccountStatement(PersonFixture.samplePerson())
        then:
        fundBalances.size() == 3

        FundBalance activeFundBalance = fundBalances.stream().find({ fb -> fb.activeContributions })
        activeFundBalance.fund.name == activeFundName
        activeFundBalance.fund.isin == activeFundIsin
        activeFundBalance.value == BigDecimal.ZERO
    }

    FundBalance sampleFundBalance(Fund activeFund, BigDecimal value) {
        return FundBalance.builder()
                .value(value)
                .fund(activeFund)
                .build()
    }

    List<PensionAccountBalanceResponseType.Units.Balance> twoFundBalanceFromKPR() {
        PensionAccountBalanceResponseType.Units.Balance balance = new PensionAccountBalanceResponseType.Units.Balance()
        balance.setSecurityName("LHV Fund")
        balance.setAmount(new BigDecimal("15883.071"))
        balance.setNav(new BigDecimal("1.58812"))

        [balance, balance]
    }

}
