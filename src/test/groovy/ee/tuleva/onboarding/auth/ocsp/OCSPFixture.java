package ee.tuleva.onboarding.auth.ocsp;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.xml.bind.DatatypeConverter;
import sun.security.x509.AccessDescription;
import sun.security.x509.AlgorithmId;
import sun.security.x509.AuthorityInfoAccessExtension;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.GeneralName;
import sun.security.x509.URIName;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

public class OCSPFixture {
  public static String sampleExampleServer = "http://aia.sk.ee/esteid2015";
  public static String getSampleCertificatePEM =
      "-----BEGIN CERTIFICATE-----\n"
          + "MO+/vQVXMO+/vQTvv73vv70DAgECAhB1R++/ve+/vRR0S++/vVvvv71m77+977+9\n"
          + "ZlXvv70wCgYIKu+/vUjvv709BAMEMFoxCzAJBgNVBAYTAkVFMRswGQYDVQQKDBJT\n"
          + "SyBJRCBTb2x1dGlvbnMgQVMxFzAVBgNVBGEMDk5UUkVFLTEwNzQ3MDEzMRUwEwYD\n"
          + "VQQDDAxFRS1Hb3ZDQTIwMTgwHhcKMTgwOTIwMDkyMjI4WhcKMzMwOTA1MDkxMTAz\n"
          + "WjBYMQswCQYDVQQGEwJFRTEbMBkGA1UECgwSU0sgSUQgU29sdXRpb25zIEFTMRcw\n"
          + "FQYDVQRhDA5OVFJFRS0xMDc0NzAxMzETMBEGA1UEAwwKRVNURUlEMjAxODDvv73v\n"
          + "v70wEAYHKu+/vUjvv709AgEGBSvvv70EICMD77+977+9IAQB77+9OBlv77+9Su+/\n"
          + "vT3vv73vv73vv714Tm/vv71A77+977+9Q27vv70y77+9JUzvv73vv71x77+9Wu+/\n"
          + "vR1F77+977+977+9ZO+/vRvHuApk77+9NO+/ve+/vVhE77+9Su+/vQfvv71s77+9\n"
          + "N++/vW8F77+9JBPvv71ZAe+/vSPvv70t77+9S1xw77+9I3jvv73vv73vv70T77+9\n"
          + "dzUbZe+/vRvvv73vv71H77+9CBUG77+9V++/vRpLTgXvv73vv73vv71IMhU8DHBW\n"
          + "Fu+/ve+/vWjvv73vv715SkPvv70g77+9cu+/vWwrREUS77+977+9Ax0w77+9Axkw\n"
          + "HwYDVR0jBBgwFu+/vRR+KVbvv70077+9eE5377+9by4zKu+/vXHvv73vv70077+9\n"
          + "MB0GA1UdDgQWBBTZrHDvv71ffu+/ve+/ve+/ve+/ve+/vUfvv73vv70077+977+9\n"
          + "KhIwDgYDVR0PAQHvv70EBAMCAQYwEgYDVR0TAQHvv70ECDAGAQHvv70CASAw77+9\n"
          + "Ae+/vQYDVR0gBO+/vQHvv70w77+9Ae+/vTAIBgYEIO+/vXoBAjAJBgcEIO+/ve+/\n"
          + "vUABAjAyBgsrBgEEAe+/ve+/vSEBAQEwIzAhBggrBgEFBQcCARYVaHR0cHM6Ly93\n"
          + "d3cuc2suZWUvQ1BTMAoGCysGAQQB77+977+9IQEBAjAKBgsrBgEEAe+/ve+/vX8B\n"
          + "AQEwCgYLKwYBBAHvv73vv70hAQEFMAoGCysGAQQB77+977+9IQEBBjAKBgsrBgEE\n"
          + "Ae+/ve+/vSEBAQcwCgYLKwYBBAHvv73vv70hAQEDMAoGCysGAQQB77+977+9IQEB\n"
          + "BDAKBgsrBgEEAe+/ve+/vSEBAQgwCgYLKwYBBAHvv73vv70hAQEJMAoGCysGAQQB\n"
          + "77+977+9IQEBCjAKBgsrBgEEAe+/ve+/vSEBAQswCgYLKwYBBAHvv73vv70hAQEM\n"
          + "MAoGCysGAQQB77+977+9IQEBCjAKBgsrBgEEAe+/ve+/vSEBAQ4wCgYLKwYBBAHv\n"
          + "v73vv70hAQEPMAoGCysGAQQB77+977+9IQEBEDAKBgsrBgEEAe+/ve+/vSEBAREw\n"
          + "CgYLKwYBBAHvv73vv70hAQESMAoGCysGAQQB77+977+9IQEBEzAKBgsrBgEEAe+/\n"
          + "ve+/vSEBARQwCgYLKwYBBAHvv73vv71/AQECMAoGCysGAQQB77+977+9fwEBAzAK\n"
          + "BgsrBgEEAe+/ve+/vX8BAQQwCgYLKwYBBAHvv73vv71/AQEFMAoGCysGAQQB77+9\n"
          + "77+9fwEBBjAqBgNVHSUBAe+/vQQgMB4GCCsGAQUFBwMJBggrBgEFBQcDAgYIKwYB\n"
          + "BQUHAwQwagYIKwYBBQUHAQEEXjBcMCkGCCsGAQUFBzAB77+9HWh0dHA6Ly9haWEu\n"
          + "c2suZWUvZWUtZ292Y2EyMDE4MC8GCCsGAQUFBzAC77+9I2h0dHA6Ly9jLnNrLmVl\n"
          + "L0VFLUdvdkNBMjAxOC5kZXIuY3J0MBgGCCsGAQUFBwEDBAwwCjAIBgYEIO+/vUYB\n"
          + "ATAwBgNVHR8EKTAnMCXvv70j77+9Ie+/vR9odHRwOi8vYy5zay5lZS9FRS1Hb3ZD\n"
          + "QTIwMTguY3JsMAoGCCrvv71I77+9PQQDBAPvv73vv70gMO+/ve+/vQJCIN65Rjgd\n"
          + "77+977+9bFLvv73vv71N77+9Z++/vSByWBh3O0fvv70JRDckzLVxOnTvv71R77+9\n"
          + "Ju+/vVJoQQggNO+/ve+/ve+/vW8hSe+/vW9i77+977+977+977+977+95pCTNO+/\n"
          + "vTE9KgJCASPvv70D77+9OSHvv71nKjTvv70877+9B0JTIu+/vWwh77+977+9Ou+/\n"
          + "vVxzH++/ve+/ve+/vRlfUyAG77+9yZk+fe+/ve+/vT/vv73vv70TXu+/vQQMA++/\n"
          + "vUdUQAnvv70877+977+9W3Vd\n"
          + "-----END CERTIFICATE-----";
  public static String sampleCertificateDER =
      "0�\u0005W0�\u0004��\u0003\u0002\u0001\u0002\u0002\u0010uG��\u0014tK�[�f��fU�0\n"
          + "\u0006\b*�H�=\u0004\u0003\u00040Z1\u000B0\t\u0006\u0003U\u0004\u0006\u0013\u0002EE1\u001B0\u0019\u0006\u0003U\u0004\n"
          + "\f\u0012SK ID Solutions AS1\u00170\u0015\u0006\u0003U\u0004a\f\u000ENTREE-107470131\u00150\u0013\u0006\u0003U\u0004\u0003\f\fEE-GovCA20180\u001E\u0017\n"
          + "180920092228Z\u0017\n"
          + "330905091103Z0X1\u000B0\t\u0006\u0003U\u0004\u0006\u0013\u0002EE1\u001B0\u0019\u0006\u0003U\u0004\n"
          + "\f\u0012SK ID Solutions AS1\u00170\u0015\u0006\u0003U\u0004a\f\u000ENTREE-107470131\u00130\u0011\u0006\u0003U\u0004\u0003\f\n"
          + "ESTEID20180��0\u0010\u0006\u0007*�H�=\u0002\u0001\u0006\u0005+�\u0004 #\u0003�� \u0004\u0001�8\u0019o�J�=���xNo�@��Cn�2�%L��q�Z�\u001DE���d�\u001BǸ\n"
          + "d�4��XD�J�\u0007�l�7�o\u0005�$\u0013�Y\u0001�#�-�K\\p�#x���\u0013�w5\u001Be�\u001B��G�\b\u0015\u0006�W�\u001AKN\u0005���H2\u0015<\fpV\u0016��h��yJC� �r�l+DE\u0012��\u0003\u001D0�\u0003\u00190\u001F\u0006\u0003U\u001D#\u0004\u00180\u0016�\u0014~)V�4�xNw�o.3*�q��4�0\u001D\u0006\u0003U\u001D\u000E\u0004\u0016\u0004\u0014٬p�_~�����G��4��*\u00120\u000E\u0006\u0003U\u001D\u000F\u0001\u0001�\u0004\u0004\u0003\u0002\u0001\u00060\u0012\u0006\u0003U\u001D\u0013\u0001\u0001�\u0004\b0\u0006\u0001\u0001�\u0002\u0001 0�\u0001�\u0006\u0003U\u001D \u0004�\u0001�0�\u0001�0\b\u0006\u0006\u0004 �z\u0001\u00020\t\u0006\u0007\u0004 ��@\u0001\u000202\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00010#0!\u0006\b+\u0006\u0001\u0005\u0005\u0007\u0002\u0001\u0016\u0015https://www.sk.ee/CPS0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00020\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��\u007F\u0001\u0001\u00010\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00050\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00060\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00070\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00030\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00040\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\b0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\t0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\n"
          + "0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u000B0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\f0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\n"
          + "0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u000E0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u000F0\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00100\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00110\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00120\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00130\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��!\u0001\u0001\u00140\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��\u007F\u0001\u0001\u00020\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��\u007F\u0001\u0001\u00030\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��\u007F\u0001\u0001\u00040\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��\u007F\u0001\u0001\u00050\n"
          + "\u0006\u000B+\u0006\u0001\u0004\u0001��\u007F\u0001\u0001\u00060*\u0006\u0003U\u001D%\u0001\u0001�\u0004 0\u001E\u0006\b+\u0006\u0001\u0005\u0005\u0007\u0003\t\u0006\b+\u0006\u0001\u0005\u0005\u0007\u0003\u0002\u0006\b+\u0006\u0001\u0005\u0005\u0007\u0003\u00040j\u0006\b+\u0006\u0001\u0005\u0005\u0007\u0001\u0001\u0004^0\\0)\u0006\b+\u0006\u0001\u0005\u0005\u00070\u0001�\u001Dhttp://aia.sk.ee/ee-govca20180/\u0006\b+\u0006\u0001\u0005\u0005\u00070\u0002�#http://c.sk.ee/EE-GovCA2018.der.crt0\u0018\u0006\b+\u0006\u0001\u0005\u0005\u0007\u0001\u0003\u0004\f0\n"
          + "0\b\u0006\u0006\u0004 �F\u0001\u000100\u0006\u0003U\u001D\u001F\u0004)0'0%�#�!�\u001Fhttp://c.sk.ee/EE-GovCA2018.crl0\n"
          + "\u0006\b*�H�=\u0004\u0003\u0004\u0003�� 0��\u0002B \u07B9F8\u001D��lR��M�g� rX\u0018w;G�\tD7$̵q:t�Q�&�RhA\b 4���o!I�ob�����搓4�1=*\u0002B\u0001#�\u0003�9!�g*4�<�\u0007BS\"�l!��:�\\s\u001F���\u0019_S \u0006�ə>}��?��\u0013^�\u0004\f\u0003�GT@\t�<��[u]";

  public static X509Certificate generateCertificate(
      String dn, int days, String algorithm, String urlCA, String urlOCSP)
      throws GeneralSecurityException, IOException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair pair = kpg.generateKeyPair();
    PrivateKey privkey = pair.getPrivate();
    X509CertInfo info = new X509CertInfo();
    Date from = new Date();
    Date to = new Date(from.getTime() + days * 86400000l);
    CertificateValidity interval = new CertificateValidity(from, to);
    BigInteger sn = new BigInteger(64, new SecureRandom());
    X500Name owner = new X500Name(dn, "Unit", "Org", "EE");

    info.set(X509CertInfo.VALIDITY, interval);
    info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
    info.set(X509CertInfo.SUBJECT, owner);
    info.set(X509CertInfo.ISSUER, owner);
    info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
    info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
    AlgorithmId algo = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);
    info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

    CertificateExtensions exts = new CertificateExtensions();
    List accDescr = new ArrayList<>();
    if (urlCA != null) {
      accDescr.add(
          new AccessDescription(
              AccessDescription.Ad_CAISSUERS_Id, new GeneralName(new URIName(urlCA))));
    }
    if (urlOCSP != null) {
      accDescr.add(
          new AccessDescription(
              AccessDescription.Ad_OCSP_Id, new GeneralName(new URIName(urlOCSP))));
    }

    exts.set(AuthorityInfoAccessExtension.NAME, new AuthorityInfoAccessExtension(accDescr));
    info.set(X509CertInfo.EXTENSIONS, exts);
    X509CertImpl cert = new X509CertImpl(info);
    cert.sign(privkey, algorithm);

    algo = (AlgorithmId) cert.get(X509CertImpl.SIG_ALG);
    info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
    cert = new X509CertImpl(info);
    cert.sign(privkey, algorithm);
    return cert;
  }

  public static String certToString(X509Certificate cert) {
    StringWriter sw = new StringWriter();
    try {
      sw.write("-----BEGIN CERTIFICATE-----\n");
      sw.write(
          DatatypeConverter.printBase64Binary(cert.getEncoded()).replaceAll("(.{64})", "$1\n"));
      sw.write("\n-----END CERTIFICATE-----\n");
    } catch (CertificateEncodingException e) {
      e.printStackTrace();
    }
    return sw.toString();
  }
}
