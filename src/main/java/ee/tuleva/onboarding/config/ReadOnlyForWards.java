package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.auth.authority.Authority.WARD;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

record ReadOnlyForWards(AuthorizationManager<RequestAuthorizationContext> otherwise)
    implements AuthorizationManager<RequestAuthorizationContext> {

  static ReadOnlyForWards unlessWard(AuthorizationManager<RequestAuthorizationContext> otherwise) {
    return new ReadOnlyForWards(otherwise);
  }

  @Override
  public @Nullable AuthorizationResult authorize(
      Supplier<? extends @Nullable Authentication> authentication,
      RequestAuthorizationContext context) {
    if (isWard(authentication.get())) {
      return new WardIsReadOnly();
    }
    return otherwise.authorize(authentication, context);
  }

  private static boolean isWard(@Nullable Authentication authentication) {
    return authentication != null
        && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(WARD::equals);
  }

  static final class WardIsReadOnly extends AuthorizationDecision {

    WardIsReadOnly() {
      super(false);
    }
  }
}
