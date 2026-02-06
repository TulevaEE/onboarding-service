package ee.tuleva.onboarding.account.transaction

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.epis.cashflows.CashFlow
import org.springframework.test.web.servlet.MockMvc

import java.time.Instant

import static org.hamcrest.Matchers.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TransactionControllerSpec extends BaseControllerSpec {

  MockMvc mockMvc

  TransactionService transactionService = Mock()
  TransactionController transactionController = new TransactionController(transactionService)

  def setup() {
    mockMvc = mockMvc(transactionController)
  }

  def "delegates to transaction service"() {
    given:
    def transactions = [
        Transaction.builder()
            .amount(new BigDecimal("100.00"))
            .currency(Currency.EUR)
            .time(Instant.parse("2025-02-01T00:00:00Z"))
            .isin("EE0000003283")
            .type(CashFlow.Type.CONTRIBUTION_CASH)
            .units(new BigDecimal("10.00000"))
            .nav(new BigDecimal("10.0"))
            .build(),
        Transaction.builder()
            .amount(new BigDecimal("-50.00"))
            .currency(Currency.EUR)
            .time(Instant.parse("2025-01-01T00:00:00Z"))
            .isin("EE123")
            .type(CashFlow.Type.SUBTRACTION)
            .build()
    ]
    transactionService.getTransactions(_ as Person) >> transactions

    expect:
    mockMvc.perform(get("/v1/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath('$', hasSize(2)))
        .andExpect(jsonPath('$[0].isin', is("EE0000003283")))
        .andExpect(jsonPath('$[0].type', is("CONTRIBUTION_CASH")))
        .andExpect(jsonPath('$[0].units').value(10.0))
        .andExpect(jsonPath('$[0].nav').value(10.0))
        .andExpect(jsonPath('$[1].isin', is("EE123")))
        .andExpect(jsonPath('$[1].type', is("SUBTRACTION")))
  }
}
