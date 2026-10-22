package ee.tuleva.onboarding.epis;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.ServiceTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EpisRequestHeaders {

  private final ServiceTokenProvider serviceTokenProvider;

  HttpEntity<String> userEntity() {
    return entityFor(userJwtToken());
  }

  HttpEntity<String> entityFor(String jwtToken) {
    return new HttpEntity<>(forToken(jwtToken));
  }

  HttpHeaders user() {
    return forToken(userJwtToken());
  }

  HttpHeaders service() {
    return forToken(serviceTokenProvider.generateServiceToken());
  }

  HttpHeaders forToken(String jwtToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("Authorization", "Bearer " + jwtToken);
    return headers;
  }

  String userJwtToken() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new IllegalStateException("No authentication present!");
    }
    return requireNonNull(
        (String) authentication.getCredentials(), "No credentials present in authentication");
  }
}
