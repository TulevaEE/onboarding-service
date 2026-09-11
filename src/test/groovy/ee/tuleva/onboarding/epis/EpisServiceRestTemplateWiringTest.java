package ee.tuleva.onboarding.epis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class EpisServiceRestTemplateWiringTest {

  @Test
  void injectsTheDedicatedEpisTemplatesAndNotThePrimaryOne() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestBeans.class)
        .withBean(EpisRequestHeaders.class)
        .withBean(EpisService.class)
        .withPropertyValues(
            "epis.service.url=http://epis", "epis.service.long-request-url=http://epis-long")
        .run(
            context -> {
              EpisService service = context.getBean(EpisService.class);

              assertThat(ReflectionTestUtils.getField(service, "episRestTemplate"))
                  .isSameAs(context.getBean("episRestTemplate"));
              assertThat(ReflectionTestUtils.getField(service, "episLongRequestRestTemplate"))
                  .isSameAs(context.getBean("episLongRequestRestTemplate"));
            });
  }

  @Configuration
  static class TestBeans {

    @Bean
    static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
      return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    @Primary
    RestTemplate restTemplate() {
      return new RestTemplate();
    }

    @Bean
    RestTemplate episRestTemplate() {
      return new RestTemplate();
    }

    @Bean
    RestTemplate episLongRequestRestTemplate() {
      return new RestTemplate();
    }

    @Bean
    JwtTokenUtil jwtTokenUtil() {
      return mock(JwtTokenUtil.class);
    }
  }
}
