package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.investment.event.PipelineTracker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TrackingDifferenceJobTestBeans {

  @Bean
  public TrackingDifferenceJob trackingDifferenceJob(
      TrackingDifferenceService trackingDifferenceService,
      TrackingDifferenceNotifier trackingDifferenceNotifier,
      PipelineTracker pipelineTracker) {
    return new TrackingDifferenceJob(
        trackingDifferenceService, trackingDifferenceNotifier, pipelineTracker);
  }
}
