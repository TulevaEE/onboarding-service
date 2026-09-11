package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class HikariPoolSaturationMonitorTest {

  private static final int THRESHOLD = 5;

  private final HikariPoolSaturationMonitor monitor =
      new HikariPoolSaturationMonitor(mock(DataSource.class), THRESHOLD);

  private ListAppender<ILoggingEvent> logAppender;
  private Logger logger;

  @BeforeEach
  void attachLogAppender() {
    logger = (Logger) LoggerFactory.getLogger(HikariPoolSaturationMonitor.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(logAppender);
  }

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

  @Test
  void monitorDoesNothingWhenThePoolIsUnavailable() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    given(dataSource.unwrap(HikariDataSource.class))
        .willThrow(new SQLException("not a Hikari datasource"));
    HikariPoolSaturationMonitor unavailableMonitor =
        new HikariPoolSaturationMonitor(dataSource, THRESHOLD);

    unavailableMonitor.monitor();

    assertThat(logAppender.list).isEmpty();
  }

  @Test
  void monitorLogsAnErrorWhenThePoolIsSaturated() throws SQLException {
    HikariDataSource dataSource = mock(HikariDataSource.class);
    HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
    given(dataSource.unwrap(HikariDataSource.class)).willReturn(dataSource);
    given(dataSource.getHikariPoolMXBean()).willReturn(pool);
    given(pool.getThreadsAwaitingConnection()).willReturn(THRESHOLD);
    HikariPoolSaturationMonitor saturatedMonitor =
        new HikariPoolSaturationMonitor(dataSource, THRESHOLD);

    saturatedMonitor.monitor();

    assertThat(logAppender.list).hasSize(1);
    assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  void monitorDoesNotLogWhenThePoolIsBelowThreshold() throws SQLException {
    HikariDataSource dataSource = mock(HikariDataSource.class);
    HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
    given(dataSource.unwrap(HikariDataSource.class)).willReturn(dataSource);
    given(dataSource.getHikariPoolMXBean()).willReturn(pool);
    given(pool.getThreadsAwaitingConnection()).willReturn(THRESHOLD - 1);
    HikariPoolSaturationMonitor belowThresholdMonitor =
        new HikariPoolSaturationMonitor(dataSource, THRESHOLD);

    belowThresholdMonitor.monitor();

    assertThat(logAppender.list).isEmpty();
  }
}
