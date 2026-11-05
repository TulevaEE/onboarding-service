package ee.tuleva.onboarding.investment.report.publishing.wordpress;

final class PublishLockLost extends RuntimeException {

  PublishLockLost(String pageSlug) {
    super("Not updating the fund page, the publish no longer holds its lock: pageSlug=" + pageSlug);
  }
}
