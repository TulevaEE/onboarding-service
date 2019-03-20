package ee.tuleva.onboarding.mandate.signature;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import lombok.Data;

import java.io.Serializable;

@Data
public class SmartIdSignatureSession implements Serializable {
    private final String challengeCode;
    private final IdCardSignatureSession session;
    private byte[] signedFile;
}
