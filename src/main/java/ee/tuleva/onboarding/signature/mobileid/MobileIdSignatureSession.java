package ee.tuleva.onboarding.signature.mobileid;

import java.io.Serial;
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
public class MobileIdSignatureSession implements Serializable {

  @Serial private static final long serialVersionUID = -7443368341567864757L;

  private final String sessionId;
  private final String verificationCode;
  private final DataToSign dataToSign;
  private final Container container;
}
