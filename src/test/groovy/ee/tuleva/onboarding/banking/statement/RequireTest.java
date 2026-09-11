package ee.tuleva.onboarding.banking.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RequireTest {

  @Test
  void exactlyOne_throwsBankStatementParseExceptionForNullList() {
    assertThatThrownBy(() -> Require.exactlyOne(null, "thing"))
        .isInstanceOf(BankStatementParseException.class);
  }

  @Test
  void exactlyOne_throwsBankStatementParseExceptionForEmptyList() {
    assertThatThrownBy(() -> Require.exactlyOne(List.of(), "thing"))
        .isInstanceOf(BankStatementParseException.class);
  }

  @Test
  void exactlyOne_throwsBankStatementParseExceptionForMultipleElements() {
    assertThatThrownBy(() -> Require.exactlyOne(List.of("a", "b"), "thing"))
        .isInstanceOf(BankStatementParseException.class);
  }

  @Test
  void exactlyOne_returnsTheSingleElement() {
    assertThat(Require.exactlyOne(List.of("a"), "thing")).isEqualTo("a");
  }

  @Test
  void atMostOne_returnsNullForNullList() {
    List<String> nullList = null;
    assertThat(Require.atMostOne(nullList, "thing")).isNull();
  }

  @Test
  void atMostOne_returnsNullForEmptyList() {
    assertThat(Require.atMostOne(List.<String>of(), "thing")).isNull();
  }

  @Test
  void atMostOne_returnsTheSingleElement() {
    assertThat(Require.atMostOne(List.of("a"), "thing")).isEqualTo("a");
  }

  @Test
  void atMostOne_throwsBankStatementParseExceptionForMultipleElements() {
    assertThatThrownBy(() -> Require.atMostOne(List.of("a", "b"), "thing"))
        .isInstanceOf(BankStatementParseException.class);
  }
}
