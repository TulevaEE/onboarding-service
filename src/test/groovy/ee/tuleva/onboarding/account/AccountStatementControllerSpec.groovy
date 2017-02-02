package ee.tuleva.onboarding.account

import ee.eesti.xtee6.kpr.KprV6PortType
import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType
import ee.tuleva.domain.fund.Fund
import ee.tuleva.domain.fund.FundRepository
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.xroad.XRoadClient
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AccountStatementControllerSpec extends BaseControllerSpec {

    MockMvc mockMvc

    def setup() {
        mockMvc = getMockMvc(controller)
    }

    XRoadClient xRoadClient = Mock(XRoadClient)
    FundRepository fundRepository = Mock(FundRepository)
    AccountStatementController controller = new AccountStatementController(xRoadClient, new IsinAppender(fundRepository))

    KprV6PortType port = Mock(KprV6PortType)
    PensionAccountBalanceResponseType resp = Mock(PensionAccountBalanceResponseType)
    PensionAccountBalanceResponseType.Units units = Mock(PensionAccountBalanceResponseType.Units)


    def "/pension-account-statement endpoint works"() {
        given:
            1 * xRoadClient.getPort() >> port
            1 * port.pensionAccountBalance(_) >> resp
            1 * resp.getUnits() >> units
            1 * units.getBalance() >> twoFundBalanceFromKPR()
            2 * fundRepository.findByName("LHV Fund") >> repoFund()
        expect:
            mockMvc.perform(get("/v1/pension-account-statement"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$[1].isin', is("LV0987654321")))
                .andExpect(jsonPath('$[1].manager', is("LHV")))
    }

    List<PensionAccountBalanceResponseType.Units.Balance> twoFundBalanceFromKPR() {
        PensionAccountBalanceResponseType.Units.Balance balance = new PensionAccountBalanceResponseType.Units.Balance()
        balance.setSecurityName("LHV Fund")
        balance.setAmount(new BigDecimal("15883.071"))
        balance.setNav(new BigDecimal("1.58812"))

        [balance, balance]
    }

    Fund repoFund() {
        Fund.builder()
            .name("LHV Fund")
            .isin("LV0987654321")
            .build()
    }


}
