package ee.tuleva.onboarding.auth.smartid;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import org.bouncycastle.asn1.ocsp.ResponderID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;

final class OcspResponderIds {

  private OcspResponderIds() {}

  static X509Certificate includedCertificateOf(ResponderID responderId, BasicOCSPResp response)
      throws GeneralSecurityException {
    for (X509CertificateHolder certificate : response.getCerts()) {
      if (identifies(responderId, certificate)) {
        return new JcaX509CertificateConverter().getCertificate(certificate);
      }
    }
    throw new SmartIdCertificateStatusUnavailableException(
        "responder certificate not included in the response");
  }

  static boolean identifies(ResponderID responderId, X509CertificateHolder certificate)
      throws NoSuchAlgorithmException {
    X500Name name = responderId.getName();
    if (name != null) {
      return name.equals(certificate.getSubject());
    }
    return Arrays.equals(responderId.getKeyHash(), keyHash(certificate));
  }

  private static byte[] keyHash(X509CertificateHolder certificate) throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-1")
        .digest(certificate.getSubjectPublicKeyInfo().getPublicKeyData().getBytes());
  }
}
