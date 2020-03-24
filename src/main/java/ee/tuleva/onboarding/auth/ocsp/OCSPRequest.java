package ee.tuleva.onboarding.auth.ocsp;

import java.security.cert.X509Certificate;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bouncycastle.cert.ocsp.OCSPReq;

@AllArgsConstructor
@Data
public class OCSPRequest {
  private final String ocspServer;
  private final X509Certificate certificate;
  private final OCSPReq ocspRequest;
}
