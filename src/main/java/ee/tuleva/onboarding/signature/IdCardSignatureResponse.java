package ee.tuleva.onboarding.signature;

public record IdCardSignatureResponse(String hash, String hashFunction) {

  public static IdCardSignatureResponse from(IdCardSignatureSession session) {
    return new IdCardSignatureResponse(session.getHashToSign(), session.getHashFunction());
  }
}
