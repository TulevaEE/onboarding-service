package ee.tuleva.onboarding.signature.smartid;

import ee.sk.smartid.SignableHash;
import ee.tuleva.onboarding.signature.SignatureFile;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;

@Data
public class SmartIdSignatureSession implements Serializable {
  @Serial private static final long serialVersionUID = -5454823973379414071L;

  private final String certificateSessionId;
  private final String personalCode;
  private final List<SignatureFile> files;
  private String signingSessionId;
  private String verificationCode;
  private String documentNumber;
  private DataToSign dataToSign;
  private SignableHash signableHash;
  private Container container;
}
