package ee.tuleva.onboarding.income

import ee.eesti.xtee6.kpr.PensionAccountTransactionResponseType
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.kpr.KPRClient
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class IncomeControllerSpec extends BaseControllerSpec {

    KPRClient xRoadClient = Mock(KPRClient)
    IncomeController controller = new IncomeController(xRoadClient);

    MockMvc mockMvc

    def setup() {
        mockMvc = getMockMvc(controller)
    }

    def "/average-salary endpoint works"() {
        given:
            1 * xRoadClient.pensionAccountTransaction(_, _) >> getKPRTransactions()
        expect:
        mockMvc.perform(get("/v1/average-salary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.currency', is("EUR")))
                .andExpect(jsonPath('$.amount', is(2000.0d)))
    }

    PensionAccountTransactionResponseType getKPRTransactions() {
        PensionAccountTransactionResponseType res = new PensionAccountTransactionResponseType()
        res.setMoney(new PensionAccountTransactionResponseType.Money())

        PensionAccountTransactionResponseType.Money.Transaction trn = new PensionAccountTransactionResponseType.Money.Transaction()
        trn.setSum(new BigDecimal("120").multiply(new BigDecimal("12")))
        trn.setCurrency("EUR")
        res.getMoney().getTransaction().add(trn)
        res
    }
}
