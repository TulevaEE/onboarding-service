package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundOnboardingLedgerInitializerTest {

  @Mock private LedgerService ledgerService;
  @InjectMocks private SavingsFundOnboardingLedgerInitializer initializer;

  @Test
  void onOnboardingCompleted_initializesUserAccounts() {
    User user = sampleUser().build();
    var event = new SavingsFundOnboardingCompletedEvent(user);

    initializer.onOnboardingCompleted(event);

    verify(ledgerService).initializeUserAccounts(user);
  }
}
