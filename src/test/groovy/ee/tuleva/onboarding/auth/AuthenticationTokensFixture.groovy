package ee.tuleva.onboarding.auth

class AuthenticationTokensFixture {
  static AuthenticationTokens sampleAuthenticationTokens() {
    return new AuthenticationTokens("sampleAccessToken", "sampleRefreshToken")
  }
}
