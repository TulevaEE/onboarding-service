package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.INVALID;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.populationregister.PersonResponse.Citizenship;
import ee.tuleva.onboarding.populationregister.PersonResponse.Code;
import ee.tuleva.onboarding.populationregister.PersonResponse.Custody;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonMapperTest {

  @Test
  void citizenshipWithoutACountryMapsToNoCitizenship() {
    var person = toPersonWithCitizenship(new Citizenship(null));

    assertThat(person.citizenship()).isNull();
    assertThat(person.citizenships()).isEmpty();
  }

  @Test
  void blankCitizenshipCountryCodeMapsToNoCitizenship() {
    var person = toPersonWithCitizenship(new Citizenship(new Code("", "unknown")));

    assertThat(person.citizenship()).isNull();
    assertThat(person.citizenships()).isEmpty();
  }

  @Test
  void unmappedNumericCitizenshipCodeMapsToNoCitizenship() {
    var person = toPersonWithCitizenship(new Citizenship(new Code("999", "unknown")));

    assertThat(person.citizenship()).isNull();
    assertThat(person.citizenships()).isEmpty();
  }

  @Test
  void mappedNumericCitizenshipCodeMapsToAlpha2() {
    var person = toPersonWithCitizenship(new Citizenship(new Code("233", "Eesti")));

    assertThat(person.citizenship()).isEqualTo("EE");
    assertThat(person.citizenships()).containsExactly("EE");
  }

  @Test
  void brazilianNumericCitizenshipCodeMapsToAlpha2() {
    var person = toPersonWithCitizenship(new Citizenship(new Code("076", "Brasiilia")));

    assertThat(person.citizenship()).isEqualTo("BR");
    assertThat(person.citizenships()).containsExactly("BR");
  }

  @Test
  void custodyWithNonValidStatusCodeIsInvalid() {
    var response =
        response(new Custody(code("H20"), code("H2"), "38888888888", code("E"), "JAAN", "TAMM"));

    assertThat(PersonMapper.toCustodyRights(response))
        .containsExactly(
            new CustodyRight("38888888888", PROPERTY_CUSTODY, INVALID, ALIVE, "Jaan", "Tamm"));
  }

  @Test
  void custodyWithValidStatusCodeIsValid() {
    var response =
        response(new Custody(code("H10"), code("H1"), "38888888888", code("E"), "JAAN", "TAMM"));

    assertThat(PersonMapper.toCustodyRights(response))
        .containsExactly(
            new CustodyRight("38888888888", PERSONAL_CUSTODY, VALID, ALIVE, "Jaan", "Tamm"));
  }

  @Test
  void guardianValidityFollowsTheStatusCode() {
    var valid = new Custody(code("H20"), code("H1"), "38888888888", code("E"), null, null);
    var invalid = new Custody(code("H20"), code("X"), "38888888888", code("E"), null, null);

    assertThat(PersonMapper.toGuardians(response(valid, invalid)))
        .containsExactly(
            new Guardian("38888888888", PROPERTY_CUSTODY, VALID, ALIVE),
            new Guardian("38888888888", PROPERTY_CUSTODY, INVALID, ALIVE));
  }

  private static PopulationRegisterPerson toPersonWithCitizenship(Citizenship citizenship) {
    return PersonMapper.toPerson(
        new PersonResponse(
            "38888888888", "JAAN", "TAMM", null, code("E"), citizenship, null, null));
  }

  private static PersonResponse response(Custody... custodies) {
    return new PersonResponse(
        "48888888888", "MARI", "TAMM", null, code("E"), null, null, List.of(custodies));
  }

  private static Code code(String value) {
    return new Code(value, null);
  }
}
