package ee.tuleva.onboarding.signature.idcard;

import java.io.Serializable;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;

@Getter
@RequiredArgsConstructor
@ToString
@Builder
public class IdCardSignatureSession implements Serializable {

  private static final long serialVersionUID = 8149193185518071327L;

  private final String hashToSignInHex;
  private final DataToSign dataToSign;
  private final Container container;
}
