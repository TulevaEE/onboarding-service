package ee.tuleva.onboarding.banking.seb.fetcher;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

final class SebGatewayAvailability {

  private SebGatewayAvailability() {}

  static boolean isUnavailable(Exception exception) {
    return exception instanceof HttpServerErrorException
        || exception instanceof ResourceAccessException;
  }
}
