
package id.vortexconverter.mappings;

import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ItemsAdderMappingService {

    private final ItemsAdderConfigParser parser;
    private final ItemsAdderCustomMappingGenerator generator;
    private final ItemsAdderMappingValidator validator;
    private final ItemsAdderMappingExporter exporter;

    public ItemsAdderMappingService() {
        this.parser = new ItemsAdderConfigParser();
        this.generator = new ItemsAdderCustomMappingGenerator();
        this.validator = new ItemsAdderMappingValidator();
        this.exporter = new ItemsAdderMappingExporter();
    }

    public ServiceResult generateAndExport(
            File itemsAdderContents,
            File geyserDataDirectory
    ) throws IOException {

        if (itemsAdderContents == null
                || !itemsAdderContents.exists()
                || !itemsAdderContents.isDirectory()) {
            throw new IOException(
                    "Folder ItemsAdder contents tidak ditemukan: "
                            + itemsAdderContents
            );
        }

        if (geyserDataDirectory == null) {
            throw new IOException(
                    "Folder data Geyser tidak ditentukan."
            );
        }

        ItemsAdderConfigParser.ParseResult parseResult =
                parser.parse(itemsAdderContents);

        if (parseResult == null) {
            throw new IOException(
                    "ItemsAdder parser tidak menghasilkan ParseResult."
            );
        }

        if (parseResult.getFilesRead() == 0) {
            throw new IOException(
                    "Tidak ada file konfigurasi ItemsAdder yang berhasil dibaca."
            );
        }

        ItemsAdderCustomMappingGenerator.MappingResult mappingResult =
                generator.generate(parseResult);

        if (mappingResult == null) {
            throw new IOException(
                    "Mapping generator tidak menghasilkan hasil."
            );
        }

        ItemsAdderMappingValidator.ValidationResult validationResult =
                validator.validate(mappingResult);

        List<String> warnings = new ArrayList<>(
                mappingResult.warnings()
        );

        warnings.addAll(validationResult.warnings());

        List<String> skippedDetails = new ArrayList<>(
                mappingResult.skipped()
        );

        List<String> parseErrors = new ArrayList<>();

        if (parseResult.hasErrors()) {
            warnings.add(
                    "ItemsAdder parser melaporkan kesalahan. "
                            + "Periksa konfigurasi ItemsAdder."
            );
        }

        if (!validationResult.isSuccessful()) {
            throw new IOException(
                    "Mapping gagal divalidasi: "
                            + String.join(
                                    " | ",
                                    validationResult.errors()
                            )
            );
        }

        if (!mappingResult.hasMappings()) {
            throw new IOException(
                    "Tidak ada mapping valid untuk diekspor."
            );
        }

        ItemsAdderCustomMappingGenerator.MappingResult exportedResult =
                exporter.export(
                        mappingResult,
                        geyserDataDirectory
                );

        if (exportedResult == null) {
            throw new IOException(
                    "Mapping exporter tidak mengembalikan hasil."
            );
        }

        File outputFile = exportedResult.outputFile();

        if (outputFile == null || !outputFile.isFile()) {
            throw new IOException(
                    "File mapping tidak ditemukan setelah proses export."
            );
        }

        return new ServiceResult(
                true,
                parseResult.getFilesRead(),
                parseResult.getAssets().size(),
                mappingResult.discoveredItems(),
                mappingResult.generatedMappings(),
                mappingResult.skippedCount(),
                warnings,
                skippedDetails,
                parseErrors,
                outputFile
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
        public boolean isSuccessful() {
            return successful;
        }
    }
}
