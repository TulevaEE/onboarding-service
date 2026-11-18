package ee.tuleva.onboarding.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MinorGuardianLookupLockTest {

  @Autowired private DataSource dataSource;

  @Test
  void letsOnlyOneLookupHoldItAtATime() {
    var lock = new MinorGuardianLookupLock(provider(lockProvider()), Clock.systemUTC());

    var first = lock.acquire();
    var whileHeld = lock.acquire();
    first.orElseThrow().unlock();
    var afterRelease = lock.acquire();
    afterRelease.ifPresent(SimpleLock::unlock);

    assertThat(first).isPresent();
    assertThat(whileHeld).isEmpty();
    assertThat(afterRelease).isPresent();
  }

  @Test
  void refusesToRunUnlockedOnANodeWithoutALockProvider() {
    var lock = new MinorGuardianLookupLock(provider(null), Clock.systemUTC());

    assertThatThrownBy(lock::acquire).isInstanceOf(IllegalStateException.class);
  }

  private LockProvider lockProvider() {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .build());
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<LockProvider> provider(LockProvider lockProvider) {
    ObjectProvider<LockProvider> provider = mock(ObjectProvider.class);
    given(provider.getIfAvailable()).willReturn(lockProvider);
    return provider;
  }
}
