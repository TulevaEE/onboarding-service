package ee.tuleva.onboarding.party;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.aml.RegisterRequester;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AutomaticGuardianLookupRequesterTest {

  private static final String MINOR = "61506150006";
  private static final String DESIGNATED = "EE38812121215";

  private final MinorGuardianLookupService service = mock(MinorGuardianLookupService.class);
  private final RegisterRequester registerRequester = mock(RegisterRequester.class);

  @Test
  void asksAsTheRequesterAmlServiceDesignates() {
    given(registerRequester.designatedXRoadUserId()).willReturn(Optional.of(DESIGNATED));

    contextRunner()
        .run(
            context -> {
              context.getBean(AutomaticGuardianLookups.class).lookUpOnce(MINOR);
              verify(service).lookUpChildOnce(DESIGNATED, MINOR);
            });
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withBean(RegisterRequester.class, () -> registerRequester)
        .withBean(MinorGuardianLookupService.class, () -> service)
        .withBean(
            AutomaticGuardianLookupCandidates.class,
            () -> mock(AutomaticGuardianLookupCandidates.class))
        .withBean(GuardianLookupRepository.class, () -> mock(GuardianLookupRepository.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withUserConfiguration(AutomaticGuardianLookups.class);
  }
}
