
package id.vortexconverter.mappings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class ItemsAdderMappingExporter {

    private static final String MAPPING_FILE_NAME =
            "vortexconverter-itemsadder.json";

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public ExportResult export(
            ItemsAdderCustomMappingGenerator.MappingResult mappingResult,
            File geyserDataDirectory
    ) throws IOException {

        Objects.requireNonNull(
                mappingResult,
                "Mapping result cannot be null."
        );

        Objects.requireNonNull(
                geyserDataDirectory,
                "Geyser directory cannot be null."
        );

        if (!mappingResult.hasMappings()) {
            throw new IOException(
                    "No generated mappings available for export."
            );
        }

        Path geyserPath = geyserDataDirectory
                .toPath()
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(geyserPath);

        if (!Files.isDirectory(geyserPath)) {
            throw new IOException(
                    "Geyser data path is not a directory."
            );
        }

        Path mappingsDirectory = geyserPath
                .resolve("custom_mappings")
                .normalize();

        if (!mappingsDirectory.startsWith(geyserPath)) {
            throw new IOException(
                    "Invalid custom mappings directory."
            );
        }

        Files.createDirectories(mappingsDirectory);

        if (!Files.isDirectory(mappingsDirectory)) {
            throw new IOException(
                    "Cannot create Geyser custom_mappings directory."
            );
        }

        Path targetFile = mappingsDirectory
                .resolve(MAPPING_FILE_NAME)
                .normalize();

        if (!targetFile.startsWith(mappingsDirectory)) {
            throw new IOException(
                    "Invalid mapping output path."
            );
        }

        JsonObject mapping = mappingResult.mapping();

        if (mapping == null || mapping.entrySet().isEmpty()) {
            throw new IOException(
                    "Generated mapping JSON is empty."
            );
        }

        Path temporaryFile = Files.createTempFile(
                mappingsDirectory,
                "vortexconverter-",
                ".tmp"
        );

        try {
            Files.writeString(
                    temporaryFile,
                    gson.toJson(mapping),
                    StandardCharsets.UTF_8
            );

            try {
                Files.move(
                        temporaryFile,
                        targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporaryFile,
                        targetFile,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            Files.deleteIfExists(temporaryFile);
        }

        return new ExportResult(
                targetFile.toFile(),
                mappingResult.discoveredItems(),
                mappingResult.generatedMappings(),
                mappingResult.skippedCount(),
                mappingResult.warningCount()
        );
    }

    public record ExportResult(
            File mappingFile,
            int discoveredItems,
            int exportedMappings,
            int skippedItems,
            int warningCount
    ) {
        public boolean isSuccessful() {
            return mappingFile != null
                    && mappingFile.isFile()
                    && exportedMappings > 0;
        }
    }
}
