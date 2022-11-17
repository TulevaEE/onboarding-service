package ee.tuleva.onboarding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class ApiResourcesPathConfiguration {

  public static final String API_RESOURCES_REQUEST_MATCHER_BEAN = "apiPathRequestMatcher";

  @Bean(API_RESOURCES_REQUEST_MATCHER_BEAN)
  public RequestMatcher apiResources() {
    return new AntPathRequestMatcher("/v1/**");
  }
}
