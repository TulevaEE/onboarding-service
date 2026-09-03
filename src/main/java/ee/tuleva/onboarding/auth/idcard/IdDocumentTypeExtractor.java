package ee.tuleva.onboarding.auth.idcard;

import static javax.security.auth.x500.X500Principal.RFC1779;
import static org.bouncycastle.asn1.x509.Extension.certificatePolicies;
import static org.bouncycastle.asn1.x509.Extension.extendedKeyUsage;
import static org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils.parseExtensionValue;

import ee.tuleva.onboarding.auth.idcard.exception.UnknownCountryException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownDocumentTypeException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownExtendedKeyUsageException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownIssuerException;
import ee.tuleva.onboarding.auth.idcard.normalizer.CertificateNormalizer;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IdDocumentTypeExtractor {

  private static final String AUTHENTICATION_POLICY_ID = "0.4.0.2042.1.2";
  private static final String ESTONIA = "EE";
  private static final String CLIENT_AUTHENTICATION_ID = "1.3.6.1.5.5.7.3.2";

  private static final List<String> DEFAULT_VALID_ISSUERS =
      List.of(
          "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE",
          "CN=ESTEID2018, OID.2.5.4.97=NTREE-10747013, O=SK ID Solutions AS, C=EE",
          "C=EE, O=Zetes Estonia OÜ, OID.2.5.4.97=NTREE-17066049, CN=ESTEID2025");

  private final List<String> validIssuers;
  private final CertificateNormalizer normalizer;

  public IdDocumentTypeExtractor(
      @Value("${id-card.additional-issuers:#{T(java.util.List).of()}}")
          List<String> additionalIssuers,
      CertificateNormalizer normalizer) {
    var merged = new ArrayList<>(DEFAULT_VALID_ISSUERS);
    merged.addAll(additionalIssuers);
    this.validIssuers = List.copyOf(merged);
    this.normalizer = normalizer;
  }

  public IdDocumentType extract(X509Certificate certificate) {
    try {
      byte[] encodedExtensionValue = certificate.getExtensionValue(certificatePolicies.getId());
      if (encodedExtensionValue == null) {
        log.error("Certificate policies extension missing");
        throw new UnknownDocumentTypeException();
      }
      var extensionValue = (DLSequence) parseExtensionValue(encodedExtensionValue);
      return resolveDocumentType(classifyPolicies(extensionValue));
    } catch (IOException e) {
      log.error("Failed to parse certificate policies extension", e);
      throw new UnknownDocumentTypeException();
    }
  }

  private static PolicyClassification classifyPolicies(DLSequence extensionValue) {
    String documentTypeOid = null;
    boolean hasAuthPolicy = false;

    for (int i = 0; i < extensionValue.size(); i++) {
      var policy = (DLSequence) extensionValue.getObjectAt(i);
      String oid = policy.getObjectAt(0).toString();
      if (Objects.equals(oid, AUTHENTICATION_POLICY_ID)) {
        hasAuthPolicy = true;
      } else if (documentTypeOid == null) {
        documentTypeOid = oid;
      } else {
        throw new UnknownDocumentTypeException("Unexpected additional policy OID: " + oid);
      }
    }

    return new PolicyClassification(documentTypeOid, hasAuthPolicy);
  }

  private IdDocumentType resolveDocumentType(PolicyClassification classification) {
    if (!classification.hasAuthPolicy()) {
      throw new UnknownDocumentTypeException("Missing authentication policy");
    }
    String documentTypeOid = classification.documentTypeOid();
    if (documentTypeOid == null) {
      throw new UnknownDocumentTypeException("Missing document type policy");
    }
    return IdDocumentType.findByIdentifier(normalizer.normalizeOid(documentTypeOid));
  }

  private record PolicyClassification(@Nullable String documentTypeOid, boolean hasAuthPolicy) {}

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

  public void checkCountry(X509Certificate certificate) {
    X500Name subject = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
    RDN[] countries = subject.getRDNs(BCStyle.C);
    String country =
        countries.length == 0 ? "" : IETFUtils.valueToString(countries[0].getFirst().getValue());
    if (!ESTONIA.equals(country)) {
      throw new UnknownCountryException(country);
    }
  }

  public void checkIssuer(X509Certificate certificate) {
    var issuer = certificate.getIssuerX500Principal().getName(RFC1779);
    var normalizedIssuer = normalizer.normalizeIssuer(issuer);
    if (validIssuers.contains(normalizedIssuer)) {
      return;
    }
    throw new UnknownIssuerException(issuer);
  }
}
