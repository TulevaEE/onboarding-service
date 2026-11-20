package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.tulevafund.TulevaFund;

record SkippedListingCheck(String cause, String action) {

  private static final String TELL_IT = "Teata IT-le, viga on logis.";

  static final SkippedListingCheck UNREADABLE_REPORT =
      new SkippedListingCheck(
          "viimast ootel-tehingute raportit ei saanud lugeda (raport puudub, read on vigased või"
              + " As-of kuupäev puudub)",
          "Kontrolli, kas SEB ootel-tehingute raport tuli kohale ja on loetav.");
  static final SkippedListingCheck REPORT_LOOKUP_FAILED =
      new SkippedListingCheck(
          "SEB ootel-tehingute raportit ei saanud andmebaasist lugeda", TELL_IT);
  static final SkippedListingCheck DROPPED_TRADES =
      new SkippedListingCheck("nimekirjast kadunud tehinguid ei saanud kontrollida", TELL_IT);
  static final SkippedListingCheck NEVER_LISTED_ORDERS =
      new SkippedListingCheck("SEB-sse jõudmata tehingukorraldusi ei saanud kontrollida", TELL_IT);

  static SkippedListingCheck unreadableStatementOf(TulevaFund fund) {
    return new SkippedListingCheck(
        "fondi " + fund.getCode() + " pangaväljavõtet ei saanud lugeda", TELL_IT);
  }
}
