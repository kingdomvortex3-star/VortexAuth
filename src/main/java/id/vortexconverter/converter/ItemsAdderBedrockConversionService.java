
package id.vortexconverter.converter;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ItemsAdderBedrockConversionService {

    private final ItemsAdderConfigParser parser;
    private final ItemsAdderCustomMappingGenerator mappingGenerator;
    private final ItemsAdderBedrockResourcePackBuilder packBuilder;

    public ItemsAdderBedrockConversionService() {
        this.parser = new ItemsAdderConfigParser();
        this.mappingGenerator = new ItemsAdderCustomMappingGenerator();
        this.packBuilder = new ItemsAdderBedrockResourcePackBuilder();
    }

    public ConversionResult convert(
            File itemsAdderContents,
            File outputDirectory
    ) throws IOException {

        if (itemsAdderContents == null
                || !itemsAdderContents.exists()
                || !itemsAdderContents.isDirectory()) {
            throw new IOException(
                    "Folder ItemsAdder contents tidak ditemukan."
            );
        }

        if (outputDirectory == null) {
            throw new IOException(
                    "Folder output tidak ditentukan."
            );
        }

        ItemsAdderConfigParser.ParseResult parseResult =
                parser.parse(itemsAdderContents);

        if (parseResult == null) {
            throw new IOException(
                    "ItemsAdder parser tidak menghasilkan data."
            );
        }

        if (parseResult.getFilesRead() == 0) {
            throw new IOException(
                    "Tidak ada konfigurasi ItemsAdder yang berhasil dibaca."
            );
        }

        ItemsAdderCustomMappingGenerator.MappingResult mappingResult =
                mappingGenerator.generate(parseResult);

        if (mappingResult == null || !mappingResult.hasMappings()) {
            throw new IOException(
                    "Tidak ada mapping custom item yang dapat diproses."
            );
        }

        ItemsAdderBedrockResourcePackBuilder.PackResult packResult =
                packBuilder.build(
                        itemsAdderContents,
                        outputDirectory,
                        parseResult,
                        mappingResult
                );

        if (packResult == null || !packResult.isSuccessful()) {
            throw new IOException(
                    "Resource pack Bedrock gagal dibuat."
            );
        }

        List<String> warnings = new ArrayList<>(
                packResult.warnings()
        );

        if (parseResult.hasErrors()) {
            warnings.add(
                    "Parser menemukan kesalahan pada sebagian konfigurasi."
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
                packResult.packFile()
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
            File packFile
    ) {

        public boolean isSuccessful() {
            return successful
                    && packFile != null
                    && packFile.isFile();
        }
    }
}
