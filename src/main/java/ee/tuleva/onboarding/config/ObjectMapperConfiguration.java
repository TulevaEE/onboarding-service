package ee.tuleva.onboarding.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class ObjectMapperConfiguration {

  @Bean
  public JsonMapperBuilderCustomizer customizeObjectMapper() {
    return builder ->
        builder
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            // Jackson 3 defaults to failing on null for primitives (Jackson 2 allowed it).
            // External APIs (e.g. EPIS ContactDetails) may return null for primitive boolean
            // fields.
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
  }
}
