package ee.tuleva.onboarding.config;

import lombok.RequiredArgsConstructor;
import org.digidoc4j.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DigiDocTslRefreshJob {

  private final Configuration digiDocConfiguration;

  @Async
  @EventListener(ApplicationReadyEvent.class)
  public void refreshTsl() {
    digiDocConfiguration.getTSL().refresh();
  }
}
