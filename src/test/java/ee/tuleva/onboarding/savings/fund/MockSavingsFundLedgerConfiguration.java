package ee.tuleva.onboarding.savings.fund;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import java.math.BigDecimal;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MockSavingsFundLedgerConfiguration {

  @Bean
  @Primary
  public SavingsFundLedger savingsFundLedger() {
    var ledger = mock(SavingsFundLedger.class);

    // Configure the mock to throw an exception for amounts of 999.00 to simulate failures
    doThrow(new RuntimeException("Ledger error"))
        .when(ledger)
        .reservePaymentForSubscription(
            any(), argThat(amount -> amount.compareTo(new BigDecimal("999.00")) == 0));

    return ledger;
  }
}
