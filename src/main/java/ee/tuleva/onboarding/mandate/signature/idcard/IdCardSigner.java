package ee.tuleva.onboarding.mandate.signature.idcard;

import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import ee.tuleva.onboarding.mandate.signature.DigiDocFacade;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Base64;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IdCardSigner {

    private final OCSPUtils ocspUtils;
    private final DigiDocFacade digiDocFacade;

    public IdCardSignatureSession startSign(List<SignatureFile> files, String signingCertificate) {
        X509Certificate certificate = ocspUtils.getX509Certificate(signingCertificate);
        Container container = digiDocFacade.buildContainer(files);

        DataToSign dataToSign = digiDocFacade.dataToSign(container, certificate);
        byte[] digestToSign = digiDocFacade.digestToSign(dataToSign);
        String hash = Base64.encodeBase64String(digestToSign);

        return new IdCardSignatureSession(hash, dataToSign, container);
    }

    public byte[] getSignedFile(IdCardSignatureSession session, String signedHash) {
        byte[] signature = Base64.decodeBase64(signedHash);
        return digiDocFacade.addSignatureToContainer(signature, session.getDataToSign(), session.getContainer());
    }
}
