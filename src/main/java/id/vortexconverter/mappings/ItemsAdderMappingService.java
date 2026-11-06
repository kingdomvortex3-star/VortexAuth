
package id.vortexconverter.mappings;

import id.vortexconverter.scanner.ItemsAdderConfigParser;
import id.vortexconverter.scanner.ItemsAdderConfigParser.ParseResult;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class ItemsAdderMappingService {

    private final ItemsAdderConfigParser parser;
    private final ItemsAdderCustomMappingGenerator generator;
    private final ItemsAdderMappingExporter exporter;

    public ItemsAdderMappingService() {
        this.parser = new ItemsAdderConfigParser();
        this.generator = new ItemsAdderCustomMappingGenerator();
        this.exporter = new ItemsAdderMappingExporter();
    }

    public ServiceResult generateAndExport(
            File itemsAdderContents,
            File geyserDataDirectory
    ) throws IOException {

        Objects.requireNonNull(
                itemsAdderContents,
                "ItemsAdder contents directory cannot be null."
        );

        Objects.requireNonNull(
                geyserDataDirectory,
                "Geyser data directory cannot be null."
        );

        if (!itemsAdderContents.exists()
                || !itemsAdderContents.isDirectory()) {
            throw new IOException(
                    "ItemsAdder contents directory does not exist: "
                            + itemsAdderContents.getPath()
            );
        }

        if (!geyserDataDirectory.exists()
                && !geyserDataDirectory.mkdirs()) {
            throw new IOException(
                    "Cannot create Geyser data directory: "
                            + geyserDataDirectory.getPath()
            );
        }

        if (!geyserDataDirectory.isDirectory()) {
            throw new IOException(
                    "Geyser data path is not a directory."
            );
        }

        ParseResult parseResult = parser.parse(itemsAdderContents);

        if (parseResult.hasErrors()
                && parseResult.getFilesRead() == 0) {
            throw new IOException(
                    "ItemsAdder parsing failed: "
                            + String.join("; ", parseResult.getErrors())
            );
        }

        ItemsAdderCustomMappingGenerator.MappingResult mappingResult =
                generator.generate(parseResult);

        if (!mappingResult.hasMappings()) {
            return new ServiceResult(
                    false,
                    parseResult.getFilesRead(),
                    parseResult.getAssets().size(),
                    mappingResult.discoveredItems(),
                    0,
                    mappingResult.skippedCount(),
                    mappingResult.warnings(),
                    mappingResult.skipped(),
                    parseResult.getErrors(),
                    null
            );
        }

        ItemsAdderMappingExporter.ExportResult exportResult =
                exporter.export(
                        mappingResult,
                        geyserDataDirectory
                );

        return new ServiceResult(
                exportResult.isSuccessful(),
                parseResult.getFilesRead(),
                parseResult.getAssets().size(),
                exportResult.discoveredItems(),
                exportResult.exportedMappings(),
                exportResult.skippedItems(),
                mappingResult.warnings(),
                mappingResult.skipped(),
                parseResult.getErrors(),
                exportResult.mappingFile()
        );
    }

    public record ServiceResult(
            boolean successful,
            int filesRead,
            int totalAssets,
            int discoveredItems,
            int exportedMappings,
            int skippedItems,
            List<String> warnings,
            List<String> skippedDetails,
            List<String> parseErrors,
            File outputFile
    ) {
        public ServiceResult {
            warnings = List.copyOf(warnings);
            skippedDetails = List.copyOf(skippedDetails);
            parseErrors = List.copyOf(parseErrors);
        }

        public boolean hasOutput() {
            return outputFile != null && outputFile.isFile();
        }

        public int warningCount() {
            return warnings.size();
        }

        public int parseErrorCount() {
            return parseErrors.size();
        }

        public boolean hasSkippedItems() {
            return skippedItems > 0;
        }
    }
}
