package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountPrecisionValidatorTest {

  Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void eurAmount_withTwoDecimalPlaces_isValid() {
    var entry = LedgerEntry.builder().amount(new BigDecimal("100.50")).assetType(EUR).build();

    assertThat(validator.validate(entry)).isEmpty();
  }

  @Test
  void eurAmount_withNoDecimalPlaces_isValid() {
    var entry = LedgerEntry.builder().amount(new BigDecimal("100")).assetType(EUR).build();

    assertThat(validator.validate(entry)).isEmpty();
  }

  @Test
  void eurAmount_withTrailingZeros_isValid() {
    var entry = LedgerEntry.builder().amount(new BigDecimal("0.060")).assetType(EUR).build();

    assertThat(validator.validate(entry)).isEmpty();
  }

  @Test
  void eurAmount_withMoreThanTwoSignificantDecimalPlaces_isInvalid() {
    var entry = LedgerEntry.builder().amount(new BigDecimal("100.555")).assetType(EUR).build();

    assertThat(validator.validate(entry)).isNotEmpty();
  }

  @Test
  void negativeEurAmount_withCorrectPrecision_isValid() {
    var entry = LedgerEntry.builder().amount(new BigDecimal("-100.50")).assetType(EUR).build();

    assertThat(validator.validate(entry)).isEmpty();
  }

  @Test
  void negativeEurAmount_withTrailingZeros_isValid() {
    var entry = LedgerEntry.builder().amount(new BigDecimal("-0.060")).assetType(EUR).build();

    assertThat(validator.validate(entry)).isEmpty();
  }

  @Test
  void negativeEurAmount_withMoreThanTwoSignificantDecimalPlaces_isInvalid() {
    var entry = LedgerEntry.builder().amount(new BigDecimal("-100.555")).assetType(EUR).build();

    assertThat(validator.validate(entry)).isNotEmpty();
  }

  @Test
  void fundUnitAmount_withFiveDecimalPlaces_isValid() {
    var entry =
        LedgerEntry.builder().amount(new BigDecimal("10.12345")).assetType(FUND_UNIT).build();

    var violations = validator.validate(entry);

    assertThat(violations.stream().anyMatch(v -> v.getMessage().contains("decimal places")))
        .isFalse();
  }

  @Test
  void fundUnitAmount_withMoreThanFiveDecimalPlaces_isInvalid() {
    var entry =
        LedgerEntry.builder().amount(new BigDecimal("10.123456")).assetType(FUND_UNIT).build();

    assertThat(validator.validate(entry)).isNotEmpty();
  }

  @Test
  void fundUnitAmount_withFewerThanFiveDecimalPlaces_isInvalid() {
    var entry = LedgerEntry.builder().amount(new BigDecimal("10.123")).assetType(FUND_UNIT).build();

    assertThat(validator.validate(entry)).isNotEmpty();
  }

  @Test
  void fundUnitAmount_withTrailingZeros_isStillValidatedOnOriginalScale() {
    var entry =
        LedgerEntry.builder().amount(new BigDecimal("10.12300")).assetType(FUND_UNIT).build();

    assertThat(validator.validate(entry)).isEmpty();
  }

  @Test
  void fundUnitAmount_withTrailingZerosReducingSignificantDecimals_isInvalid() {
    var entry =
        LedgerEntry.builder().amount(new BigDecimal("10.1230")).assetType(FUND_UNIT).build();

    assertThat(validator.validate(entry)).isNotEmpty();
  }

  @Test
  void ledgerAccount_addEntry_rejectsAssetTypeMismatch() {
    var account = LedgerAccount.builder().assetType(EUR).build();
    var entry = LedgerEntry.builder().amount(new BigDecimal("100.00")).assetType(FUND_UNIT).build();

    assertThatThrownBy(() -> account.addEntry(entry)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ledgerAccount_addEntry_rejectsNullEntry() {
    var account = LedgerAccount.builder().assetType(EUR).build();

    assertThatThrownBy(() -> account.addEntry(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
