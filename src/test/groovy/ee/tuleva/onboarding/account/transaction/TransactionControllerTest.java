package ee.tuleva.onboarding.account.transaction;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@WithMockUser
class TransactionControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TransactionService transactionService;

  @Test
  void serializesNavAndUnitsWithFullPrecision() throws Exception {
    var transaction =
        Transaction.builder()
            .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
            .amount(new BigDecimal("100.00"))
            .currency(EUR)
            .time(Instant.parse("2025-02-01T10:00:00Z"))
            .isin("EE0000003283")
            .type(CONTRIBUTION_CASH)
            .units(new BigDecimal("10.00000"))
            .nav(new BigDecimal("1.0000"))
            .build();

    when(transactionService.getTransactions(any())).thenReturn(List.of(transaction));

    mockMvc
        .perform(get("/v1/transactions"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
            [{
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "amount": 100.00,
              "currency": "EUR",
              "isin": "EE0000003283",
              "type": "CONTRIBUTION_CASH",
              "units": 10.00000,
              "nav": 1.0000
            }]
            """));
  }
}
