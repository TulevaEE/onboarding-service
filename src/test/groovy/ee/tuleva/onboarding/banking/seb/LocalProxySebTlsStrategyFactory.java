package ee.tuleva.onboarding.banking.seb;

import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

/**
 * Test component that creates a TLS strategy bypassing hostname verification. This is required when
 * connecting to the SEB test gateway through an AWS SSM port forwarding session, where the
 * connection goes through localhost:8443 but the certificate is issued to the actual gateway host.
 */
@TestComponent
@Primary
public class LocalProxySebTlsStrategyFactory implements SebTlsStrategyFactory {

  @Override
  public TlsSocketStrategy create(SSLContext sslContext) {
    return new DefaultClientTlsStrategy(sslContext, NoopHostnameVerifier.INSTANCE);
  }
}
