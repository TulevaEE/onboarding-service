package ee.tuleva.onboarding.config;

import static com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ee.tuleva.onboarding.epis.mandate.GenericMandateCreationDto;
import ee.tuleva.onboarding.epis.mandate.GenericMandateCreationDtoDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectMapperConfiguration {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer customizeObjectMapper() {
    return jacksonObjectMapperBuilder ->
        jacksonObjectMapperBuilder
            .deserializerByType(GenericMandateCreationDto.class, new GenericMandateCreationDtoDeserializer())
            .featuresToEnable(WRITE_BIGDECIMAL_AS_PLAIN)
            .modules(new Jdk8Module(), new JavaTimeModule());
  }
}
