package ee.tuleva.onboarding.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.client.http.DSSFileLoader;
import eu.europa.esig.dss.spi.exception.DSSExternalResourceException;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import eu.europa.esig.dss.xades.tsl.TrustedListV5SignatureParametersBuilder;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.digidoc4j.Configuration;

final class TrustListFixture {

  static final String LIST_OF_TRUSTED_LISTS_URL = "https://lotl.test/eu-lotl.xml";
  static final String ESTONIAN_TRUSTED_LIST_URL = "https://tl.test/EE.xml";
  private static final String TRUSTSTORE_PASSWORD = "test-truststore";

  private final KeyPair listOfTrustedListsSigner = rsaKeyPair();
  private final X509Certificate listOfTrustedListsSignerCertificate =
      selfSigned("CN=Test List of Trusted Lists Signer", listOfTrustedListsSigner);
  private final KeyPair trustedListSigner = rsaKeyPair();
  private final X509Certificate trustedListSignerCertificate =
      selfSigned("CN=Test Estonian Trusted List Signer", trustedListSigner);
  private final X509Certificate qualifiedCaCertificate =
      selfSigned("CN=Test Qualified CA", rsaKeyPair());
  private final Map<String, byte[]> publishedLists;
  private final Path truststore;
  private final AtomicBoolean endpointDown = new AtomicBoolean();
  private final AtomicInteger downloadAttempts = new AtomicInteger();

  private TrustListFixture(Instant nextUpdate, Path directory) {
    publishedLists =
        Map.of(
            LIST_OF_TRUSTED_LISTS_URL,
            sign(
                listOfTrustedLists(nextUpdate),
                listOfTrustedListsSigner,
                listOfTrustedListsSignerCertificate),
            ESTONIAN_TRUSTED_LIST_URL,
            sign(estonianTrustedList(nextUpdate), trustedListSigner, trustedListSignerCertificate));
    truststore = writeTruststore(directory.resolve("lotl-truststore.p12"));
  }

  static TrustListFixture publishedUntil(Instant nextUpdate, Path directory) {
    return new TrustListFixture(nextUpdate, directory);
  }

  Configuration servedTo(Configuration configuration) {
    configuration.setLotlLocation(LIST_OF_TRUSTED_LISTS_URL);
    configuration.setLotlTruststorePath(truststore.toString());
    configuration.setLotlTruststoreType("PKCS12");
    configuration.setLotlTruststorePassword(TRUSTSTORE_PASSWORD);
    configuration.setLotlPivotSupportEnabled(false);
    configuration.setTrustedTerritories("EE");
    configuration.setRequiredTerritories("EE");
    configuration.setTslFileLoaderFactory(() -> new PublishedListDownloads(this));
    return configuration;
  }

  void endpointGoesDown() {
    endpointDown.set(true);
  }

  void endpointComesBack() {
    endpointDown.set(false);
  }

  int downloadAttempts() {
    return downloadAttempts.get();
  }

  CertificateToken qualifiedCa() {
    return new CertificateToken(qualifiedCaCertificate);
  }

  private DSSDocument download(String url) {
    downloadAttempts.incrementAndGet();
    if (endpointDown.get()) {
      throw new DSSExternalResourceException("Trust list endpoint is down: url=" + url);
    }
    return new InMemoryDocument(publishedLists.get(url), url);
  }

  private record PublishedListDownloads(TrustListFixture fixture) implements DSSFileLoader {
    @Override
    public DSSDocument getDocument(String url) {
      return fixture.download(url);
    }
  }

  private String listOfTrustedLists(Instant nextUpdate) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <tsl:TrustServiceStatusList xmlns:tsl="http://uri.etsi.org/02231/v2#" \
        xmlns:ns3="http://uri.etsi.org/02231/v2/additionaltypes#" \
        TSLTag="http://uri.etsi.org/19612/TSLTag" Id="test-lotl">
          <tsl:SchemeInformation>
            <tsl:TSLVersionIdentifier>5</tsl:TSLVersionIdentifier>
            <tsl:TSLSequenceNumber>1</tsl:TSLSequenceNumber>
            <tsl:TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists</tsl:TSLType>
            %s
            <tsl:StatusDeterminationApproach>http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUlistofthelists</tsl:StatusDeterminationApproach>
            <tsl:SchemeTypeCommunityRules>
              <tsl:URI xml:lang="en">http://uri.etsi.org/TrstSvc/TrustedList/schemerules/EUlistofthelists</tsl:URI>
            </tsl:SchemeTypeCommunityRules>
            <tsl:SchemeTerritory>EU</tsl:SchemeTerritory>
            <tsl:PolicyOrLegalNotice><tsl:TSLLegalNotice xml:lang="en">Test list</tsl:TSLLegalNotice></tsl:PolicyOrLegalNotice>
            <tsl:HistoricalInformationPeriod>65535</tsl:HistoricalInformationPeriod>
            <tsl:PointersToOtherTSL>
              <tsl:OtherTSLPointer>
                <tsl:ServiceDigitalIdentities>
                  <tsl:ServiceDigitalIdentity>
                    <tsl:DigitalId><tsl:X509Certificate>%s</tsl:X509Certificate></tsl:DigitalId>
                  </tsl:ServiceDigitalIdentity>
                </tsl:ServiceDigitalIdentities>
                <tsl:TSLLocation>%s</tsl:TSLLocation>
                <tsl:AdditionalInformation>
                  <tsl:OtherInformation><tsl:TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</tsl:TSLType></tsl:OtherInformation>
                  <tsl:OtherInformation><tsl:SchemeTerritory>EE</tsl:SchemeTerritory></tsl:OtherInformation>
                  <tsl:OtherInformation><ns3:MimeType>application/vnd.etsi.tsl+xml</ns3:MimeType></tsl:OtherInformation>
                </tsl:AdditionalInformation>
              </tsl:OtherTSLPointer>
            </tsl:PointersToOtherTSL>
            <tsl:ListIssueDateTime>%s</tsl:ListIssueDateTime>
            <tsl:NextUpdate><tsl:dateTime>%s</tsl:dateTime></tsl:NextUpdate>
          </tsl:SchemeInformation>
        </tsl:TrustServiceStatusList>
        """
        .formatted(
            schemeOperator("Test list of trusted lists"),
            base64(trustedListSignerCertificate),
            ESTONIAN_TRUSTED_LIST_URL,
            issueDate(),
            nextUpdate.truncatedTo(ChronoUnit.SECONDS));
  }

  private String estonianTrustedList(Instant nextUpdate) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <tsl:TrustServiceStatusList xmlns:tsl="http://uri.etsi.org/02231/v2#" \
        TSLTag="http://uri.etsi.org/19612/TSLTag" Id="test-ee-tl">
          <tsl:SchemeInformation>
            <tsl:TSLVersionIdentifier>5</tsl:TSLVersionIdentifier>
            <tsl:TSLSequenceNumber>1</tsl:TSLSequenceNumber>
            <tsl:TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</tsl:TSLType>
            %s
            <tsl:StatusDeterminationApproach>http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate</tsl:StatusDeterminationApproach>
            <tsl:SchemeTypeCommunityRules>
              <tsl:URI xml:lang="en">http://uri.etsi.org/TrstSvc/TrustedList/schemerules/EUcommon</tsl:URI>
            </tsl:SchemeTypeCommunityRules>
            <tsl:SchemeTerritory>EE</tsl:SchemeTerritory>
            <tsl:PolicyOrLegalNotice><tsl:TSLLegalNotice xml:lang="en">Test list</tsl:TSLLegalNotice></tsl:PolicyOrLegalNotice>
            <tsl:HistoricalInformationPeriod>65535</tsl:HistoricalInformationPeriod>
            <tsl:ListIssueDateTime>%s</tsl:ListIssueDateTime>
            <tsl:NextUpdate><tsl:dateTime>%s</tsl:dateTime></tsl:NextUpdate>
          </tsl:SchemeInformation>
          <tsl:TrustServiceProviderList>
            <tsl:TrustServiceProvider>
              <tsl:TSPInformation>
                <tsl:TSPName><tsl:Name xml:lang="en">Test Trust Service Provider</tsl:Name></tsl:TSPName>
                %s
                <tsl:TSPInformationURI><tsl:URI xml:lang="en">https://tsp.test</tsl:URI></tsl:TSPInformationURI>
              </tsl:TSPInformation>
              <tsl:TSPServices>
                <tsl:TSPService>
                  <tsl:ServiceInformation>
                    <tsl:ServiceTypeIdentifier>http://uri.etsi.org/TrstSvc/Svctype/CA/QC</tsl:ServiceTypeIdentifier>
                    <tsl:ServiceName><tsl:Name xml:lang="en">Test Qualified CA</tsl:Name></tsl:ServiceName>
                    <tsl:ServiceDigitalIdentity>
                      <tsl:DigitalId><tsl:X509Certificate>%s</tsl:X509Certificate></tsl:DigitalId>
                    </tsl:ServiceDigitalIdentity>
                    <tsl:ServiceStatus>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted</tsl:ServiceStatus>
                    <tsl:StatusStartingTime>%s</tsl:StatusStartingTime>
                  </tsl:ServiceInformation>
                </tsl:TSPService>
              </tsl:TSPServices>
            </tsl:TrustServiceProvider>
          </tsl:TrustServiceProviderList>
        </tsl:TrustServiceStatusList>
        """
        .formatted(
            schemeOperator("Test Estonian trusted list"),
            issueDate(),
            nextUpdate.truncatedTo(ChronoUnit.SECONDS),
            address("TSPAddress"),
            base64(qualifiedCaCertificate),
            issueDate());
  }

  private static String schemeOperator(String schemeName) {
    return """
        <tsl:SchemeOperatorName><tsl:Name xml:lang="en">Test Scheme Operator</tsl:Name></tsl:SchemeOperatorName>
        %s
        <tsl:SchemeName><tsl:Name xml:lang="en">%s</tsl:Name></tsl:SchemeName>
        <tsl:SchemeInformationURI><tsl:URI xml:lang="en">https://scheme.test</tsl:URI></tsl:SchemeInformationURI>
        """
        .formatted(address("SchemeOperatorAddress"), schemeName);
  }

  private static String address(String element) {
    return """
        <tsl:%1$s>
          <tsl:PostalAddresses>
            <tsl:PostalAddress xml:lang="en">
              <tsl:StreetAddress>Test street 1</tsl:StreetAddress>
              <tsl:Locality>Test</tsl:Locality>
              <tsl:CountryName>EE</tsl:CountryName>
            </tsl:PostalAddress>
          </tsl:PostalAddresses>
          <tsl:ElectronicAddress><tsl:URI xml:lang="en">https://operator.test</tsl:URI></tsl:ElectronicAddress>
        </tsl:%1$s>
        """
        .formatted(element);
  }

  private static Instant issueDate() {
    return Instant.parse("2026-01-01T00:00:00Z");
  }

  @SneakyThrows
  private static String base64(X509Certificate certificate) {
    return Base64.getEncoder().encodeToString(certificate.getEncoded());
  }

  @SneakyThrows
  private static byte[] sign(String xml, KeyPair signer, X509Certificate signerCertificate) {
    DSSDocument unsigned = new InMemoryDocument(xml.getBytes(UTF_8));
    XAdESSignatureParameters parameters =
        new TrustedListV5SignatureParametersBuilder(
                new CertificateToken(signerCertificate), unsigned)
            .build();
    XAdESService service = new XAdESService(new CommonCertificateVerifier());
    ToBeSigned dataToSign = service.getDataToSign(unsigned, parameters);
    SignatureAlgorithm algorithm = parameters.getSignatureAlgorithm();
    Signature signature = Signature.getInstance(algorithm.getJCEId());
    signature.initSign(signer.getPrivate());
    signature.update(dataToSign.getBytes());
    SignatureValue signatureValue = new SignatureValue(algorithm, signature.sign());
    return DSSUtils.toByteArray(service.signDocument(unsigned, parameters, signatureValue));
  }

  @SneakyThrows
  private Path writeTruststore(Path path) {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    keyStore.setCertificateEntry("lotl-signer", listOfTrustedListsSignerCertificate);
    try (var out = new FileOutputStream(path.toFile())) {
      keyStore.store(out, TRUSTSTORE_PASSWORD.toCharArray());
    }
    return path;
  }

  @SneakyThrows
  private static KeyPair rsaKeyPair() {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  @SneakyThrows
  private static X509Certificate selfSigned(String subject, KeyPair keyPair) {
    X500Name name = new X500Name(subject);
    var builder =
        new JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(System.nanoTime()),
            Date.from(Instant.parse("2020-01-01T00:00:00Z")),
            Date.from(Instant.parse("2099-01-01T00:00:00Z")),
            name,
            keyPair.getPublic());
    var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }
}
