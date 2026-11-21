package ee.tuleva.onboarding.fund.fees;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundFeeSyncJobTest {

  private static final Clock SEVEN_PAST_MIDNIGHT_IN_TALLINN_ON_JUNE_4 =
      Clock.fixed(Instant.parse("2026-06-03T21:07:00Z"), UTC);
  private static final LocalDate JUNE_4 = LocalDate.parse("2026-06-04");

  @Mock private SavingsFundManagementFeeSync savingsFundManagementFeeSync;
  @Mock private SavingsFundOngoingChargesSync savingsFundOngoingChargesSync;

  @Test
  void syncsTheManagementFeeAndThenTheKidFigureOnTodaysDateInTallinnNotInUtc() {
    job().sync();

    var inOrder = inOrder(savingsFundManagementFeeSync, savingsFundOngoingChargesSync);
    inOrder.verify(savingsFundManagementFeeSync).syncToTheRatesInForceOn(JUNE_4);
    inOrder.verify(savingsFundOngoingChargesSync).syncToTheKidsInForceOn(JUNE_4);
  }

  @Test
  void aFailingManagementFeeSyncIsLoggedAndTheOngoingChargesSyncStillRuns() {
    willThrow(new RuntimeException("boom"))
        .given(savingsFundManagementFeeSync)
        .syncToTheRatesInForceOn(any());

    assertThatCode(() -> job().sync()).doesNotThrowAnyException();
    verify(savingsFundOngoingChargesSync).syncToTheKidsInForceOn(JUNE_4);
  }

  @Test
  void aFailingOngoingChargesSyncIsLoggedRatherThanThrownIntoTheScheduler() {
    willThrow(new RuntimeException("boom"))
        .given(savingsFundOngoingChargesSync)
        .syncToTheKidsInForceOn(any());

    assertThatCode(() -> job().sync()).doesNotThrowAnyException();
  }

  private SavingsFundFeeSyncJob job() {
    return new SavingsFundFeeSyncJob(
        savingsFundManagementFeeSync,
        savingsFundOngoingChargesSync,
        SEVEN_PAST_MIDNIGHT_IN_TALLINN_ON_JUNE_4);
  }
}
