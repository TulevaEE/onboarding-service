package ee.tuleva.onboarding.config;

import static com.google.common.io.Files.touch;

import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    try {
      touch(new File("/tmp/app-initialized"));
    } catch (IOException e) {
      log.error("Could not indicate NGINX that it can start accepting traffic", e);
    }
  }
}
