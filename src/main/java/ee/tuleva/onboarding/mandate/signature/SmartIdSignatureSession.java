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
    private String challengeCode;
    private final String certificateSessionId;
    private String signingSessionId;
    private final String personalCode;
    private final List<SignatureFile> files;
    private String documentNumber;
    private DataToSign dataToSign;
    private SignableHash signableHash;
    private Container container;
}
