package ee.tuleva.onboarding.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

@AuthenticationPrincipal(expression = "principal")
public @interface AuthenticatedPersonPrincipal {}
