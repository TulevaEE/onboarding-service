package ee.tuleva.onboarding.auth.browser;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;

class RememberedBrowserPurgeJobTest {

  private final ExpiringRememberedEntries smartIdAccounts = mock(ExpiringRememberedEntries.class);
  private final ExpiringRememberedEntries otherEntries = mock(ExpiringRememberedEntries.class);
  private final RememberedBrowsers browsers = mock(RememberedBrowsers.class);
  private final RememberedBrowserPurgeJob job =
      new RememberedBrowserPurgeJob(List.of(smartIdAccounts, otherEntries), browsers);

  @Test
  void erasesEveryExpiredEntryAndThenEveryExpiredBrowser() {
    given(smartIdAccounts.removeExpired()).willReturn(2);
    given(otherEntries.removeExpired()).willReturn(1);

    job.eraseWhatIsPastItsValidity();

    var order = inOrder(smartIdAccounts, otherEntries, browsers);
    order.verify(smartIdAccounts).removeExpired();
    order.verify(otherEntries).removeExpired();
    order.verify(browsers).removeExpired();
  }
}
