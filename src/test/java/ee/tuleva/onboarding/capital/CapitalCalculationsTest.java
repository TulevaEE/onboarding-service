package ee.tuleva.onboarding.capital;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CapitalCalculationsTest {

  @Test
  void throwsIllegalArgumentExceptionWhenOwnershipUnitsIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CapitalCalculations.calculateCapitalValue(null, BigDecimal.TEN));

    assertTrue(exception.getMessage().contains("Ownership units and price must not be null"));
    assertTrue(exception.getMessage().contains("ownershipUnits=null"));
  }

  @Test
  void throwsIllegalArgumentExceptionWhenOwnershipUnitPriceIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CapitalCalculations.calculateCapitalValue(BigDecimal.TEN, null));

    assertTrue(exception.getMessage().contains("Ownership units and price must not be null"));
    assertTrue(exception.getMessage().contains("ownershipUnitPrice=null"));
  }

  @Test
  void throwsIllegalArgumentExceptionWhenBothParametersAreNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CapitalCalculations.calculateCapitalValue(null, null));

    assertTrue(exception.getMessage().contains("Ownership units and price must not be null"));
  }

  @Test
  void returnsZeroWhenOwnershipUnitsIsZero() {
    BigDecimal result =
        CapitalCalculations.calculateCapitalValue(BigDecimal.ZERO, new BigDecimal("1.567890"));

    assertEquals(0, result.compareTo(BigDecimal.ZERO));
    assertEquals(5, result.scale());
  }

  @Test
  void returnsZeroWhenOwnershipUnitPriceIsZero() {
    BigDecimal result =
        CapitalCalculations.calculateCapitalValue(new BigDecimal("100"), BigDecimal.ZERO);

    assertEquals(0, result.compareTo(BigDecimal.ZERO));
    assertEquals(5, result.scale());
  }

  @Test
  void multipliesUnitsByPriceWith5DecimalPrecision() {
    BigDecimal result =
        CapitalCalculations.calculateCapitalValue(
            new BigDecimal("1000.10"), new BigDecimal("1.567890"));

    // 1000.10 * 1.567890 = 1568.046790, rounded to 5 decimals with HALF_UP = 1568.04679
    assertEquals(new BigDecimal("1568.04679"), result);
  }

  @Test
  void appliesHalfUpRoundingAt5Decimals() {
    // 100 * 1.123456789 = 112.3456789, should round to 112.34568 (round up at 6th decimal)
    BigDecimal units = new BigDecimal("100");
    BigDecimal price = new BigDecimal("1.123456789");

    BigDecimal result = CapitalCalculations.calculateCapitalValue(units, price);

    assertEquals(new BigDecimal("112.34568"), result);
  }

  @Test
  void appliesHalfUpRoundingDownWhen6thDecimalIsLessThan5() {
    // 100 * 1.123454999 = 112.3454999, should round to 112.34550 (round down at 6th decimal)
    BigDecimal units = new BigDecimal("100");
    BigDecimal price = new BigDecimal("1.123454999");

    BigDecimal result = CapitalCalculations.calculateCapitalValue(units, price);

    assertEquals(new BigDecimal("112.34550"), result);
  }

  @Test
  void handlesExact5DecimalsWithoutRounding() {
    BigDecimal units = new BigDecimal("100");
    BigDecimal price = new BigDecimal("1.12345");

    BigDecimal result = CapitalCalculations.calculateCapitalValue(units, price);

    assertEquals(new BigDecimal("112.34500"), result);
  }

  @Test
  void handlesLargeValues() {
    BigDecimal units = new BigDecimal("999999.99");
    BigDecimal price = new BigDecimal("2.123456");

    BigDecimal result = CapitalCalculations.calculateCapitalValue(units, price);

    // 999999.99 * 2.123456 = 2123455.978774, rounded to 5 decimals with HALF_UP = 2123455.97877
    assertEquals(new BigDecimal("2123455.97877"), result);
  }

  @Test
  void resultHasExactly5DecimalPlaces() {
    BigDecimal result =
        CapitalCalculations.calculateCapitalValue(
            new BigDecimal("1900.2"), new BigDecimal("1.567890"));

    assertEquals(5, result.scale());
  }

  @Test
  void matchesRealWorldExampleFromCapitalServiceSpec() {
    // From CapitalServiceSpec: MEMBERSHIP_BONUS with 1900.00 + 0.20 units at 1.567890 price
    BigDecimal units = new BigDecimal("1900.00").add(new BigDecimal("0.20"));
    BigDecimal price = new BigDecimal("1.567890");

    BigDecimal result = CapitalCalculations.calculateCapitalValue(units, price);

    // 1900.20 * 1.567890 = 2979.304580, rounded to 5 decimals with HALF_UP = 2979.30458
    assertEquals(new BigDecimal("2979.30458"), result);
  }
}
