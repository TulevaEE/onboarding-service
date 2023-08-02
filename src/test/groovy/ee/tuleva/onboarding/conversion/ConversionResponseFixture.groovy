package ee.tuleva.onboarding.conversion

class ConversionResponseFixture {

  static ConversionResponse notFullyConverted() {
    return ConversionResponse.builder()
        .secondPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(true)
            .transfersPartial(true)
            .selectionComplete(false)
            .selectionPartial(false)
            .weightedAverageFee(0.51)
            .build()
        ).thirdPillar(ConversionResponse.Conversion.builder()
        .transfersComplete(true)
        .transfersPartial(true)
        .selectionComplete(false)
        .selectionPartial(false)
        .weightedAverageFee(0.52)
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
            .weightedAverageFee(0.49)
            .build()
        ).thirdPillar(ConversionResponse.Conversion.builder()
        .transfersComplete(true)
        .transfersPartial(true)
        .selectionComplete(true)
        .selectionPartial(true)
        .weightedAverageFee(0.48)
        .build()
    ).build()
  }

}
