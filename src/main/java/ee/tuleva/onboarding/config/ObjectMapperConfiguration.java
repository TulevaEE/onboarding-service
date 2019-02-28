package ee.tuleva.onboarding.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN;

@Configuration
public class ObjectMapperConfiguration {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer customizeObjectMapper() {
    return jacksonObjectMapperBuilder ->
        jacksonObjectMapperBuilder.featuresToEnable(WRITE_BIGDECIMAL_AS_PLAIN);
  }
}