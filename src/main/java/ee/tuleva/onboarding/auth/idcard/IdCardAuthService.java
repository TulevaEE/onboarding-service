package ee.tuleva.onboarding.auth.idcard;

import com.codeborne.security.mobileid.CheckCertificateResponse;
import ee.tuleva.onboarding.auth.ocsp.OnlineCertificateStatusProtocolService;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.asn1.DLSequence;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils.parseExtensionValue;

@Service
@Slf4j
@AllArgsConstructor
public class IdCardAuthService {

    private static final String OID = "2.5.29.32";
    private static final String AUTHENTICATION_POLICY_ID = "0.4.0.2042.1.2";
    private static final int POLICY_NO_1 = 0;
    private static final int POLICY_NO_2 = 1;
    private final OnlineCertificateStatusProtocolService authenticator;
    private final GenericSessionStore sessionStore;

    public IdCardSession checkCertificate(String certificate) {
        log.info("Checking ID card certificate");
        CheckCertificateResponse response = authenticator.checkCertificate(certificate);
        IdCardSession session = IdCardSession.builder()
            .firstName(response.firstName)
            .lastName(response.lastName)
            .personalCode(response.personalCode)
            .documentType(getDocumentTypeFromCertificate(certificate))
            .build();
        sessionStore.save(session);
        return session;
    }

    public IdDocumentType getDocumentTypeFromCertificate(String certificate) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            val certStream = toInputStream(certificate, "UTF8");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(certStream);
            return getDocumentTypeFromCertificate(cert);
        } catch (CertificateException | IOException e) {
            return IdDocumentType.UNKNOWN;
        }
    }

    public IdDocumentType getDocumentTypeFromCertificate(X509Certificate cert) {
        try {
            byte[] encodedExtensionValue = cert.getExtensionValue(OID);
            if (encodedExtensionValue != null) {
                DLSequence extensionValue = (DLSequence) parseExtensionValue(encodedExtensionValue);
                try {
                    DLSequence first = (DLSequence) extensionValue.getObjectAt(POLICY_NO_1);
                    DLSequence second = (DLSequence) extensionValue.getObjectAt(POLICY_NO_2);
                    if (Objects.equals(second.getObjectAt(0).toString(), AUTHENTICATION_POLICY_ID)) {
                        return IdDocumentType.findByIdentifier(first.getObjectAt(POLICY_NO_1).toString());
                    } else {
                        log.warn("Unknown identifier {}", second.getObjectAt(0));
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    log.warn("Extension of card certificate not known: {}", extensionValue.toString());
                }
            } else {
                log.warn("Extension missing!");
            }
        } catch (IOException e) {
            log.warn("Could not parse certificate");
        }
        return IdDocumentType.UNKNOWN;
    }

}
