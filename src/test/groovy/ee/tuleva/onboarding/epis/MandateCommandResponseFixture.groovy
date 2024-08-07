package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse

class MandateCommandResponseFixture {
  static MandateCommandResponse sampleMandateCommandResponse(String processId, boolean successful, Integer errorCode, String errorMessage) {
    return new MandateCommandResponse(processId, successful, errorCode, errorMessage)
  }
}
