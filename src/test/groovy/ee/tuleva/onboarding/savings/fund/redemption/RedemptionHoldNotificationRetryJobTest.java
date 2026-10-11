package ee.tuleva.onboarding.savings.fund.redemption;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionHoldNotificationRetryJobTest {

  @Mock private RedemptionHoldService redemptionHoldService;

  @InjectMocks private RedemptionHoldNotificationRetryJob job;

  @Test
  void resendUnsentHoldNotifications_asksTheHoldServiceToRetryThem() {
    job.resendUnsentHoldNotifications();

    verify(redemptionHoldService).resendUnsentHoldNotifications();
  }
}
