package ee.tuleva.onboarding.fund.fees;

import java.math.BigDecimal;
import java.util.regex.Pattern;

record PensionikeskusFeeRow(String fundName, BigDecimal rate) {

  private static final char NON_BREAKING_SPACE = (char) 0xA0;
  private static final Pattern PERCENT_VALUE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*%?");

  static PensionikeskusFeeRow of(String fundName, String rawPercent) {
    String cleaned = rawPercent.replace(NON_BREAKING_SPACE, ' ').strip();
    var matcher = PERCENT_VALUE.matcher(cleaned);
    if (!matcher.matches()) {
      throw new NumberFormatException("Not a percent value: " + rawPercent);
    }
    return new PensionikeskusFeeRow(
        fundName.replace(NON_BREAKING_SPACE, ' ').strip(),
        new BigDecimal(matcher.group(1).replace(',', '.')).movePointLeft(2));
  }
}
