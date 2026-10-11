package ee.tuleva.onboarding.auth.mobileid;

import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MobileNumberNormalizer {

  private static final Pattern TYPING_SEPARATORS = Pattern.compile("[\\s\\u00A0\\-.()]");
  private static final Pattern ESTONIAN_LOCAL_NUMBER = Pattern.compile("\\d{7,8}");
  private static final Pattern ESTONIAN_MOBILE_NUMBER = Pattern.compile("\\+372\\d{7,8}");

  public String normalize(String typed) {
    String compact = TYPING_SEPARATORS.matcher(typed).replaceAll("");
    String canonical = "+" + withCountryCode(compact);
    if (!ESTONIAN_MOBILE_NUMBER.matcher(canonical).matches()) {
      throw invalid();
    }
    return canonical;
  }

  private static String withCountryCode(String compact) {
    if (compact.startsWith("+")) {
      return compact.substring(1);
    }
    if (ESTONIAN_LOCAL_NUMBER.matcher(compact).matches()) {
      return "372" + compact;
    }
    if (compact.startsWith("00")) {
      return compact.substring(2);
    }
    return compact;
  }

  private static MobileIdException invalid() {
    return new MobileIdException(
        ofSingleError(
            "mobile.id.phone.number.invalid",
            "The phone number is not an Estonian mobile number."));
  }
}
