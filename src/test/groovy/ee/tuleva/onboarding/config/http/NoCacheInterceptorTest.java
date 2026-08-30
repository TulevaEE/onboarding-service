package ee.tuleva.onboarding.config.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

class NoCacheInterceptorTest {

  private final NoCacheInterceptor interceptor = new NoCacheInterceptor();

  static class Controller {
    @NoCache
    void noCacheMethod() {}

    void cachedMethod() {}
  }

  @Test
  void preHandleSetsNoStoreForAnnotatedHandlersAndContinues() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    HandlerMethod handlerMethod = handlerMethodFor("noCacheMethod");

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isTrue();
    verify(response).setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  @Test
  void preHandleLeavesCachingHeadersAloneForUnannotatedHandlers() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    HandlerMethod handlerMethod = handlerMethodFor("cachedMethod");

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isTrue();
    verify(response, never()).setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  @Test
  void preHandleIgnoresNonHandlerMethodHandlers() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isTrue();
    verify(response, never()).setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  private static HandlerMethod handlerMethodFor(String methodName) throws Exception {
    Method method = Controller.class.getDeclaredMethod(methodName);
    return new HandlerMethod(new Controller(), method);
  }
}
