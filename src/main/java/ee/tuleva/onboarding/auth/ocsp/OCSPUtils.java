package ee.tuleva.onboarding.auth.ocsp;

import com.codeborne.security.AuthenticationException;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import sun.security.x509.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

@Component
public class OCSPUtils {
    public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERT = "-----END CERTIFICATE-----";
    public final static String LINE_SEPARATOR = System.getProperty("line.separator");


    public String getIssuerCertificate(X509Certificate certificate) {
        AuthorityInfoAccessExtension extension = ((X509CertImpl) certificate).getAuthorityInfoAccessExtension();
        List<AccessDescription> accessDescriptions = extension.getAccessDescriptions();
        AccessDescription result = accessDescriptions.stream().filter(accessDescription -> accessDescription.getAccessMethod().equals(AccessDescription.Ad_CAISSUERS_Id)).findFirst().orElse(null);

        if (result == null) {
            throw new AuthenticationException(AuthenticationException.Code.INVALID_INPUT, "Issuer missing in certificate", new CertificateParsingException());
        }

        GeneralName generalName = result.getAccessLocation();
        URIName uri = (URIName) generalName.getName();
        URL url = null;
        try {
            url = uri.getURI().toURL();
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(conn.getInputStream(), baos);

            byte[] derCert = baos.toByteArray();
            final Base64.Encoder encoder = Base64.getMimeEncoder(64, LINE_SEPARATOR.getBytes());
            final String encodedCertText = new String(encoder.encode(derCert));
            final String pemCert = BEGIN_CERT + LINE_SEPARATOR + encodedCertText + LINE_SEPARATOR + END_CERT;

            return pemCert;
        } catch ( IOException e) {
            throw new AuthenticationException(AuthenticationException.Code.INTERNAL_ERROR, "Unable to aquire resource", e);
        }

    }

    public URI getResponderURI(X509Certificate certificate) {
        AuthorityInfoAccessExtension extension = ((X509CertImpl) certificate).getAuthorityInfoAccessExtension();
        List<AccessDescription> accessDescriptions = extension.getAccessDescriptions();
        AccessDescription result = accessDescriptions.stream().filter(accessDescription -> accessDescription.getAccessMethod().equals(AccessDescription.Ad_OCSP_Id)).findFirst().orElse(null);
        if (result == null) {
            throw new AuthenticationException(AuthenticationException.Code.INVALID_INPUT, "Responder OCSP URI missing in certificate", new CertificateParsingException());
        }
        GeneralName generalName = result.getAccessLocation();
        URIName uri = (URIName) generalName.getName();
        return uri.getURI();
    }

    public X509Certificate getX509Certificate(String certificate) {
        CertificateFactory cf = null;
        try {
            cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificate.getBytes("UTF-8")));
        } catch (CertificateException | UnsupportedEncodingException e) {
            throw new AuthenticationException(AuthenticationException.Code.INVALID_INPUT, "Unable to read certificate", e);
        }
    }
}
