package ee.tuleva.onboarding.savings.fund.nav;

import java.util.List;

public final class NavReportAccountNames {

  static final String CASH = "Cash account in SEB Pank";
  static final String TRADE_RECEIVABLES = "Total receivables of unsettled transactions";
  static final String TRADE_PAYABLES = "Total payables of unsettled transactions";
  static final String PENDING_SUBSCRIPTIONS = "Receivables of outstanding units";
  static final String PENDING_REDEMPTIONS = "Payables of redeemed units";
  static final String BLACKROCK_RECEIVABLE = "Other receivables";
  static final String BLACKROCK_LIABILITY = "Liabilities Other";
  static final String MANAGEMENT_FEE = "Management fee";
  static final String CUSTODY_FEE = "Custody fee";
  static final String UNITS_OUTSTANDING = "Total outstanding units:";
  static final String NET_ASSET_VALUE = "Net Asset Value";

  // The NAV calculation takes these from our own register or from a manual adjustment, so the
  // custodian position report neither carries them nor is authoritative for them.
  public static final List<String> NOT_SOURCED_FROM_CUSTODIAN =
      List.of(
          PENDING_SUBSCRIPTIONS, PENDING_REDEMPTIONS, BLACKROCK_RECEIVABLE, BLACKROCK_LIABILITY);

  private NavReportAccountNames() {}
}
