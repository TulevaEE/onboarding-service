package ee.tuleva.onboarding.fund.fees;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundManagementFeeSyncJobTest {

  private static final Clock SEVEN_PAST_MIDNIGHT_IN_TALLINN_ON_JUNE_4 =
      Clock.fixed(Instant.parse("2026-06-03T21:07:00Z"), UTC);

  @Mock private SavingsFundManagementFeeSync savingsFundManagementFeeSync;

  @Test
  void syncsToTheRatesInForceOnTodaysDateInTallinnNotInUtc() {
    job().sync();

    verify(savingsFundManagementFeeSync).syncToTheRatesInForceOn(LocalDate.parse("2026-06-04"));
  }

  @Test
  void aFailingSyncIsLoggedRatherThanThrownIntoTheScheduler() {
    willThrow(new RuntimeException("boom"))
        .given(savingsFundManagementFeeSync)
        .syncToTheRatesInForceOn(any());

    assertThatCode(() -> job().sync()).doesNotThrowAnyException();
  }

  private SavingsFundManagementFeeSyncJob job() {
    return new SavingsFundManagementFeeSyncJob(
        savingsFundManagementFeeSync, SEVEN_PAST_MIDNIGHT_IN_TALLINN_ON_JUNE_4);
  }
}
