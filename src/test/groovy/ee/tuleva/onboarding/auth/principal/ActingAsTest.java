package ee.tuleva.onboarding.auth.principal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ActingAsTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void serializesWithUnifiedCodeField() throws Exception {
    assertThat(mapper.writeValueAsString(new ActingAs.Person("38501010000")))
        .contains("\"type\":\"PERSON\"")
        .contains("\"code\":\"38501010000\"");

    assertThat(mapper.writeValueAsString(new ActingAs.Company("12345678")))
        .contains("\"type\":\"COMPANY\"")
        .contains("\"code\":\"12345678\"");
  }

  @Test
  void deserializesFromJson() {
    ActingAs person =
        mapper.readValue(
            """
            {"type":"PERSON","code":"38501010000"}""",
            ActingAs.class);
    assertThat(person).isEqualTo(new ActingAs.Person("38501010000"));
    assertThat(person.code()).isEqualTo("38501010000");

    ActingAs company =
        mapper.readValue(
            """
            {"type":"COMPANY","code":"12345678"}""",
            ActingAs.class);
    assertThat(company).isEqualTo(new ActingAs.Company("12345678"));
    assertThat(company.code()).isEqualTo("12345678");
  }

  @Test
  void roundTripsViaMapConversion() {
    var company = new ActingAs.Company("12345678");
    var map = mapper.convertValue(company, java.util.Map.class);
    assertThat(map.get("type")).isEqualTo("COMPANY");
    assertThat(map.get("code")).isEqualTo("12345678");

    ActingAs result = mapper.convertValue(map, ActingAs.class);
    assertThat(result).isEqualTo(company);
  }
}
