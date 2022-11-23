package ee.tuleva.onboarding.auth.principal;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class ServiceSecurityContextUtil {

  public static SecurityContext createServiceSecurityContext() {
    SecurityContext context = SecurityContextHolder.createEmptyContext();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "onboarding-service", null, AuthorityUtils.createAuthorityList("ROLE_SERVICE"));
    context.setAuthentication(authentication);

    return context;
  }
}
