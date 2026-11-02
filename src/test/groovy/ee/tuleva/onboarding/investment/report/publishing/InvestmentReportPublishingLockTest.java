package ee.tuleva.onboarding.investment.report.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublishingLockTest {

  private static final Instant NOW = Instant.parse("2026-04-10T07:00:00Z");

  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock lock;
  private InvestmentReportPublishingLock publishingLock;

  @BeforeEach
  void setUp() {
    publishingLock =
        new InvestmentReportPublishingLock(lockProvider, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void aPublishRunsUnderTheOneLockEveryPublishTakesAndReleasesIt() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    var result = publishingLock.whileNoOtherPublishRuns(() -> "published");

    assertThat(result).contains("published");
    verify(lockProvider)
        .lock(
            argThat(
                configuration ->
                    configuration.getName().equals("investmentReportPublishing")
                        && configuration
                            .getLockAtMostUntil()
                            .equals(NOW.plus(Duration.ofMinutes(30)))));
    verify(lock).unlock();
  }

  @Test
  void aPublishDoesNotRunWhileAnotherHoldsTheLock() {
    given(lockProvider.lock(any())).willReturn(Optional.empty());
    var ran = new AtomicBoolean();

    var result =
        publishingLock.whileNoOtherPublishRuns(
            () -> {
              ran.set(true);
              return "published";
            });

    assertThat(result).isEmpty();
    assertThat(ran).isFalse();
  }

  @Test
  void theLockIsReleasedWhenAPublishFails() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    assertThatThrownBy(
            () ->
                publishingLock.whileNoOtherPublishRuns(
                    () -> {
                      throw new IllegalStateException("database down");
                    }))
        .isInstanceOf(IllegalStateException.class);
    verify(lock).unlock();
  }
}
