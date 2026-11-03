package ee.tuleva.onboarding.signature;

import static org.bouncycastle.asn1.x509.Extension.extendedKeyUsage;
import static org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_timeStamping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

class LocalTimestampAuthority implements AutoCloseable {

  private static final String PATH = "/tsa";
  private static final ASN1ObjectIdentifier TEST_POLICY = new ASN1ObjectIdentifier("1.2.3.4.1");

  private final KeyPair keyPair = rsaKeyPair();
  private final X509Certificate certificate = selfSignedTimestampingCertificate(keyPair);
  private final AtomicLong serialNumbers = new AtomicLong();
  private final HttpServer server;

  @SneakyThrows
  LocalTimestampAuthority() {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(PATH, this::timestamp);
    server.start();
  }

  String url() {
    return "http://localhost:" + server.getAddress().getPort() + PATH;
  }

  @Override
  public void close() {
    server.stop(0);
  }

  @SneakyThrows
  private void timestamp(HttpExchange exchange) {
    try (exchange) {
      var request = new TimeStampRequest(exchange.getRequestBody().readAllBytes());
      byte[] response =
          new TimeStampResponseGenerator(tokenGenerator(), TSPAlgorithms.ALLOWED)
              .generate(request, BigInteger.valueOf(serialNumbers.incrementAndGet()), new Date())
              .getEncoded();
      exchange.getResponseHeaders().add("Content-Type", "application/timestamp-reply");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
    }
  }

  @SneakyThrows
  private TimeStampTokenGenerator tokenGenerator() {
    var generator =
        new TimeStampTokenGenerator(
            new JcaSimpleSignerInfoGeneratorBuilder()
                .build("SHA256withRSA", keyPair.getPrivate(), certificate),
            new JcaDigestCalculatorProviderBuilder()
                .build()
                .get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)),
            TEST_POLICY);
    generator.addCertificates(new JcaCertStore(List.of(certificate)));
    return generator;
  }

  @SneakyThrows
  private static KeyPair rsaKeyPair() {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  @SneakyThrows
  private static X509Certificate selfSignedTimestampingCertificate(KeyPair keyPair) {
    var name = new X500Name("C=EE, O=Test, CN=Local test timestamp authority");
    var now = Instant.now();
    var holder =
        new JcaX509v3CertificateBuilder(
                name,
                BigInteger.ONE,
                Date.from(now.minus(Duration.ofDays(1))),
                Date.from(now.plus(Duration.ofDays(1))),
                name,
                keyPair.getPublic())
            .addExtension(extendedKeyUsage, true, new ExtendedKeyUsage(id_kp_timeStamping))
            .build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate()));
    return new JcaX509CertificateConverter().getCertificate(holder);
  }
}
