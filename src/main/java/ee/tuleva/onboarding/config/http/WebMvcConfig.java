package ee.tuleva.onboarding.config.http;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
class WebMvcConfig implements WebMvcConfigurer {

  private final NoCacheInterceptor noCacheInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(noCacheInterceptor);
  }
}
