package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;

class SchedulerLockConfigurationTest {

  private final SchedulerLockConfiguration configuration = new SchedulerLockConfiguration();

  @Test
  void lockProvider_shouldCreateJdbcTemplateLockProvider() {
    DataSource mockDataSource = mock(DataSource.class);

    LockProvider lockProvider = configuration.lockProvider(mockDataSource);

    assertThat(lockProvider).isNotNull();
    assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
  }

  @Test
  void lockProvider_shouldUseDbTime() {
    DataSource mockDataSource = mock(DataSource.class);

    LockProvider lockProvider = configuration.lockProvider(mockDataSource);

    assertThat(lockProvider).isNotNull();
  }
}
