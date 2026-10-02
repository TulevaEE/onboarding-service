package ee.tuleva.onboarding.audit.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AuditHealthService.class)
class AuditHealthServiceStartupTest {

  @MockitoBean private AuditHealthRepository auditHealthRepository;
  @Autowired private AuditHealthService auditHealthService;
  @Autowired private ConfigurableApplicationContext context;

  @Test
  void leavesTheAuditLogScanOutOfApplicationStartup() {
    verifyNoInteractions(auditHealthRepository);
  }

  @Test
  void setsTheThresholdOnceTheApplicationIsReady() {
    given(auditHealthRepository.findLongestIntervalSecondsSince(any(Instant.class)))
        .willReturn(new AuditLogInterval(600.0));
    given(auditHealthRepository.findLastAuditEventTimestamp())
        .willReturn(Optional.of(ClockHolder.clock().instant().minus(Duration.ofHours(1))));

    context.publishEvent(
        new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

    assertThat(auditHealthService.isAuditLogDelayed()).isTrue();
  }
}
