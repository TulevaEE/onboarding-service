package ee.tuleva.onboarding.user.personalcode;

import static org.apache.commons.lang3.StringUtils.isBlank;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PersonalCodeValidator implements ConstraintValidator<ValidPersonalCode, String> {

  public boolean isValid(String personalCode) {
    return isValid(personalCode, null);
  }

  @Override
  public boolean isValid(String personalCode, ConstraintValidatorContext context) {

    if (isBlank(personalCode)) {
      return false;
    }

    if (personalCode.length() != 11) {
      return false;
    }

    try {
      int century = Integer.parseInt(personalCode.substring(0, 1));
      int order = Integer.parseInt(personalCode.substring(7, 10));
      int checksum = Integer.parseInt(personalCode.substring(10, 11));

      // only allow 20th-21st century
      if (!(century >= 3 && century <= 6)) {
        return false;
      }

      PersonalCode.getDateOfBirth(personalCode); // throws exception on invalid date

      if (!(order >= 0 && order <= 999)) {
        return false;
      }

      int realChecksum = calculateChecksum(personalCode);
      return checksum == realChecksum;

    } catch (NumberFormatException | DateTimeParseException e) {
      log.info("Invalid personal code {}", personalCode);
      return false;
    }
  }

  // https://et.wikipedia.org/wiki/Isikukood#Kontrollnumber
  private int calculateChecksum(String personalCode) {
    int[] c = personalCode.chars().map(Character::getNumericValue).toArray();

    int sum =
        1 * c[0] + 2 * c[1] + 3 * c[2] + 4 * c[3] + 5 * c[4] + 6 * c[5] + 7 * c[6] + 8 * c[7]
            + 9 * c[8] + 1 * c[9];
    int mod = sum % 11;

    if (mod == 10) {
      sum =
          3 * c[0] + 4 * c[1] + 5 * c[2] + 6 * c[3] + 7 * c[4] + 8 * c[5] + 9 * c[6] + 1 * c[7]
              + 2 * c[8] + 3 * c[9];
      mod = sum % 11;
    }

    return mod == 10 ? 0 : mod;
  }

  @Override
  public void initialize(ValidPersonalCode constraintAnnotation) {}
}
