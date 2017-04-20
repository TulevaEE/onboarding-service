package ee.tuleva.onboarding.config;

import com.rollbar.Rollbar;
import com.rollbar.payload.data.Person;
import com.rollbar.payload.data.Request;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@ControllerAdvice
@RequiredArgsConstructor
@Configuration
@Profile("production")
public class RollbarExceptionHandler {

  private final Environment environment;

  @Value("${logging.rollbar.accessToken:#{null}}")
  private String accessToken;

  @ExceptionHandler(value = Exception.class)
  public ModelAndView defaultErrorHandler(HttpServletRequest request, Exception ex, @AuthenticationPrincipal User user) throws Exception {

    // If the exception is annotated with @ResponseStatus rethrow it and let the framework handle it
    if (AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class) != null) {
      throw ex;
    }

    Rollbar rollbar = new Rollbar(accessToken, environment.getActiveProfiles()[0])
      .platform(System.getProperty("java.version"))
      .request(new Request()
        .url(request.getRequestURL().toString())
        .method(request.getMethod())
        .headers(headers(request))
        .queryString(request.getQueryString())
        .setGet(parameters(request))
        .userIp(InetAddress.getByName(request.getRemoteAddr())));

    if (user != null) {
      rollbar = rollbar.person(new Person(Objects.toString(user.getId()))
        .email(user.getEmail()));
    }

    rollbar.error(ex);

    throw ex;
  }

  private Map<String, String> parameters(HttpServletRequest request) {
    return request.getParameterMap().entrySet().stream()
      .collect(Collectors.toMap(
        Entry::getKey,
        e -> Arrays.toString(e.getValue())
      ));
  }

  private Map<String, String> headers(HttpServletRequest request) {
    Map<String, String> headers = new HashMap<>();

    Enumeration headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String name = (String) headerNames.nextElement();
      String value = request.getHeader(name);
      headers.put(name, value);
    }

    return headers;
  }

}