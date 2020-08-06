package ee.tuleva.onboarding.mandate.signature;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;

import java.io.Serializable;

@Getter
@RequiredArgsConstructor
@ToString
public class MobileIdSignatureSession implements Serializable {

    private static final long serialVersionUID = -7443368341567864757L;

    private final String sessionId;
    private final String verificationCode;
    private final DataToSign dataToSign;
    private final Container container;

}
