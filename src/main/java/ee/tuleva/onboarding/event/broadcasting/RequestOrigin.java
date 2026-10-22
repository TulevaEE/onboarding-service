package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEvent.IP_ADDRESS;
import static ee.tuleva.onboarding.event.TrackableEvent.USER_AGENT;

import ee.tuleva.onboarding.auth.ClientConnection;
import java.util.HashMap;
import java.util.Map;

final class RequestOrigin {

  private RequestOrigin() {}

  static Map<String, Object> readOnTheRequestThread(ClientConnection clientConnection) {
    Map<String, Object> origin = new HashMap<>();
    clientConnection.ipAddress().ifPresent(ipAddress -> origin.put(IP_ADDRESS, ipAddress));
    clientConnection.userAgent().ifPresent(userAgent -> origin.put(USER_AGENT, userAgent));
    return origin;
  }
}
