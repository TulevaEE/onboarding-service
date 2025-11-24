package ee.tuleva.onboarding.savings.fund;

import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MockSavingsFundLedgerConfiguration {

  @Bean
  @Primary
  public SavingsFundLedger savingsFundLedger() {
    return mock(SavingsFundLedger.class);
  }
}
