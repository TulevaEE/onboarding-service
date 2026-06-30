package ee.tuleva.onboarding.event;

// Each value must declare whether it is publishable by a client via POST /v1/t.
// Server-emitted events (auth, ledger, audit of represented-party actions) must be
// clientPublishable=false so authenticated callers can't forge event_log rows that
// claim a privileged server-side action happened. When adding a new value, decide
// deliberately: client-tracked instrumentation (page views, clicks) is true;
// anything published from a @Service / @Component is false.
public enum TrackableEventType {
  LOGIN(false),
  GET_ACCOUNT_STATEMENT(false),
  GET_CASH_FLOWS(false),
  MANDATE_SUCCESSFUL(false),
  MANDATE_DENIED(false),
  PAGE_VIEW(true),
  PAYMENT_LINK(false),
  CLICK(true),
  CAPITAL_TRANSFER_STATE_CHANGE(false),
  SAVINGS_FUND_ONBOARDING_STATUS_CHANGE(false),
  SUBSCRIPTION_BATCH_CREATED(false),
  REPRESENT_MINOR_ROLE_SWITCH(false),
  MINOR_CUSTODY_VERIFICATION(false),
  MINOR_DEPOSIT_VERIFIED(false),
  MINOR_REDEMPTION(false);

  private final boolean clientPublishable;

  TrackableEventType(boolean clientPublishable) {
    this.clientPublishable = clientPublishable;
  }

  public boolean isClientPublishable() {
    return clientPublishable;
  }
}
