package ee.tuleva.onboarding.signature;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.jspecify.annotations.Nullable;

@Data
public class SmartIdSignatureSession implements Serializable {
  @Serial private static final long serialVersionUID = -5454823973379414072L;

  private final String personalCode;
  private final List<SignatureFile> files;
  private @Nullable String certificateSessionId;
  private @Nullable String documentNumber;
  private @Nullable String signingSessionId;
  private @Nullable String verificationCode;
  private @Nullable DataToSign dataToSign;
  private @Nullable Container container;
}
