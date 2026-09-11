package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.sentry.IScope;
import io.sentry.ScopeCallback;
import io.sentry.Sentry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.MDC;

class MDCFilterTest {

  private final MDCFilter filter = new MDCFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void doFilterRegistersAndClearsThePersonalIdAroundTheChain() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getUserPrincipal()).willReturn(() -> "38888888888");
    var mdcDuringChain = new java.util.concurrent.atomic.AtomicReference<String>();
    org.mockito.BDDMockito.willAnswer(
            invocation -> {
              mdcDuringChain.set(MDC.get("personalId"));
              return null;
            })
        .given(chain)
        .doFilter(request, response);

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain.get()).isEqualTo("38888888888");
    assertThat(MDC.get("personalId")).isNull();
  }

  @Test
  void doFilterCallsTheChainEvenWithoutAPrincipal() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getUserPrincipal()).willReturn(null);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(MDC.get("personalId")).isNull();
  }

  @Test
  void doFilterNeverRegistersABlankPrincipalName() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getUserPrincipal()).willReturn((Principal) () -> "   ");
    var mdcDuringChain = new java.util.concurrent.atomic.AtomicReference<String>("untouched");
    org.mockito.BDDMockito.willAnswer(
            invocation -> {
              mdcDuringChain.set(MDC.get("personalId"));
              return null;
            })
        .given(chain)
        .doFilter(request, response);

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain.get()).isNull();
    verify(chain).doFilter(request, response);
    assertThat(MDC.get("personalId")).isNull();
  }

  @Test
  void registerPersonalIdTagsTheSentryScopeAndReturnsTrueForANonBlankId() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getUserPrincipal()).willReturn(() -> "38888888888");
    IScope scope = mock(IScope.class);

    try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
      sentry
          .when(() -> Sentry.configureScope(any(ScopeCallback.class)))
          .thenAnswer(
              invocation -> {
                ScopeCallback callback = invocation.getArgument(0);
                callback.run(scope);
                return null;
              });

      filter.doFilter(request, response, chain);

      verify(scope).setTag("personalId", "38888888888");
    }
  }

  @Test
  void clearPersonalIdRemovesTheSentryTagAfterTheChainRuns() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    given(request.getUserPrincipal()).willReturn(() -> "38888888888");
    IScope scope = mock(IScope.class);

    try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
      sentry
          .when(() -> Sentry.configureScope(any(ScopeCallback.class)))
          .thenAnswer(
              invocation -> {
                ScopeCallback callback = invocation.getArgument(0);
                callback.run(scope);
                return null;
              });

      filter.doFilter(request, response, chain);

      verify(scope).removeTag("personalId");
    }
  }
}
