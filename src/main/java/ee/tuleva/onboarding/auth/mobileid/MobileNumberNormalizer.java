package ee.tuleva.onboarding.auth.mobileid;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class MobileNumberNormalizer {

  String normalizePhoneNumber(String phoneNumber) {
    phoneNumber = StringUtils.trim(phoneNumber);
    if (startsWith(phoneNumber, "+")) {
      phoneNumber = phoneNumber.substring(1);
    }
    if (startsWith(phoneNumber, "372")) {
      phoneNumber = phoneNumber.substring(3);
    }
    if (isBlank(phoneNumber)) {
      return null;
    }
    return "+372" + phoneNumber;
  }
}
