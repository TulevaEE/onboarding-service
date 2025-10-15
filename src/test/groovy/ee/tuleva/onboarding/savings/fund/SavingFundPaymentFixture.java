package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.CREATED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class SavingFundPaymentFixture {

  public static SavingFundPayment.SavingFundPaymentBuilder aPayment() {
    return SavingFundPayment.builder()
        .id(UUID.randomUUID())
        .status(CREATED)
        .amount(new BigDecimal("100.00"))
        .remitterIban("EE123456789012345678")
        .remitterName("Test Remitter")
        .beneficiaryIban("EE987654321098765432")
        .beneficiaryName("Test Beneficiary")
        .description("Test payment")
        .createdAt(Instant.now())
        .statusChangedAt(Instant.now());
  }
}
