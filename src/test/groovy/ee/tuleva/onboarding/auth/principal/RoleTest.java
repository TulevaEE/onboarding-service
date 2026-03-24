package ee.tuleva.onboarding.auth.principal;

import static ee.tuleva.onboarding.auth.role.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.role.Role;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RoleTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void serializesWithUnifiedCodeField() {
    assertThat(mapper.writeValueAsString(new Role(PERSON, "38501010000", "Jordan Valdma")))
        .contains("\"type\":\"PERSON\"")
        .contains("\"code\":\"38501010000\"");

    assertThat(mapper.writeValueAsString(new Role(LEGAL_ENTITY, "12345678", "Test Company")))
        .contains("\"type\":\"LEGAL_ENTITY\"")
        .contains("\"code\":\"12345678\"");
  }

  @Test
  void deserializesFromJson() {
    Role person =
        mapper.readValue(
            """
            {"type":"PERSON","code":"38501010000","name":"John Doe"}""",
            Role.class);
    assertThat(person).isEqualTo(new Role(PERSON, "38501010000", "John Doe"));
    assertThat(person.code()).isEqualTo("38501010000");

    Role company =
        mapper.readValue(
            """
            {"type":"LEGAL_ENTITY","code":"12345678","name":"Test Company"}""",
            Role.class);
    assertThat(company).isEqualTo(new Role(LEGAL_ENTITY, "12345678", "Test Company"));
    assertThat(company.code()).isEqualTo("12345678");
  }

  @Test
  void roundTripsViaMapConversion() {
    var company = new Role(LEGAL_ENTITY, "12345678", "Test Company");
    var map = mapper.convertValue(company, java.util.Map.class);
    assertThat(map.get("type")).isEqualTo("LEGAL_ENTITY");
    assertThat(map.get("code")).isEqualTo("12345678");

    Role result = mapper.convertValue(map, Role.class);
    assertThat(result).isEqualTo(company);
  }
}
