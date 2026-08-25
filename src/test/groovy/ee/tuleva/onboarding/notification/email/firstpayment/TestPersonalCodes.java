package ee.tuleva.onboarding.notification.email.firstpayment;

final class TestPersonalCodes {

  private TestPersonalCodes() {}

  static String withValidChecksum(String firstTenDigits) {
    int[] digits = firstTenDigits.chars().map(Character::getNumericValue).toArray();
    int checksum = weightedMod11(digits, new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 1});
    if (checksum == 10) {
      checksum = weightedMod11(digits, new int[] {3, 4, 5, 6, 7, 8, 9, 1, 2, 3});
      if (checksum == 10) {
        checksum = 0;
      }
    }
    return firstTenDigits + checksum;
  }

  private static int weightedMod11(int[] digits, int[] weights) {
    int sum = 0;
    for (int i = 0; i < digits.length; i++) {
      sum += digits[i] * weights[i];
    }
    return sum % 11;
  }
}
