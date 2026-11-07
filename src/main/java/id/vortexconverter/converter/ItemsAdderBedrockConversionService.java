
package id.vortexconverter.converter;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.mappings.ItemsAdderMappingExporter;
import id.vortexconverter.mappings.ItemsAdderMappingValidator;
import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemsAdderBedrockConversionService {

    private static final String PACK_FILE_NAME =
            "VortexConverter-Bedrock.mcpack";

    private static final String MAPPING_FILE_NAME =
            "vortexconverter-itemsadder.json";

    private static final String DIAGNOSTIC_FILE_NAME =
            "vortexconverter-itemsadder-diagnostic.txt";

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

        // Buat laporan sebelum proses mapping dan konversi dimulai.
        // Laporan tetap tersedia meskipun proses berikutnya gagal.
        Path diagnosticPath = geyserDataDirectory.toPath()
                .resolve(DIAGNOSTIC_FILE_NAME);

        writeDiagnosticReport(
                diagnosticPath,
                itemsAdderContents,
                parseResult
        );

        ItemsAdderCustomMappingGenerator.MappingResult mappingResult =
                mappingGenerator.generate(parseResult);

        if (mappingResult == null || !mappingResult.hasMappings()) {
            throw new IOException(
                    "Tidak ada custom item mapping yang dapat dibuat. "
                            + "Laporan diagnostik: " + diagnosticPath
            );
        }

        ItemsAdderMappingValidator.ValidationResult validation =
                validator.validate(mappingResult);

        if (!validation.isSuccessful()) {
            throw new IOException(
                    "Mapping tidak valid: "
                            + String.join(" | ", validation.errors())
                            + ". Laporan diagnostik: " + diagnosticPath
            );
        }

        Path geyserPath = geyserDataDirectory.toPath()
                .toAbsolutePath()
                .normalize();

        Path packsDirectory = geyserPath.resolve("packs");
        Path mappingsDirectory = geyserPath.resolve("custom_mappings");

        Files.createDirectories(packsDirectory);
        Files.createDirectories(mappingsDirectory);

        Path packTarget = packsDirectory.resolve(PACK_FILE_NAME);
        Path mappingTarget = mappingsDirectory.resolve(MAPPING_FILE_NAME);

        FileBackup packBackup = null;
        FileBackup mappingBackup = null;

        try {
            packBackup = createBackup(packTarget);
            mappingBackup = createBackup(mappingTarget);

            ItemsAdderBedrockResourcePackBuilder.PackResult packResult =
                    packBuilder.build(
                            itemsAdderContents,
                            geyserDataDirectory,
                            parseResult,
                            mappingResult
                    );

            if (packResult == null || !packResult.isSuccessful()) {
                throw new IOException(
                        "Resource pack gagal dibuat. "
                                + "Laporan diagnostik: " + diagnosticPath
                );
            }

            File packFile = packResult.packFile();

            if (packFile == null || !packFile.isFile()) {
                throw new IOException(
                        "File resource pack tidak ditemukan setelah build."
                );
            }

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

            warnings.add(
                    "Laporan diagnostik tersedia di: "
                            + diagnosticPath
            );

            return new ConversionResult(
                    true,
                    parseResult.getFilesRead(),
                    parseResult.getAssets().size(),
                    packResult.discoveredItems(),
                    packResult.copiedTextures(),
                    packResult.skippedItems(),
                    warnings,
                    packResult.skippedDetails(),
                    packFile,
                    mappingFile
            );

        } catch (IOException | RuntimeException exception) {

            IOException rollbackError = null;

            try {
                if (mappingBackup != null) {
                    restoreBackup(mappingBackup);
                }
            } catch (IOException restoreException) {
                rollbackError = restoreException;
            }

            try {
                if (packBackup != null) {
                    restoreBackup(packBackup);
                }
            } catch (IOException restoreException) {
                if (rollbackError == null) {
                    rollbackError = restoreException;
                } else {
                    rollbackError.addSuppressed(restoreException);
                }
            }

            if (rollbackError != null) {
                exception.addSuppressed(rollbackError);
            }

            throw exception;

        } finally {
            deleteBackup(packBackup);
            deleteBackup(mappingBackup);
        }
    }

    private void writeDiagnosticReport(
            Path output,
            File contentsFolder,
            ItemsAdderConfigParser.ParseResult result
    ) throws IOException {

        StringBuilder report = new StringBuilder();

        report.append("VORTEXCONVERTER ITEMADDER DIAGNOSTIC\n");
        report.append("====================================\n\n");

        report.append("Contents folder: ")
                .append(contentsFolder.getAbsolutePath())
                .append('\n');

        report.append("Files read: ")
                .append(result.getFilesRead())
                .append('\n');

        report.append("Namespaces read: ")
                .append(result.getNamespacesRead())
                .append('\n');

        report.append("Total assets: ")
                .append(result.getTotalAssets())
                .append('\n');

        report.append("Items: ")
                .append(result.count(
                        ItemsAdderConfigParser.AssetType.ITEM
                ))
                .append('\n');

        report.append("Blocks: ")
                .append(result.count(
                        ItemsAdderConfigParser.AssetType.BLOCK
                ))
                .append('\n');

        report.append("Furniture: ")
                .append(result.count(
                        ItemsAdderConfigParser.AssetType.FURNITURE
                ))
                .append('\n');

        report.append("Entities: ")
                .append(result.count(
                        ItemsAdderConfigParser.AssetType.ENTITY
                ))
                .append('\n');

        report.append("Items with material: ")
                .append(result.countWithMaterial())
                .append('\n');

        report.append("Items with texture property: ")
                .append(result.countWithTexture())
                .append('\n');

        report.append("Items with model property: ")
                .append(result.countWithModel())
                .append('\n');

        report.append("Items with Custom Model Data: ")
                .append(result.countCustomModelData())
                .append('\n');

        report.append("Items with explicit Bedrock identifier: ")
                .append(result.countWithBedrockIdentifier())
                .append('\n');

        report.append("\nCONFIGURATION ERRORS\n");
        report.append("--------------------\n");

        if (result.getErrors().isEmpty()) {
            report.append("No parser errors.\n");
        } else {
            for (String error : result.getErrors()) {
                report.append(error).append('\n');
            }
        }

        report.append("\nDISCOVERED ASSETS\n");
        report.append("-----------------\n");

        for (ItemsAdderConfigParser.AssetInfo asset : result.getAssets()) {
            report.append("\n[")
                    .append(asset.getType())
                    .append("] ")
                    .append(asset.getNamespacedId())
                    .append('\n');

            report.append("Source: ")
                    .append(asset.getSourceFile())
                    .append('\n');

            report.append("Material: ")
                    .append(valueOrMissing(asset.getMaterial()))
                    .append('\n');

            report.append("Java identifier: ")
                    .append(valueOrMissing(asset.getJavaIdentifier()))
                    .append('\n');

            report.append("Custom Model Data: ")
                    .append(valueOrMissing(
                            asset.getCustomModelDataValue()
                    ))
                    .append('\n');

            report.append("Texture path: ")
                    .append(valueOrMissing(asset.getTexturePath()))
                    .append('\n');

            report.append("Model path: ")
                    .append(valueOrMissing(asset.getModelPath()))
                    .append('\n');

            report.append("Properties:\n");

            if (asset.getProperties().isEmpty()) {
                report.append("  (none)\n");
            } else {
                for (Map.Entry<String, String> property
                        : asset.getProperties().entrySet()) {

                    report.append("  ")
                            .append(property.getKey())
                            .append(" = ")
                            .append(property.getValue())
                            .append('\n');
                }
            }
        }

        Files.writeString(
                output,
                report.toString(),
                StandardCharsets.UTF_8
        );
    }

    private String valueOrMissing(String value) {
        return value == null || value.isBlank()
                ? "(not found)"
                : value;
    }

    private FileBackup createBackup(Path target)
            throws IOException {

        boolean existed = Files.isRegularFile(target);

        if (!existed) {
            return new FileBackup(target, null, false);
        }

        Path backup = Files.createTempFile(
                target.getParent(),
                "vortexconverter-backup-",
                ".tmp"
        );

        try {
            Files.copy(
                    target,
                    backup,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return new FileBackup(target, backup, true);

        } catch (IOException exception) {
            Files.deleteIfExists(backup);
            throw exception;
        }
    }

    private void restoreBackup(FileBackup backup)
            throws IOException {

        if (backup == null) {
            return;
        }

        if (backup.existed()) {
            if (backup.backup() == null
                    || !Files.isRegularFile(backup.backup())) {
                throw new IOException(
                        "File backup tidak ditemukan untuk "
                                + backup.target()
                );
            }

            Files.copy(
                    backup.backup(),
                    backup.target(),
                    StandardCopyOption.REPLACE_EXISTING
            );

        } else {
            Files.deleteIfExists(backup.target());
        }
    }

    private void deleteBackup(FileBackup backup) {
        if (backup == null || backup.backup() == null) {
            return;
        }

        try {
            Files.deleteIfExists(backup.backup());
        } catch (IOException ignored) {
            // Backup sementara akan dibersihkan pada proses berikutnya.
        }
    }

    private record FileBackup(
            Path target,
            Path backup,
            boolean existed
    ) {
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
