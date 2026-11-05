package ee.tuleva.onboarding.investment.report.publishing.wordpress;

public abstract sealed class PageUpdateFailed extends RuntimeException
    permits PageUpdateFailed.LeftUnchanged,
        PageUpdateFailed.NotLinked,
        PageUpdateFailed.LinkUnknown {

  private final String failedBecause;

  private PageUpdateFailed(String outcome, String pageSlug, RuntimeException decidingFailure) {
    super(
        "Fund page update failed: outcome=" + outcome + ", pageSlug=" + pageSlug, decidingFailure);
    this.failedBecause = InnermostCause.nameOf(decidingFailure);
  }

  public String failedBecause() {
    return failedBecause;
  }

  public static final class LeftUnchanged extends PageUpdateFailed {

    public LeftUnchanged(String pageSlug, RuntimeException lockLost) {
      super("left unchanged", pageSlug, lockLost);
    }
  }

  public static final class NotLinked extends PageUpdateFailed {

    public NotLinked(String pageSlug, RuntimeException decidingFailure) {
      super("not linked", pageSlug, decidingFailure);
    }
  }

  public static final class LinkUnknown extends PageUpdateFailed {

    public LinkUnknown(String pageSlug, RuntimeException decidingFailure) {
      super("link unknown", pageSlug, decidingFailure);
    }
  }
}
