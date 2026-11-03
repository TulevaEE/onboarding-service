package ee.tuleva.onboarding.insurance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArteaContractNumberTest {

  @ParameterizedTest
  @CsvSource({
    "SP000123, SP000123",
    "sp000123, SP000123",
    "' Sp 000123 ', SP000123",
    "sp-000123, SP000123",
    "UP - 000000123, UP000000123",
    "tw 000 000 001, TW000000001"
  })
  void writesATypedContractNumberAsTheInsurerPrintsIt(String typed, String printed) {
    assertThat(ArteaContractNumber.printed(typed)).isEqualTo(printed);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "KK1",
        "KP123456",
        "SK000001",
        "SP000123",
        "LK12",
        "IK123",
        "IP1234",
        "JK12345",
        "JP654321",
        "TL123456789",
        "TW123456789",
        "NL123456789",
        "NW123456789",
        "UL123456789",
        "UP000000123",
        "sp 000123",
        "up-000000123"
      })
  void acceptsANumberFromTheSeriesTheInsurerIssues(String contractNumber) {
    assertThat(ArteaContractNumber.isInTheFormat(contractNumber)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "SP",
        "SP1234567",
        "UP000123",
        "UP0000001234",
        "TL12345678",
        "KL123456",
        "XX123456",
        "S123456",
        "12345678",
        "SP12A45",
        "SPX12345"
      })
  void refusesANumberOutsideTheSeriesTheInsurerIssues(String contractNumber) {
    assertThat(ArteaContractNumber.isInTheFormat(contractNumber)).isFalse();
  }
}
