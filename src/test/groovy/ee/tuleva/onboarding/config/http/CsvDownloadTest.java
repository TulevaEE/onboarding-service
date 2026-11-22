package ee.tuleva.onboarding.config.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvDownloadTest {

  @Test
  void writesUtf8WithAByteOrderMarkSemicolonsAndCrlfAndQuotesAValueHoldingASemicolon() {
    var csv =
        CsvDownload.bytes(
            List.of("Kuupäev", "Märkus"),
            List.of(List.of("03.02.2026", "a;b"), List.of("04.02.2026", "õ")));

    assertThat(new String(csv, UTF_8))
        .isEqualTo("﻿Kuupäev;Märkus\r\n03.02.2026;\"a;b\"\r\n04.02.2026;õ\r\n");
  }

  @Test
  void anAttachmentIsUtf8CsvNamedAfterItsIsinWithAnythingButLettersAndDigitsLeftOut() {
    var csv = CsvDownload.bytes(List.of("Kuupäev"), List.of());

    var response = CsvDownload.attachment("nav", "EE00\"00/003283", csv);

    assertThat(response.getHeaders().getFirst(CONTENT_TYPE)).isEqualTo("text/csv;charset=UTF-8");
    assertThat(response.getHeaders().getFirst(CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"nav-EE0000003283.csv\"");
    assertThat(response.getBody()).isEqualTo(csv);
  }
}
