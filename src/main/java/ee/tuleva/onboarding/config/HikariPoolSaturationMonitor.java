package ee.tuleva.onboarding.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Leading indicator for connection-pool saturation. Samples the Hikari pool periodically and logs
 * an error (routed to Sentry -> Slack) when connection requests start queuing, so the next
 * saturation is visible before requests time out.
 */
@Slf4j
@Component
public class HikariPoolSaturationMonitor {

  private final DataSource dataSource;
  private final int pendingThreshold;

  public HikariPoolSaturationMonitor(
      DataSource dataSource,
      @Value("${hikari.saturation.pending-threshold:5}") int pendingThreshold) {
    this.dataSource = dataSource;
    this.pendingThreshold = pendingThreshold;
  }

  @Scheduled(fixedRateString = "30s")
  public void monitor() {
    HikariPoolMXBean pool = poolMXBean();
    if (isSaturated(pool)) {
      log.error(
          "Hikari pool saturated: pending={}, active={}, total={}, threshold={}",
          pool.getThreadsAwaitingConnection(),
          pool.getActiveConnections(),
          pool.getTotalConnections(),
          pendingThreshold);
    }
  }

  boolean isSaturated(HikariPoolMXBean pool) {
    return pool != null && pool.getThreadsAwaitingConnection() >= pendingThreshold;
  }

  private HikariPoolMXBean poolMXBean() {
    try {
      return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
    } catch (SQLException e) {
      return null;
    }
  }
}
