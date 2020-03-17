package ee.tuleva.onboarding.auth.ocsp;

import com.codeborne.security.AuthenticationException;
import com.codeborne.security.mobileid.CheckCertificateResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.stereotype.Service;

import java.net.*;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static com.codeborne.security.AuthenticationException.Code.valueOf;

@Service
@Slf4j
@AllArgsConstructor
public class OnlineCertificateStatusProtocolService {
    private final OCSPUtils ocspUtils;

    public CheckCertificateResponse checkCertificate(String certificate) {

        X509Certificate cert = ocspUtils.getX509Certificate(certificate);

        String caCertificate = ocspUtils.getIssuerCertificate(cert);
        URI responderUri = ocspUtils.getResponderURI(cert);

        OCSPRequest request = new OCSPRequest(caCertificate, responderUri.toString());

        String result = request.checkSerialNumber(cert.getSerialNumber());

        validateGoodResult(result);

        String subject = getSubject(cert);
        String[] parts = subject.split("\\\\,");
        if (parts.length != 3) {
            log.warn("Subject line did not have 3 separated parts, so parsing failed");
            throw new AuthenticationException(AuthenticationException.Code.USER_CERTIFICATE_MISSING);
        }

        return new CheckCertificateResponse(parts[1], parts[0], parts[2]);
    }

    private String getSubject(X509Certificate cert){
        X500Name x500name = null;
        try {
            x500name = new JcaX509CertificateHolder(cert).getSubject();
        } catch (CertificateEncodingException e) {
            throw new AuthenticationException(AuthenticationException.Code.INVALID_INPUT);
        }
        RDN cn = x500name.getRDNs(BCStyle.CN)[0];

        return IETFUtils.valueToString(cn.getFirst().getValue());
    }

    private void validateGoodResult(String result) {
        if (!"GOOD".equals(result))
            throw new AuthenticationException(valueOf(result));
    }
}
