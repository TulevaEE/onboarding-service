package ee.tuleva.onboarding.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class ScheduledMinorGuardianLookupJobTest {

  private static final String REQUESTER = "38001085718";

  @Mock private MinorGuardianLookupService service;

  @Test
  void looksUpOneBatchAsTheRequesterAtTheConfiguredPace() {
    var properties = new MinorGuardianLookupProperties(REQUESTER, 1000, Duration.ofSeconds(1));
    given(service.lookUpMinorThirdPillarHolders(REQUESTER, 1000, Duration.ofSeconds(1)))
        .willReturn(new MinorGuardianLookupRun(1000, 0, 4272));

    new ScheduledMinorGuardianLookupJob(service, properties).lookUpMinorGuardians();

    verify(service).lookUpMinorThirdPillarHolders(REQUESTER, 1000, Duration.ofSeconds(1));
  }

  @Test
  void staysOffUntilARequesterIsConfigured() {
    contextRunner()
        .run(context -> assertThat(context).doesNotHaveBean(ScheduledMinorGuardianLookupJob.class));
  }

  @Test
  void runsOnceARequesterIsConfiguredWithADefaultBatchAndPace() {
    contextRunner()
        .withPropertyValues("guardian-lookup.requester-personal-code=" + REQUESTER)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ScheduledMinorGuardianLookupJob.class);
              assertThat(context.getBean(MinorGuardianLookupProperties.class))
                  .isEqualTo(
                      new MinorGuardianLookupProperties(REQUESTER, 1000, Duration.ofSeconds(1)));
            });
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withBean(MinorGuardianLookupService.class, () -> service)
        .withUserConfiguration(ScheduledMinorGuardianLookupJob.class);
  }
}
