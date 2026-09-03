package ee.tuleva.onboarding.signature;

import ee.sk.smartid.SignableHash;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.jspecify.annotations.Nullable;

@Data
public class SmartIdSignatureSession implements Serializable {
  @Serial private static final long serialVersionUID = -5454823973379414071L;

  private final String certificateSessionId;
  private final String personalCode;
  private final List<SignatureFile> files;
  private @Nullable String signingSessionId;
  private @Nullable String verificationCode;
  private @Nullable String documentNumber;
  private @Nullable DataToSign dataToSign;
  private @Nullable SignableHash signableHash;
  private @Nullable Container container;
}
