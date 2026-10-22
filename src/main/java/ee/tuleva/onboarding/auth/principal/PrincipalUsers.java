package ee.tuleva.onboarding.auth.principal;

import java.util.Optional;

public interface PrincipalUsers {
  PrincipalUser findOrCreate(Person person);

  boolean isMember(Long userId);

  Optional<String> fullName(String personalCode);

  record PrincipalUser(Long id, boolean active) {}
}
