package ee.tuleva.onboarding.config;

import tools.jackson.databind.json.JsonMapper;

public final class JsonMapperFixture {

  private JsonMapperFixture() {}

  public static JsonMapper jsonMapper() {
    var builder = JsonMapper.builder();
    new ObjectMapperConfiguration().customizeObjectMapper().customize(builder);
    return builder.build();
  }
}
