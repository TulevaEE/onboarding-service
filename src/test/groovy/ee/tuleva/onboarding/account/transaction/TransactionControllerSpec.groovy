package ee.tuleva.onboarding.account.transaction

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.epis.cashflows.CashFlow
import org.springframework.test.web.servlet.MockMvc

import java.time.Instant

import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
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
        new Transaction(
            new BigDecimal("100.00"),
            Currency.EUR,
            Instant.parse("2025-02-01T00:00:00Z"),
            "EE0000003283",
            CashFlow.Type.CONTRIBUTION_CASH,
            null
        ),
        new Transaction(
            new BigDecimal("-50.00"),
            Currency.EUR,
            Instant.parse("2025-01-01T00:00:00Z"),
            "EE123",
            CashFlow.Type.SUBTRACTION,
            "comment"
        )
    ]
    transactionService.getTransactions(_ as Person) >> transactions

    expect:
    mockMvc.perform(get("/v1/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath('$', hasSize(2)))
        .andExpect(jsonPath('$[0].isin', is("EE0000003283")))
        .andExpect(jsonPath('$[0].type', is("CONTRIBUTION_CASH")))
        .andExpect(jsonPath('$[1].isin', is("EE123")))
        .andExpect(jsonPath('$[1].type', is("SUBTRACTION")))
        .andExpect(jsonPath('$[1].comment', is("comment")))
  }
}
