package ee.tuleva.onboarding.mandate.signature;

import com.codeborne.security.mobileid.SignatureFile;
import ee.sk.smartid.SignableHash;
import lombok.Data;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;

import java.io.Serializable;
import java.util.List;

@Data
public class SmartIdSignatureSession implements Serializable {
    private static final long serialVersionUID = -5454823973379414071L;

    private final String certificateSessionId;
    private final String personalCode;
    private final List<SignatureFile> files;
    private String signingSessionId;
    private String challengeCode;
    private String documentNumber;
    private DataToSign dataToSign;
    private SignableHash signableHash;
    private Container container;
}
