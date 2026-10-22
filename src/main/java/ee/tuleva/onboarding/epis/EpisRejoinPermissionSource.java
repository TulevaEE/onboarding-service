package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.epis.EpisResponses.requireBody;
import static org.springframework.http.HttpMethod.GET;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Profile("!mock")
class EpisRejoinPermissionSource implements RejoinPermissionSource {

  private final RestTemplate episRestTemplate;
  private final EpisRequestHeaders requestHeaders;
  private final String episServiceUrl;

  EpisRejoinPermissionSource(
      @Qualifier("episRestTemplate") RestTemplate episRestTemplate,
      EpisRequestHeaders requestHeaders,
      @Value("${epis.service.url}") String episServiceUrl) {
    this.episRestTemplate = episRestTemplate;
    this.requestHeaders = requestHeaders;
    this.episServiceUrl = episServiceUrl;
  }

  @Override
  public RejoinPermission getRejoinPermission() {
    return requireBody(
        episRestTemplate.exchange(
            episServiceUrl + "/rejoin-permission",
            GET,
            requestHeaders.userEntity(),
            RejoinPermission.class),
        "rejoin-permission");
  }
}
