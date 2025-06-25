package ee.tuleva.onboarding.aml;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "aml.jobs.third-pillar.cron=*/1 * * * * *",
      "aml.jobs.third-pillar.zone=Europe/Tallinn"
    })
class ScheduledAmlCheckJobIntegrationTest {
  @Autowired ScheduledAmlCheckJob job;
  @MockitoBean AmlService amlService;

  @Test
  void scheduleRuns() {
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> verify(amlService, atLeastOnce()).runAmlChecksOnThirdPillarCustomers());
  }
}
