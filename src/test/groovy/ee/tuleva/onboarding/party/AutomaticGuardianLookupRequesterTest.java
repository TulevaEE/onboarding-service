package ee.tuleva.onboarding.party;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.populationregister.PopulationRegisterMember;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AutomaticGuardianLookupRequesterTest {

  private static final String MINOR = "61506150006";

  private final MinorGuardianLookupService service = mock(MinorGuardianLookupService.class);

  @Test
  void asksAsTheXRoadMemberCodeUnlessOverridden() {
    contextRunner()
        .run(
            context -> {
              context.getBean(AutomaticGuardianLookups.class).lookUpOnce(MINOR);
              verify(service).lookUpChildOnce("00000000", MINOR);
            });
  }

  @Test
  void asksAsTheOverridingRequesterAsItIs() {
    contextRunner()
        .withPropertyValues("guardian-lookup.automatic-requester=EE00000000")
        .run(
            context -> {
              context.getBean(AutomaticGuardianLookups.class).lookUpOnce(MINOR);
              verify(service).lookUpChildOnce("EE00000000", MINOR);
            });
  }

  private ApplicationContextRunner contextRunner() {
    PopulationRegisterMember member = mock(PopulationRegisterMember.class);
    given(member.memberCode()).willReturn("00000000");
    return new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withBean("populationRegisterMember", PopulationRegisterMember.class, () -> member)
        .withBean(MinorGuardianLookupService.class, () -> service)
        .withBean(
            AutomaticGuardianLookupCandidates.class,
            () -> mock(AutomaticGuardianLookupCandidates.class))
        .withBean(ThirdPillarAnalytics.class, () -> mock(ThirdPillarAnalytics.class))
        .withBean(GuardianLookupRepository.class, () -> mock(GuardianLookupRepository.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withUserConfiguration(AutomaticGuardianLookups.class);
  }
}
