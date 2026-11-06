
package id.vortexconverter.converter;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.mappings.ItemsAdderMappingExporter;
import id.vortexconverter.mappings.ItemsAdderMappingValidator;
import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ItemsAdderBedrockConversionService {

    private static final String PACK_FILE_NAME =
            "VortexConverter-Bedrock.mcpack";

    private static final String MAPPING_FILE_NAME =
            "vortexconverter-itemsadder.json";

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

        Path geyserPath = geyserDataDirectory
                .toPath()
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
            // Simpan output lama sebelum memulai konversi.
            packBackup = createBackup(packTarget);
            mappingBackup = createBackup(mappingTarget);

            // Tahap 1: buat resource pack terlebih dahulu.
            ItemsAdderBedrockResourcePackBuilder.PackResult packResult =
                    packBuilder.build(
                            itemsAdderContents,
                            geyserDataDirectory,
                            parseResult,
                            mappingResult
                    );

            if (packResult == null || !packResult.isSuccessful()) {
                throw new IOException(
                        "Resource pack gagal dibuat."
                );
            }

            File packFile = packResult.packFile();

            if (packFile == null || !packFile.isFile()) {
                throw new IOException(
                        "File resource pack tidak ditemukan setelah build."
                );
            }

            // Tahap 2: ekspor mapping setelah pack berhasil dibuat.
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
                    "Resource pack berhasil dibuat sebelum mapping diekspor."
            );

            warnings.add(
                    "Konversi menghasilkan file pada folder Geyser packs "
                            + "dan custom_mappings."
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

            // Jika salah satu tahap gagal, pulihkan kedua output.
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
