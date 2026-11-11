package ee.tuleva.onboarding.auth.partner;

interface HandoverTokenUses {

  boolean firstUse(String issuer, String tokenId);
}
