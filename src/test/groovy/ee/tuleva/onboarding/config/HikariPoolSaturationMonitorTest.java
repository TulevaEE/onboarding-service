package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zaxxer.hikari.HikariPoolMXBean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class HikariPoolSaturationMonitorTest {

  private static final int THRESHOLD = 5;

  private final HikariPoolSaturationMonitor monitor =
      new HikariPoolSaturationMonitor(mock(DataSource.class), THRESHOLD);

  @Test
  void saturatedWhenPendingConnectionsReachThreshold() {
    HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
    given(pool.getThreadsAwaitingConnection()).willReturn(THRESHOLD);

    assertThat(monitor.isSaturated(pool)).isTrue();
  }

  @Test
  void notSaturatedWhenPendingConnectionsBelowThreshold() {
    HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
    given(pool.getThreadsAwaitingConnection()).willReturn(THRESHOLD - 1);

    assertThat(monitor.isSaturated(pool)).isFalse();
  }

  @Test
  void notSaturatedWhenPoolUnavailable() {
    assertThat(monitor.isSaturated(null)).isFalse();
  }
}
