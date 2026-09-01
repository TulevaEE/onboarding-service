package ee.tuleva.onboarding.auth.mobileid;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;

import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class MobileNumberNormalizer {

  @Nullable String normalizePhoneNumber(@Nullable String phoneNumber) {
    phoneNumber = trim(phoneNumber);
    if (Strings.CS.startsWith(phoneNumber, "+")) {
      phoneNumber = phoneNumber.substring(1);
    }
    if (Strings.CS.startsWith(phoneNumber, "372")) {
      phoneNumber = phoneNumber.substring(3);
    }
    if (isBlank(phoneNumber)) {
      return null;
    }
    return "+372" + phoneNumber;
  }
}
