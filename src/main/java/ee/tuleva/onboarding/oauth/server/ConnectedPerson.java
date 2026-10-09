package ee.tuleva.onboarding.oauth.server;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

record ConnectedPerson(UUID subjectId, String personalCode) implements Principal {

  static Authentication authenticated(UUID subjectId, String personalCode) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new ConnectedPerson(subjectId, personalCode), null, List.of());
  }

  static ConnectedPerson of(Authentication authentication) {
    return (ConnectedPerson) requireNonNull(authentication.getPrincipal());
  }

  @Override
  public String getName() {
    return subjectId.toString();
  }

  @Override
  public String toString() {
    return "ConnectedPerson[subjectId=" + subjectId + "]";
  }
}
