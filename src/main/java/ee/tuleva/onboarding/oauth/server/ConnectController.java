package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.oauth.server.ConnectEntryPoint.BROWSER_BINDING_COOKIE;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/connect")
@RequiredArgsConstructor
class ConnectController {

  private final ConnectService connectService;

  @GetMapping("/{id}")
  ConnectRequestResponse details(
      @PathVariable UUID id,
      @CookieValue(name = BROWSER_BINDING_COOKIE, required = false)
          @Nullable String browserBinding) {
    return connectService.details(id, requireBrowserBinding(browserBinding));
  }

  @PostMapping("/{id}/approve")
  RedirectResponse approve(
      @PathVariable UUID id,
      @CookieValue(name = BROWSER_BINDING_COOKIE, required = false) @Nullable String browserBinding,
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return connectService.approve(person, id, requireBrowserBinding(browserBinding));
  }

  @PostMapping("/{id}/deny")
  RedirectResponse deny(
      @PathVariable UUID id,
      @CookieValue(name = BROWSER_BINDING_COOKIE, required = false)
          @Nullable String browserBinding) {
    return connectService.deny(id, requireBrowserBinding(browserBinding));
  }

  private static String requireBrowserBinding(@Nullable String browserBinding) {
    if (browserBinding == null) {
      throw ConnectRejectedException.requestNotFound();
    }
    return browserBinding;
  }
}
