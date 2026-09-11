package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

class InstrumentReferenceServiceClusterRefreshTest {

  private final List<InstrumentReference> tableRows =
      new ArrayList<>(List.of(reference("IE00BOOT")));

  private final InstrumentReferenceRepository repository =
      mock(InstrumentReferenceRepository.class);
  private final BenchmarkCategoryProxyRepository proxyRepository =
      mock(BenchmarkCategoryProxyRepository.class);

  @Test
  void everyInstanceRefreshesEvenWhenAnotherInstanceWonTheClusterLock() {
    given(repository.findAllByOrderByIdAsc()).willAnswer(invocation -> List.copyOf(tableRows));
    given(proxyRepository.findAll()).willReturn(List.of());

    try (var cluster = clusterOfTwoInstances()) {
      var instanceOne = cluster.getBean("instanceOne", InstrumentReferenceService.class);
      var instanceTwo = cluster.getBean("instanceTwo", InstrumentReferenceService.class);

      assertThat(instanceOne.findByIsin("IE00BOOT")).isPresent();
      assertThat(instanceTwo.findByIsin("IE00BOOT")).isPresent();

      tableRows.add(reference("IE00NEW"));

      instanceOne.scheduledRefresh();
      instanceTwo.scheduledRefresh();

      assertThat(instanceOne.findByIsin("IE00NEW")).isPresent();
      assertThat(instanceTwo.findByIsin("IE00NEW")).isPresent();
    }
  }

  private AnnotationConfigApplicationContext clusterOfTwoInstances() {
    var context = new AnnotationConfigApplicationContext();
    context.register(ClusterLockConfiguration.class);
    context.registerBean("instanceOne", InstrumentReferenceService.class, this::newInstance);
    context.registerBean("instanceTwo", InstrumentReferenceService.class, this::newInstance);
    context.refresh();
    return context;
  }

  private InstrumentReferenceService newInstance() {
    return new InstrumentReferenceService(
        new InstrumentSnapshotLoader(repository, proxyRepository), Clock.systemUTC());
  }

  private static InstrumentReference reference(String isin) {
    var reference = BeanUtils.instantiateClass(InstrumentReference.class);
    ReflectionTestUtils.setField(reference, "isin", isin);
    ReflectionTestUtils.setField(reference, "displayName", isin);
    ReflectionTestUtils.setField(reference, "eodhdTicker", isin + ".XETRA");
    ReflectionTestUtils.setField(reference, "eodhdListed", true);
    ReflectionTestUtils.setField(reference, "active", true);
    return reference;
  }

  @Configuration
  @EnableSchedulerLock(defaultLockAtMostFor = "10m")
  static class ClusterLockConfiguration {

    @Bean
    LockProvider lockProvider() {
      Set<String> lockedNames = ConcurrentHashMap.newKeySet();
      SimpleLock heldUntilLockAtLeastForElapses = () -> {};
      return configuration ->
          lockedNames.add(configuration.getName())
              ? Optional.of(heldUntilLockAtLeastForElapses)
              : Optional.empty();
    }
  }
}
