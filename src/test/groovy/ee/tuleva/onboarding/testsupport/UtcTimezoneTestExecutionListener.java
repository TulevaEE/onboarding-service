package ee.tuleva.onboarding.testsupport;

import java.util.TimeZone;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class UtcTimezoneTestExecutionListener extends AbstractTestExecutionListener {

  private static final TimeZone ORIGINAL_TIMEZONE = TimeZone.getDefault();

  @Override
  public void beforeTestClass(TestContext testContext) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @Override
  public void afterTestClass(TestContext testContext) {
    TimeZone.setDefault(ORIGINAL_TIMEZONE);
  }
}
