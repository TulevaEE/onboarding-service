package ee.tuleva.onboarding.investment.fundpage;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.Complete;
import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.Gaps;
import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class FundPageDisclosureNotifier {

  private static final String SHARED_DOCUMENTS_PAGE = "Shared documents page";
  private static final String LINE_INDENT = "  ";
  private static final String ACTION = "Set them through agent-gateway fund_pages.";

  private enum Mark {
    MISSING("🔴", "🔴 Required documents are missing from the fund pages on tuleva.ee"),
    NO_EFFECTIVE_DATE(
        "⚠️", "⚠️ A fund page document on tuleva.ee has no effective date in its filename"),
    NOT_CHECKED("⏸", "⚠️ Could not check the fund pages' documents on tuleva.ee");

    private final String icon;
    private final String headline;

    Mark(String icon, String headline) {
      this.icon = icon;
      this.headline = headline;
    }
  }

  private record Line(Mark mark, String text) {

    String render() {
      return LINE_INDENT + mark.icon + " " + text;
    }
  }

  private final OperationsNotificationService notificationService;

  void notify(List<FundPageDisclosures> pages) {
    try {
      var lines = lines(pages);
      if (lines.isEmpty()) {
        return;
      }
      var worst = lines.getFirst().mark();
      notificationService.sendMessage(
          message(worst, lines), INVESTMENT, worst == Mark.MISSING ? ERROR : WARNING);
    } catch (Exception e) {
      log.error("Failed to send fund page disclosure notification: pages={}", pages, e);
    }
  }

  private static String message(Mark worst, List<Line> lines) {
    return Stream.of(
            Stream.of(worst.headline), lines.stream().map(Line::render), actionAfter(worst))
        .flatMap(identity())
        .collect(joining("\n"));
  }

  private static Stream<String> actionAfter(Mark worst) {
    return worst == Mark.NOT_CHECKED ? Stream.empty() : Stream.of(ACTION);
  }

  private static List<Line> lines(List<FundPageDisclosures> pages) {
    return Stream.concat(
            pages.stream().flatMap(FundPageDisclosureNotifier::fundPageLines),
            sharedDocumentsPageLines(pages))
        .sorted(comparing(Line::mark))
        .toList();
  }

  private static Stream<Line> fundPageLines(FundPageDisclosures page) {
    return switch (page) {
      case Complete _ -> Stream.empty();
      case Gaps(var fund, var gaps) ->
          gapLines(
              fund.getCode(),
              gaps.stream().filter(not(FundPageDisclosureNotifier::isOnTheSharedPage)).toList());
      case NotChecked(var fund, var reason) ->
          Stream.of(
              new Line(Mark.NOT_CHECKED, "%s: not checked — %s".formatted(fund.getCode(), reason)));
    };
  }

  private static Stream<Line> sharedDocumentsPageLines(List<FundPageDisclosures> pages) {
    return gapLines(
        SHARED_DOCUMENTS_PAGE,
        pages.stream()
            .flatMap(FundPageDisclosureNotifier::listedGaps)
            .filter(FundPageDisclosureNotifier::isOnTheSharedPage)
            .distinct()
            .toList());
  }

  private static Stream<DisclosureGap> listedGaps(FundPageDisclosures page) {
    return page instanceof Gaps listed ? listed.gaps().stream() : Stream.empty();
  }

  private static boolean isOnTheSharedPage(DisclosureGap gap) {
    return gap.page() == Page.SHARED;
  }

  private static Stream<Line> gapLines(String pageName, List<DisclosureGap> gaps) {
    return gaps.stream()
        .collect(
            groupingBy(
                DisclosureGap::problem,
                LinkedHashMap::new,
                mapping(DisclosureGap::field, toList())))
        .entrySet()
        .stream()
        .map(
            fieldsWithTheProblem ->
                gapLine(pageName, fieldsWithTheProblem.getKey(), fieldsWithTheProblem.getValue()));
  }

  private static Line gapLine(String pageName, Problem problem, List<String> fields) {
    return switch (problem) {
      case MISSING ->
          new Line(Mark.MISSING, "%s: %s".formatted(pageName, String.join(", ", fields)));
      case NO_EFFECTIVE_DATE ->
          new Line(
              Mark.NO_EFFECTIVE_DATE, "%s: %s".formatted(pageName, withoutEffectiveDate(fields)));
    };
  }

  private static String withoutEffectiveDate(List<String> fields) {
    return fields.size() == 1
        ? fields.getFirst() + " has no effective date in its filename"
        : String.join(", ", fields) + " have no effective date in their filenames";
  }
}
