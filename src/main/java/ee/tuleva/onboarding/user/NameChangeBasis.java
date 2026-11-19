package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.user.NameChangeSource.ADMIN;
import static ee.tuleva.onboarding.user.NameChangeSource.POPULATION_REGISTER;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import ee.tuleva.onboarding.auth.GrantType;
import org.jspecify.annotations.Nullable;

public record NameChangeBasis(NameChangeSource source, @Nullable String documentReference) {

  public static NameChangeBasis login(GrantType grantType) {
    return new NameChangeBasis(NameChangeSource.loginWith(grantType), null);
  }

  public static NameChangeBasis populationRegister() {
    return new NameChangeBasis(POPULATION_REGISTER, null);
  }

  public static NameChangeBasis admin(@Nullable String documentReference) {
    return new NameChangeBasis(ADMIN, trimToNull(documentReference));
  }
}
