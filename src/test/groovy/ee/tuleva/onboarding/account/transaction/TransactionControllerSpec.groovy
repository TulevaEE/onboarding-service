package ee.tuleva.onboarding.account.transaction

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture
import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TransactionControllerSpec extends BaseControllerSpec {

  MockMvc mockMvc

  CashFlowService cashFlowService = Mock()
  TransactionController transactionController = new TransactionController(cashFlowService)

  def setup() {
    mockMvc = mockMvc(transactionController)
  }

  def "can get all transactions"() {
    given:
    def cashFlowStatement = cashFlowFixture()
    def cashFlows = cashFlowStatement.getTransactions()
    cashFlowService.getCashFlowStatement(_ as Person) >> cashFlowStatement
    expect:
    mockMvc.perform(get("/v1/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath('$[0]', is([
            amount  : cashFlows[1].amount.doubleValue(),
            currency: cashFlows[1].currency.name(),
            time    : cashFlows[1].time.toString(),
            isin    : cashFlows[1].isin,
            type    : cashFlows[1].type.toString(),
            comment : cashFlows[1].comment.toString()
        ])))
        .andExpect(jsonPath('$[1]', is([
            amount  : cashFlows[2].amount.doubleValue(),
            currency: cashFlows[2].currency.name(),
            time    : cashFlows[2].time.toString(),
            isin    : cashFlows[2].isin,
            type    : cashFlows[2].type.toString(),
            comment : cashFlows[2].comment.toString()
        ])))
        .andExpect(jsonPath('$[2]', is([
            amount  : cashFlows[0].amount.doubleValue(),
            currency: cashFlows[0].currency.name(),
            time    : cashFlows[0].time.toString(),
            isin    : cashFlows[0].isin,
            type    : cashFlows[0].type.toString(),
            comment : cashFlows[0].comment.toString()
        ])))
        .andExpect(jsonPath('$', hasSize(3)))
  }
}
