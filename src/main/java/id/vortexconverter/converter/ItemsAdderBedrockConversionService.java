
package id.vortexconverter.converter;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.mappings.ItemsAdderMappingExporter;
import id.vortexconverter.mappings.ItemsAdderMappingValidator;
import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ItemsAdderBedrockConversionService {

    private final ItemsAdderConfigParser parser;
    private final ItemsAdderCustomMappingGenerator mappingGenerator;
    private final ItemsAdderMappingValidator validator;
    private final ItemsAdderMappingExporter exporter;
    private final ItemsAdderBedrockResourcePackBuilder packBuilder;

    public ItemsAdderBedrockConversionService() {
        this.parser = new ItemsAdderConfigParser();
        this.mappingGenerator = new ItemsAdderCustomMappingGenerator();
        this.validator = new ItemsAdderMappingValidator();
        this.exporter = new ItemsAdderMappingExporter();
        this.packBuilder = new ItemsAdderBedrockResourcePackBuilder();
    }

    public ConversionResult convert(
            File itemsAdderContents,
            File geyserDataDirectory
    ) throws IOException {

        if (itemsAdderContents == null
                || !itemsAdderContents.isDirectory()) {
            throw new IOException(
                    "Folder ItemsAdder contents tidak ditemukan."
            );
        }

        if (geyserDataDirectory == null
                || (!geyserDataDirectory.isDirectory()
                && !geyserDataDirectory.mkdirs())) {
            throw new IOException(
                    "Folder data Geyser tidak dapat diakses."
            );
        }

        ItemsAdderConfigParser.ParseResult parseResult =
                parser.parse(itemsAdderContents);

        if (parseResult == null || parseResult.getFilesRead() == 0) {
            throw new IOException(
                    "Tidak ada konfigurasi ItemsAdder yang berhasil dibaca."
            );
        }

        ItemsAdderCustomMappingGenerator.MappingResult mappingResult =
                mappingGenerator.generate(parseResult);

        if (mappingResult == null || !mappingResult.hasMappings()) {
            throw new IOException(
                    "Tidak ada custom item mapping yang dapat dibuat."
            );
        }

        ItemsAdderMappingValidator.ValidationResult validation =
                validator.validate(mappingResult);

        if (!validation.isSuccessful()) {
            throw new IOException(
                    "Mapping tidak valid: "
                            + String.join(" | ", validation.errors())
            );
        }

        // Export custom mappings directly to Geyser.
        ItemsAdderMappingExporter.ExportResult exportResult =
                exporter.export(
                        mappingResult,
                        geyserDataDirectory
                );

        if (exportResult == null || !exportResult.isSuccessful()) {
            throw new IOException(
                    "Gagal mengekspor custom mappings ke Geyser."
            );
        }

        File mappingFile = exportResult.mappingFile();

        if (mappingFile == null || !mappingFile.isFile()) {
            throw new IOException(
                    "File custom mappings tidak ditemukan setelah export."
            );
        }

        // Build the Bedrock resource pack inside Geyser/packs.
        ItemsAdderBedrockResourcePackBuilder.PackResult packResult =
                packBuilder.build(
                        itemsAdderContents,
                        geyserDataDirectory,
                        parseResult,
                        mappingResult
                );

        if (packResult == null || !packResult.isSuccessful()) {
            throw new IOException(
                    "Resource pack gagal dibuat. Mapping sudah diekspor ke: "
                            + mappingFile.getPath()
            );
        }

        List<String> warnings = new ArrayList<>(
                mappingResult.warnings()
        );

        warnings.addAll(validation.warnings());
        warnings.addAll(packResult.warnings());

        if (parseResult.hasErrors()) {
            warnings.add(
                    "Sebagian konfigurasi ItemsAdder mengalami kesalahan."
            );
        }

        return new ConversionResult(
                true,
                parseResult.getFilesRead(),
                parseResult.getAssets().size(),
                packResult.discoveredItems(),
                packResult.copiedTextures(),
                packResult.skippedItems(),
                warnings,
                packResult.skippedDetails(),
                packResult.packFile(),
                mappingFile
        );
    }

    public record ConversionResult(
            boolean successful,
            int filesRead,
            int totalAssets,
            int discoveredItems,
            int convertedTextures,
            int skippedItems,
            List<String> warnings,
            List<String> skippedDetails,
            File packFile,
            File mappingFile
    ) {

        public boolean isSuccessful() {
            return successful
                    && packFile != null
                    && packFile.isFile()
                    && mappingFile != null
                    && mappingFile.isFile();
        }
    }
}
