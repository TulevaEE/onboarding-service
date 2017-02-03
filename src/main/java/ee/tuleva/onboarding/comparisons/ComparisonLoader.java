package ee.tuleva.onboarding.comparisons;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

@Slf4j
public class ComparisonLoader implements ApplicationListener<ContextRefreshedEvent> {

    public void onApplicationEvent(ContextRefreshedEvent event) {
        EstonianFeeFinderService.updateFeesFromPensionSystem();
    }
}
