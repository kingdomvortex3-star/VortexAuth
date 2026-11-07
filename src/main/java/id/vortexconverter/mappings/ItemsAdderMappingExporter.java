
package id.vortexconverter.mappings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Map;

public final class ItemsAdderMappingExporter {

    private static final String MAPPING_FILE_NAME =
            "vortexconverter-itemsadder.json";

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final ItemsAdderMappingValidator validator =
            new ItemsAdderMappingValidator();

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

        JsonObject mapping = mappingResult.mapping();

        if (!mappingResult.hasMappings()
                || mapping == null
                || !mapping.has("items")
                || !mapping.get("items").isJsonObject()) {
            throw new IOException(
                    "No generated mappings available for export."
            );
        }

        ItemsAdderMappingValidator.ValidationResult validation =
                validator.validate(mapping);

        if (!validation.isSuccessful()) {
            throw new IOException(
                    "Mapping validation failed: "
                            + String.join("; ", validation.errors())
            );
        }

        int actualMappings = countDefinitions(mapping);

        if (actualMappings <= 0) {
            throw new IOException(
                    "No valid mapping definitions available for export."
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

            // Pastikan file sementara benar-benar berisi JSON valid.
            String exportedJson = Files.readString(
                    temporaryFile,
                    StandardCharsets.UTF_8
            );

            com.google.gson.JsonElement parsed =
                    com.google.gson.JsonParser.parseString(exportedJson);

            if (!parsed.isJsonObject()) {
                throw new IOException(
                        "Temporary mapping file is not a JSON object."
                );
            }

            JsonObject exportedMapping = parsed.getAsJsonObject();

            ItemsAdderMappingValidator.ValidationResult exportedValidation =
                    validator.validate(exportedMapping);

            if (!exportedValidation.isSuccessful()) {
                throw new IOException(
                        "Temporary mapping validation failed: "
                                + String.join(
                                        "; ",
                                        exportedValidation.errors()
                                )
                );
            }

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

        if (!Files.isRegularFile(targetFile)) {
            throw new IOException(
                    "Mapping output file was not created."
            );
        }

        return new ExportResult(
                targetFile.toFile(),
                mappingResult.discoveredItems(),
                actualMappings,
                mappingResult.skippedCount(),
                mappingResult.warningCount()
        );
    }

    private int countDefinitions(JsonObject mapping)
            throws IOException {

        JsonElement itemsElement = mapping.get("items");

        if (itemsElement == null || !itemsElement.isJsonObject()) {
            throw new IOException(
                    "Mapping items must be a JSON object."
            );
        }

        int count = 0;

        for (Map.Entry<String, JsonElement> entry
                : itemsElement.getAsJsonObject().entrySet()) {

            if (!entry.getValue().isJsonArray()) {
                throw new IOException(
                        "Mapping definitions must be an array: "
                                + entry.getKey()
                );
            }

            JsonArray definitions = entry.getValue().getAsJsonArray();

            for (JsonElement definition : definitions) {
                if (!definition.isJsonObject()) {
                    throw new IOException(
                            "Invalid mapping definition: "
                                    + entry.getKey()
                    );
                }

                count++;
            }
        }

        return count;
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
