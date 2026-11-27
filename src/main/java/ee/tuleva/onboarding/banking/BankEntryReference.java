package ee.tuleva.onboarding.banking;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.UUID;

public final class BankEntryReference {

  private BankEntryReference() {}

  public static UUID of(String iban, String externalId) {
    return UUID.nameUUIDFromBytes((iban + ":" + externalId).getBytes(UTF_8));
  }
}
