package ee.tuleva.onboarding.investment.event;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.event.PipelineStep.LIMIT_CHECK;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.check.limit.LimitCheckJobTestBeans;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationCompleted;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(LimitCheckJobTestBeans.class)
class NavListenerOrderIntegrationTest {

  @Autowired private ApplicationEventPublisher publisher;
  @Autowired private PipelineTracker pipelineTracker;

  @AfterEach
  void tearDown() {
    pipelineTracker.clear();
  }

  @Test
  void limitCheckListenerRunsOnNavCalculationCompleted() {
    pipelineTracker.start(PipelineRun.PipelineType.NAV, "test");

    publisher.publishEvent(new NavCalculationCompleted(List.of(TUK75)));

    var stepNames =
        pipelineTracker.current().getSteps().stream().map(PipelineRun.StepResult::getName).toList();
    assertThat(stepNames).contains(LIMIT_CHECK);
  }
}
