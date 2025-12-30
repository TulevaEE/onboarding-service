package ee.tuleva.onboarding.auth.idcard.normalizer;

public interface CertificateNormalizer {
  String normalizeOid(String oid);

  String normalizeIssuer(String issuer);
}
