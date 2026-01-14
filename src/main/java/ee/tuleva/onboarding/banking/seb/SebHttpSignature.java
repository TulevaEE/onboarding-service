package ee.tuleva.onboarding.banking.seb;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class SebHttpSignature {

  private final PrivateKey privateKey;
  private final String keyId;

  @SneakyThrows
  public String createDigest(byte[] body) {
    // SEB docs: 1) Base64 encode the body, 2) SHA-256 hash the Base64 string
    String bodyBase64 = Base64.getEncoder().encodeToString(body);
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(bodyBase64.getBytes(UTF_8));
    return "SHA-256=" + Base64.getEncoder().encodeToString(hash);
  }

  @SneakyThrows
  public String createSignature(String digestHeader) {
    // The signing string is the digest header line
    String signingString = "digest: " + digestHeader;

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(signingString.getBytes(UTF_8));
    byte[] signatureBytes = signature.sign();

    String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);

    return String.format(
        "keyId=\"%s\",algorithm=\"rsa-sha256\",headers=\"digest\",signature=\"%s\"",
        keyId, signatureBase64);
  }

  public static String buildKeyId(X509Certificate certificate) {
    String serialHex = certificate.getSerialNumber().toString(16);
    // Pad with leading zero if odd length (SEB requires even-length hex)
    if (serialHex.length() % 2 != 0) {
      serialHex = "0" + serialHex;
    }
    // SEB requires DN format: CN=xxx,O=yyy (CN first, then O)
    String issuerDN = formatIssuerDn(certificate);
    return "SN=" + serialHex + ",CA=" + issuerDN;
  }

  private static String formatIssuerDn(X509Certificate certificate) {
    // Extract CN and O from issuer DN and format as "CN=xxx,O=yyy"
    String dn = certificate.getIssuerX500Principal().getName();
    String cn = extractDnComponent(dn, "CN=");
    String o = extractDnComponent(dn, "O=");

    if (cn != null && o != null) {
      return "CN=" + cn + ",O=" + o;
    }
    // Fallback to raw DN if components not found
    return dn;
  }

  private static String extractDnComponent(String dn, String component) {
    int start = dn.indexOf(component);
    if (start < 0) return null;
    start += component.length();
    int end = dn.indexOf(",", start);
    return end > 0 ? dn.substring(start, end) : dn.substring(start);
  }
}
