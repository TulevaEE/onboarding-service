package ee.tuleva.onboarding.capital.transfer.iban;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IbanValidator implements ConstraintValidator<ValidIban, String> {

  private static final int IBAN_MIN_LENGTH = 15;
  private static final int IBAN_MAX_LENGTH = 34;

  /** 2-letter country, 2-digit checksum, alphanumerics */
  private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$");

  /** https://www.iban.com/structure */
  private static final Map<String, Integer> COUNTRY_LENGTHS =
      Map.ofEntries(
          Map.entry("AL", 28),
          Map.entry("AD", 24),
          Map.entry("AT", 20),
          Map.entry("AZ", 28),
          Map.entry("BH", 22),
          Map.entry("BE", 16),
          Map.entry("BA", 20),
          Map.entry("BR", 29),
          Map.entry("BG", 22),
          Map.entry("CR", 22),
          Map.entry("HR", 21),
          Map.entry("CY", 28),
          Map.entry("CZ", 24),
          Map.entry("FO", 18),
          Map.entry("GL", 18),
          Map.entry("DK", 18),
          Map.entry("DO", 28),
          Map.entry("EE", 20),
          Map.entry("EG", 29),
          Map.entry("FI", 18),
          Map.entry("FR", 27),
          Map.entry("GE", 22),
          Map.entry("DE", 22),
          Map.entry("GI", 23),
          Map.entry("GR", 27),
          Map.entry("GT", 28),
          Map.entry("HU", 28),
          Map.entry("IS", 26),
          Map.entry("IE", 22),
          Map.entry("IL", 23),
          Map.entry("IT", 27),
          Map.entry("JO", 30),
          Map.entry("KZ", 20),
          Map.entry("XK", 20),
          Map.entry("KW", 30),
          Map.entry("LV", 21),
          Map.entry("LB", 28),
          Map.entry("LI", 21),
          Map.entry("LT", 20),
          Map.entry("LU", 20),
          Map.entry("MK", 19),
          Map.entry("MT", 31),
          Map.entry("MR", 27),
          Map.entry("MU", 30),
          Map.entry("MD", 24),
          Map.entry("MC", 27),
          Map.entry("ME", 22),
          Map.entry("NL", 18),
          Map.entry("NO", 15),
          Map.entry("PK", 24),
          Map.entry("PS", 29),
          Map.entry("PL", 28),
          Map.entry("PT", 25),
          Map.entry("QA", 29),
          Map.entry("RO", 24),
          Map.entry("SM", 27),
          Map.entry("LC", 32),
          Map.entry("ST", 25),
          Map.entry("SA", 24),
          Map.entry("RS", 22),
          Map.entry("SK", 24),
          Map.entry("SI", 19),
          Map.entry("ES", 24),
          Map.entry("SE", 24),
          Map.entry("CH", 21),
          Map.entry("TL", 23),
          Map.entry("TN", 24),
          Map.entry("TR", 26),
          Map.entry("AE", 23),
          Map.entry("GB", 22),
          Map.entry("VA", 22),
          Map.entry("VG", 24),
          Map.entry("UA", 29),
          Map.entry("SC", 31),
          Map.entry("IQ", 23),
          Map.entry("BY", 28),
          Map.entry("SV", 28),
          Map.entry("LY", 25),
          Map.entry("SD", 18),
          Map.entry("BI", 27),
          Map.entry("DJ", 27),
          Map.entry("RU", 33),
          Map.entry("SO", 23),
          Map.entry("NI", 28),
          Map.entry("MN", 20),
          Map.entry("FK", 18),
          Map.entry("OM", 23),
          Map.entry("YE", 30),
          Map.entry("HN", 28));

  @Override
  public boolean isValid(String iban, ConstraintValidatorContext ctx) {
    return IbanValidator.isValid(iban);
  }

  public static boolean isValid(String iban) {
    if (iban == null) return false;

    String s = iban.trim().toUpperCase().replaceAll("\\s+", "");
    int len = s.length();
    if (len < IBAN_MIN_LENGTH || len > IBAN_MAX_LENGTH) return false;
    if (!IBAN_PATTERN.matcher(s).matches()) return false;

    String cc = s.substring(0, 2);
    Integer expected = COUNTRY_LENGTHS.get(cc);
    if (expected == null || len != expected) return false;

    // ISO-7064 MOD-97-10 (streaming, O(1) memory)
    String rearr = s.substring(4) + s.substring(0, 4);
    int mod = 0;
    for (char c : rearr.toCharArray()) {
      int n = Character.getNumericValue(c);
      if (n < 0) return false;
      if (n < 10) {
        mod = (mod * 10 + n) % 97;
      } else {
        mod = (mod * 10 + n / 10) % 97;
        mod = (mod * 10 + n % 10) % 97;
      }
    }
    boolean ok = mod == 1;
    if (!ok && log.isDebugEnabled()) log.debug("IBAN failed checksum: {}", iban);
    return ok;
  }
}
