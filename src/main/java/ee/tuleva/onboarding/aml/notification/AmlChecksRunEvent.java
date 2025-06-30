package ee.tuleva.onboarding.aml.notification;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillar;
import java.util.List;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AmlChecksRunEvent extends ApplicationEvent {

  private final int numberOfRecords;

  public AmlChecksRunEvent(Object source, List<AnalyticsRecentThirdPillar> records) {
    super(source);
    this.numberOfRecords = records.size();
  }
}
