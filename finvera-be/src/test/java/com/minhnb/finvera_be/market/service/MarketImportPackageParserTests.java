package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketImportPackageParserTests {
    @Test
    void parsesCanonicalFileWithoutChangingChecksumPayload() throws Exception {
        String payload = "{\"records\":[]}";
        String checksum = MarketImportService.sha256(payload);
        var file = Files.createTempFile("finvera-import-", ".json");
        Files.writeString(file, """
                {"contractVersion":"vnstock-history-private-bootstrap-v1","toolName":"finvera-vnstock-exporter","toolVersion":"0.1.0","upstreamSource":"VNSTOCK_KBS","packageSha256":"%s","canonicalPayload":"{\\"records\\":[]}","generatedAt":"2026-08-17T03:00:00Z","rangeStart":"2026-08-15","rangeEnd":"2026-08-15","records":[]}
                """.formatted(checksum));

        var parsed = new MarketImportPackageParser().parse(file);

        assertThat(parsed.canonicalPayload()).isEqualTo(payload);
        assertThat(parsed.packageSha256()).isEqualTo(checksum);
        assertThat(parsed.records()).isEmpty();
    }

    @Test
    void rejectsMalformedPackage() throws Exception {
        var file = Files.createTempFile("finvera-import-invalid-", ".json");
        Files.writeString(file, "{}");
        assertThatThrownBy(() -> new MarketImportPackageParser().parse(file))
                .hasMessage("INVALID_IMPORT_PACKAGE");
    }
}
