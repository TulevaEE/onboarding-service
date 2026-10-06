package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.FlowType;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.sk.smartid.rest.dao.SessionSignature;
import ee.sk.smartid.rest.dao.SessionStatus;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class SmartIdFlowTypes {

  private SmartIdFlowTypes() {}

  static @Nullable FlowType ofAProducedSignature(SessionStatus status) {
    SessionSignature signature = status.getSignature();
    if (signature == null) {
      return null;
    }
    String flowType = signature.getFlowType();
    if (flowType == null || !FlowType.isSupported(flowType)) {
      throw new UnprocessableSmartIdResponseException(
          "Unusable Smart-ID flow type: flowType=" + flowType);
    }
    return FlowType.fromString(flowType);
  }

  static void requireOffered(@Nullable FlowType flowType, Set<FlowType> offered) {
    if (flowType != null && !offered.contains(flowType)) {
      throw new UnprocessableSmartIdResponseException(
          "Unexpected Smart-ID flow type: flowType=" + flowType);
    }
  }
}
