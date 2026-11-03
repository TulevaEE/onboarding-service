package ee.tuleva.onboarding.config;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.boot.availability.LivenessState.CORRECT;
import static org.springframework.boot.availability.ReadinessState.ACCEPTING_TRAFFIC;
import static org.springframework.boot.health.contributor.Status.OUT_OF_SERVICE;
import static org.springframework.boot.health.contributor.Status.UP;

import org.digidoc4j.Configuration;
import org.digidoc4j.TSLCertificateSource;
import org.digidoc4j.exceptions.TslRefreshException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.endpoint.web.AdditionalPathsMapper;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.SystemHealthDescriptor;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.application.AvailabilityHealthContributorAutoConfiguration;
import org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class TrustListReadinessTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withConfiguration(
              AutoConfigurations.of(
                  ApplicationAvailabilityAutoConfiguration.class,
                  AvailabilityHealthContributorAutoConfiguration.class,
                  AvailabilityProbesAutoConfiguration.class,
                  HealthContributorAutoConfiguration.class,
                  HealthContributorRegistryAutoConfiguration.class,
                  HealthEndpointAutoConfiguration.class))
          .withUserConfiguration(TrustListHealthIndicator.class, RootHealthWithoutTrustList.class);

  @Mock private Configuration digiDocConfiguration;
  @Mock private TSLCertificateSource tsl;

  @Test
  void readinessIsOutOfServiceUntilTheTrustListLoadsWhileRootHealthAndLivenessStayUp() {
    contextRunner.run(
        context -> {
          startedAndAcceptingTraffic(context);

          assertThat(rootStatus(context)).isEqualTo(UP);
          assertThat(groupStatus(context, "liveness")).isEqualTo(UP);
          assertThat(groupStatus(context, "readiness")).isEqualTo(OUT_OF_SERVICE);
        });
  }

  @Test
  void rootHealthStillListsTheProbeGroups() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(HealthEndpoint.class).health())
                .isInstanceOfSatisfying(
                    SystemHealthDescriptor.class,
                    root ->
                        assertThat(root.getGroups())
                            .containsExactlyInAnyOrder("liveness", "readiness")));
  }

  @Test
  void keepsTheAdditionalPathsOfTheOtherHealthGroupsWhicheverPostProcessorRunsLast() {
    contextRunner
        .withPropertyValues(
            "management.endpoint.health.group.trust.include=trustList",
            "management.endpoint.health.group.trust.additional-path=server:/trustz")
        .run(
            context ->
                assertThat(context.getBean(HealthEndpointGroups.class))
                    .isInstanceOfSatisfying(
                        AdditionalPathsMapper.class,
                        mapper ->
                            assertThat(
                                    mapper.getAdditionalPaths(
                                        HealthEndpoint.ID, WebServerNamespace.SERVER))
                                .contains("/trustz")));
  }

  @Test
  void readinessTurnsUpOnceTheStartupRefreshHasLoadedTheTrustList() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    given(tsl.getNumberOfCertificates()).willReturn(42);

    contextRunner.run(
        context -> {
          startedAndAcceptingTraffic(context);

          refreshJob(context).refreshTslOnStartup();

          assertThat(groupStatus(context, "readiness")).isEqualTo(UP);
          assertThat(rootStatus(context)).isEqualTo(UP);
        });
  }

  @Test
  void readinessStaysUpWhenAScheduledRefreshFailsAfterTheTrustListHasLoaded() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    given(tsl.getNumberOfCertificates()).willReturn(42);
    willDoNothing().willThrow(new TslRefreshException("LoTL download failed")).given(tsl).refresh();

    contextRunner.run(
        context -> {
          startedAndAcceptingTraffic(context);
          DigiDocTslRefreshJob job = refreshJob(context);
          job.refreshTslOnStartup();

          job.scheduledRefreshTsl();

          assertThat(groupStatus(context, "readiness")).isEqualTo(UP);
        });
  }

  private DigiDocTslRefreshJob refreshJob(AssertableApplicationContext context) {
    DigiDocTslRefreshJob job =
        new DigiDocTslRefreshJob(
            digiDocConfiguration, context.getBean(TrustListHealthIndicator.class));
    job.backoffBaseSeconds = 0;
    return job;
  }

  private static void startedAndAcceptingTraffic(AssertableApplicationContext context) {
    AvailabilityChangeEvent.publish(context, CORRECT);
    AvailabilityChangeEvent.publish(context, ACCEPTING_TRAFFIC);
  }

  private static Status rootStatus(AssertableApplicationContext context) {
    return context.getBean(HealthEndpoint.class).health().getStatus();
  }

  private static Status groupStatus(AssertableApplicationContext context, String group) {
    return requireNonNull(context.getBean(HealthEndpoint.class).healthForPath(group)).getStatus();
  }
}
