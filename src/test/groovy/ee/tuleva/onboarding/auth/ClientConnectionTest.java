package ee.tuleva.onboarding.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ClientConnectionTest {

  private final ClientConnection clientConnection = new ClientConnection();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  private void bindRequest() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  void readsTheAddressAndBrowserOfTheCurrentRequest() {
    request.setRemoteAddr("81.90.100.5");
    request.addHeader("User-Agent", "Mozilla/5.0 (iPhone)");
    bindRequest();

    assertThat(clientConnection.ipAddress()).contains("81.90.100.5");
    assertThat(clientConnection.userAgent()).contains("Mozilla/5.0 (iPhone)");
  }

  @Test
  void isEmptyOutsideOfARequest() {
    assertThat(clientConnection.ipAddress()).isEmpty();
    assertThat(clientConnection.userAgent()).isEmpty();
  }

  @Test
  void isEmptyWhenTheRequestCarriesNoBrowser() {
    request.setRemoteAddr("81.90.100.5");
    bindRequest();

    assertThat(clientConnection.userAgent()).isEmpty();
  }

  @Test
  void keepsAnOverlongBrowserStringToATwoHundredCharacterBudget() {
    request.setRemoteAddr("81.90.100.5");
    request.addHeader("User-Agent", "u".repeat(500));
    bindRequest();

    assertThat(clientConnection.userAgent()).contains("u".repeat(200));
  }
}
