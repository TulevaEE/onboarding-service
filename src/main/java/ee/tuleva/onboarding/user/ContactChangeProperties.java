package ee.tuleva.onboarding.user;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contact-change")
record ContactChangeProperties(Duration replacedEmailNoticeWindow) {

  ContactChangeProperties {
    if (replacedEmailNoticeWindow == null || !replacedEmailNoticeWindow.isPositive()) {
      throw new IllegalStateException(
          "Contact change misconfigured: property=contact-change.replaced-email-notice-window,"
              + " value="
              + replacedEmailNoticeWindow);
    }
  }
}
