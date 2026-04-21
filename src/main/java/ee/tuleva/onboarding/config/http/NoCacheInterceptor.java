package ee.tuleva.onboarding.config.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
class NoCacheInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (handler instanceof HandlerMethod handlerMethod
        && handlerMethod.hasMethodAnnotation(NoCache.class)) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
    return true;
  }
}
