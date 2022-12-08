package ee.tuleva.onboarding.conversion

class ConversionResponseFixture {

  static ConversionResponse notFullyConverted() {
    return ConversionResponse.builder()
        .secondPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(true)
            .transfersPartial(true)
            .selectionComplete(false)
            .selectionPartial(false)
            .build()
        ).thirdPillar(ConversionResponse.Conversion.builder()
        .transfersComplete(true)
        .transfersPartial(true)
        .selectionComplete(false)
        .selectionPartial(false)
        .build()
    ).build()
  }

  static ConversionResponse fullyConverted() {
    return ConversionResponse.builder()
        .secondPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(true)
            .transfersPartial(true)
            .selectionComplete(true)
            .selectionPartial(true)
            .build()
        ).thirdPillar(ConversionResponse.Conversion.builder()
        .transfersComplete(true)
        .transfersPartial(true)
        .selectionComplete(true)
        .selectionPartial(true)
        .build()
    ).build()
  }

}
