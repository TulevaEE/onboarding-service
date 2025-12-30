package ee.tuleva.onboarding.auth.idcard;

import static javax.security.auth.x500.X500Principal.RFC1779;
import static org.bouncycastle.asn1.x509.Extension.certificatePolicies;
import static org.bouncycastle.asn1.x509.Extension.extendedKeyUsage;
import static org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils.parseExtensionValue;

import ee.tuleva.onboarding.auth.idcard.exception.UnknownDocumentTypeException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownExtendedKeyUsageException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownIssuerException;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.DLSequence;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IdDocumentTypeExtractor {

  private static final String AUTHENTICATION_POLICY_ID = "0.4.0.2042.1.2";
  private static final String CLIENT_AUTHENTICATION_ID = "1.3.6.1.5.5.7.3.2";
  private static final List<String> VALID_ISSUERS =
      List.of(
          "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE",
          "CN=ESTEID2018, OID.2.5.4.97=NTREE-10747013, O=SK ID Solutions AS, C=EE",
          "CN=ESTEID2025, OID.2.5.4.97=NTREE-17066049, O=Zetes Estonia OÜ, C=EE");

  public IdDocumentType extract(X509Certificate certificate) {
    try {
      byte[] encodedExtensionValue = certificate.getExtensionValue(certificatePolicies.getId());
      if (encodedExtensionValue != null) {
        var extensionValue = (DLSequence) parseExtensionValue(encodedExtensionValue);
        String documentTypeOid = null;
        boolean hasAuthPolicy = false;

        for (int i = 0; i < extensionValue.size(); i++) {
          var policy = (DLSequence) extensionValue.getObjectAt(i);
          String oid = policy.getObjectAt(0).toString();
          if (Objects.equals(oid, AUTHENTICATION_POLICY_ID)) {
            hasAuthPolicy = true;
          } else {
            documentTypeOid = oid;
          }
        }

        if (hasAuthPolicy && documentTypeOid != null) {
          return IdDocumentType.findByIdentifier(documentTypeOid);
        } else if (!hasAuthPolicy) {
          throw new UnknownDocumentTypeException("Missing authentication policy");
        }
      } else {
        log.error("Certificate policies extension missing");
      }
    } catch (IOException e) {
      log.error("Failed to parse certificate policies extension", e);
    }
    throw new UnknownDocumentTypeException();
  }

  public void checkClientAuthentication(X509Certificate certificate) {
    try {
      byte[] encodedExtendedKeyUsage = certificate.getExtensionValue(extendedKeyUsage.getId());
      if (encodedExtendedKeyUsage != null) {
        var extendedKeyUsageSequence = (DLSequence) parseExtensionValue(encodedExtendedKeyUsage);
        for (var element : extendedKeyUsageSequence) {
          if (element.toString().equals(CLIENT_AUTHENTICATION_ID)) {
            return;
          }
        }
        throw new UnknownExtendedKeyUsageException(extendedKeyUsageSequence.toString());
      } else {
        log.error("Extended key usage extension missing");
      }
    } catch (IOException e) {
      log.error("Failed to parse extended key usage extension", e);
    }
    throw new UnknownExtendedKeyUsageException();
  }

  public void checkIssuer(X509Certificate certificate) {
    var issuer = certificate.getIssuerX500Principal().getName(RFC1779);
    if (VALID_ISSUERS.contains(issuer)) {
      return;
    }
    throw new UnknownIssuerException(issuer);
  }
}
