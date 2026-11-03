package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublishingLockTest {

  private static final Instant NOW = Instant.parse("2026-04-10T07:00:00Z");

  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock lock;
  @Mock private JdbcClient jdbcClient;
  private InvestmentReportPublishingLock publishingLock;

  @BeforeEach
  void setUp() {
    publishingLock = lockFor(List.of(TUK75, TUK00, TUV100));
  }

  private InvestmentReportPublishingLock lockFor(List<TulevaFund> funds) {
    return new InvestmentReportPublishingLock(
        lockProvider,
        jdbcClient,
        Clock.fixed(NOW, ZoneOffset.UTC),
        new InvestmentReportPublishingProperties(funds));
  }

  @Test
  void aPublishRunsUnderTheOneLockForAsLongAsItsWordPressCallsCanTakeAndReleasesIt() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    var result = publishingLock.whileNoOtherPublishRuns(() -> "published", heldUntil -> "refused");

    assertThat(result).isEqualTo("published");
    verify(lockProvider)
        .lock(
            argThat(
                configuration ->
                    configuration.getName().equals("investmentReportPublishing")
                        && configuration
                            .getLockAtMostUntil()
                            .equals(NOW.plus(Duration.parse("PT43M9S")))));
    verify(lock).unlock();
  }

  @Test
  void theLeaseGrowsWithEveryConfiguredFund() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    lockFor(List.of(TUK75, TUK00, TUV100, TKF100))
        .whileNoOtherPublishRuns(() -> "published", heldUntil -> "refused");

    verify(lockProvider)
        .lock(
            argThat(
                configuration ->
                    configuration
                        .getLockAtMostUntil()
                        .equals(NOW.plus(Duration.parse("PT55M52S")))));
  }

  @Test
  void theLockIsReleasedWhenAPublishFails() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    assertThatThrownBy(
            () ->
                publishingLock.whileNoOtherPublishRuns(
                    () -> {
                      throw new IllegalStateException("database down");
                    },
                    heldUntil -> "refused"))
        .isInstanceOf(IllegalStateException.class);
    verify(lock).unlock();
  }
}
