package ee.tuleva.onboarding.mandate.signature;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

import static java.lang.Integer.parseInt;

@Getter
@RequiredArgsConstructor
public class IdCardSignatureSession implements Serializable {

  private static final long serialVersionUID = 8149193185518071327L;

  private final int sessCode;
  private final String signatureId;
  private final String hash;


  @Override
  public String toString() {
    return sessCode + ":::" + signatureId + ":::" + hash;
  }

  public static IdCardSignatureSession fromString(String serializedIdCardSession) {
    String[] tokens = serializedIdCardSession.split(":::");
    return new IdCardSignatureSession(parseInt(tokens[0]), tokens[1], tokens[2]);
  }
}
