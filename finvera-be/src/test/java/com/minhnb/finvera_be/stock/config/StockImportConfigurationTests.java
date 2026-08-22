package com.minhnb.finvera_be.stock.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the directory-scan support added so export_all_symbols.py's per-symbol batch output
 * (hundreds of files in one folder) can be imported without one app restart per file. */
class StockImportConfigurationTests {

    @Test
    void importsEveryMatchingFileInADirectoryAndSkipsOthers(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("daily-bars-aaa-2024-01-01-2026-08-20.json"), "a");
        Files.writeString(dir.resolve("daily-bars-aam-2024-01-01-2026-08-20.json"), "b");
        Files.writeString(dir.resolve("fundamentals-aaa-quarter.json"), "c"); // different dataset, must be ignored
        Files.writeString(dir.resolve("full-universe-checkpoint.json"), "d"); // must be ignored

        List<String> imported = new ArrayList<>();
        StockImportConfiguration.importAll("daily-bar", dir.toString(), "daily-bars-*.json",
                file -> imported.add(file.getFileName().toString()));

        assertThat(imported).containsExactlyInAnyOrder(
                "daily-bars-aaa-2024-01-01-2026-08-20.json", "daily-bars-aam-2024-01-01-2026-08-20.json");
    }

    @Test
    void aSingleFilePathStillWorksUnchanged(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("daily-bars-aaa-2024-01-01-2026-08-20.json");
        Files.writeString(file, "a");

        List<Path> imported = new ArrayList<>();
        StockImportConfiguration.importAll("daily-bar", file.toString(), "daily-bars-*.json", imported::add);

        assertThat(imported).containsExactly(file);
    }

    @Test
    void oneRejectedFileDoesNotStopTheRestOfTheBatch() throws IOException {
        Path dir = Files.createTempDirectory("stock-import-test");
        try {
            Files.writeString(dir.resolve("daily-bars-aaa-x.json"), "a");
            Files.writeString(dir.resolve("daily-bars-bad-x.json"), "b");
            Files.writeString(dir.resolve("daily-bars-ccc-x.json"), "c");

            List<String> succeeded = new ArrayList<>();
            StockImportConfiguration.importAll("daily-bar", dir.toString(), "daily-bars-*.json", file -> {
                if (file.getFileName().toString().contains("bad")) {
                    throw new IllegalArgumentException("simulated rejection");
                }
                succeeded.add(file.getFileName().toString());
            });

            assertThat(succeeded).containsExactlyInAnyOrder("daily-bars-aaa-x.json", "daily-bars-ccc-x.json");
        } finally {
            try (var stream = Files.list(dir)) {
                stream.forEach(p -> p.toFile().delete());
            }
            Files.delete(dir);
        }
    }

    @Test
    void aBlankPackagePathIsANoOp() {
        List<Path> imported = new ArrayList<>();
        StockImportConfiguration.importAll("daily-bar", "", "daily-bars-*.json", imported::add);
        StockImportConfiguration.importAll("daily-bar", "   ", "daily-bars-*.json", imported::add);

        assertThat(imported).isEmpty();
    }
}
