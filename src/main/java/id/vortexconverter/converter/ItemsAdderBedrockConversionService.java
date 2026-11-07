
package id.vortexconverter.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.mappings.ItemsAdderMappingExporter;
import id.vortexconverter.mappings.ItemsAdderMappingValidator;
import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ItemsAdderBedrockConversionService {

    private static final String PACK_FILE_NAME =
            "VortexConverter-Bedrock.mcpack";

    private static final String MAPPING_FILE_NAME =
            "vortexconverter-itemsadder.json";

    private static final String ATLAS_ENTRY =
            "textures/item_texture.json";

    private static final String MANIFEST_ENTRY =
            "manifest.json";

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

        File itemsAdderDataDirectory =
                itemsAdderContents.getParentFile();

        if (itemsAdderDataDirectory == null) {
            throw new IOException(
                    "Folder data ItemsAdder tidak valid."
            );
        }

        File generatedZip = new File(
                itemsAdderDataDirectory,
                "output/generated.zip"
        );

        if (!generatedZip.isFile()) {
            throw new IOException(
                    "ItemsAdder generated.zip tidak ditemukan: "
                            + generatedZip.getPath()
                            + ". Jalankan /iazip terlebih dahulu."
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

        Files.createDirectories(geyserPath);

        Path packsDirectory = geyserPath.resolve("packs");
        Path mappingsDirectory = geyserPath.resolve("custom_mappings");

        Files.createDirectories(packsDirectory);
        Files.createDirectories(mappingsDirectory);

        Path packTarget = packsDirectory.resolve(PACK_FILE_NAME);
        Path mappingTarget = mappingsDirectory.resolve(MAPPING_FILE_NAME);

        FileBackup packBackup = null;
        FileBackup mappingBackup = null;

        boolean rollbackFailed = false;

        try {
            // Backup dibuat sebelum file lama disentuh.
            packBackup = createBackup(packTarget);
            mappingBackup = createBackup(mappingTarget);

            ItemsAdderBedrockResourcePackBuilder.PackResult packResult =
                    packBuilder.build(
                            itemsAdderContents,
                            generatedZip,
                            geyserDataDirectory,
                            parseResult,
                            mappingResult
                    );

            if (packResult == null || !packResult.isSuccessful()) {
                throw new IOException(
                        "Resource pack gagal dibuat dari generated.zip."
                );
            }

            File packFile = packResult.packFile();

            if (packFile == null || !packFile.isFile()) {
                throw new IOException(
                        "File resource pack tidak ditemukan setelah build."
                );
            }

            // Pastikan pack benar-benar ZIP valid dan atlas tersedia.
            validateResourcePack(packFile, mappingResult.mapping());

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

            // Pastikan file hasil ekspor masih valid.
            validateExportedMapping(mappingFile);

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
                    "Sumber metadata: ItemsAdder/contents."
            );

            warnings.add(
                    "Sumber model dan tekstur: "
                            + generatedZip.getPath()
            );

            warnings.add(
                    "Resource pack telah diperiksa sebagai ZIP valid."
            );

            warnings.add(
                    "Semua icon mapping telah diperiksa terhadap texture atlas."
            );

            warnings.add(
                    "Mapping berhasil diekspor setelah pemeriksaan resource pack."
            );

            warnings.add(
                    "File output berada di Geyser packs dan custom_mappings."
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
                rollbackFailed = true;
                exception.addSuppressed(rollbackError);

                exception.addSuppressed(
                        new IOException(
                                "Backup dipertahankan untuk pemulihan manual. "
                                        + "Mapping backup: "
                                        + backupPath(mappingBackup)
                                        + "; pack backup: "
                                        + backupPath(packBackup)
                        )
                );
            }

            throw exception;

        } finally {
            // Jangan hapus backup jika rollback gagal.
            if (!rollbackFailed) {
                deleteBackup(packBackup);
                deleteBackup(mappingBackup);
            }
        }
    }

    private void validateResourcePack(
            File packFile,
            JsonObject mapping
    ) throws IOException {

        if (packFile == null || !packFile.isFile()) {
            throw new IOException(
                    "Resource pack tidak ditemukan."
            );
        }

        try (ZipFile zip = new ZipFile(packFile)) {

            ZipEntry manifestEntry = zip.getEntry(MANIFEST_ENTRY);
            ZipEntry atlasEntry = zip.getEntry(ATLAS_ENTRY);

            if (manifestEntry == null) {
                throw new IOException(
                        "Resource pack tidak memiliki manifest.json."
                );
            }

            if (atlasEntry == null) {
                throw new IOException(
                        "Resource pack tidak memiliki "
                                + ATLAS_ENTRY
                );
            }

            JsonObject manifest;

            try (InputStream input = zip.getInputStream(manifestEntry);
                 InputStreamReader reader = new InputStreamReader(
                         input,
                         StandardCharsets.UTF_8
                 )) {

                JsonElement element = JsonParser.parseReader(reader);

                if (!element.isJsonObject()) {
                    throw new IOException(
                            "manifest.json bukan JSON object."
                    );
                }

                manifest = element.getAsJsonObject();
            }

            if (!manifest.has("header")
                    || !manifest.get("header").isJsonObject()
                    || !manifest.has("modules")
                    || !manifest.get("modules").isJsonArray()) {
                throw new IOException(
                        "Struktur manifest.json tidak lengkap."
                );
            }

            JsonObject atlas;

            try (InputStream input = zip.getInputStream(atlasEntry);
                 InputStreamReader reader = new InputStreamReader(
                         input,
                         StandardCharsets.UTF_8
                 )) {

                JsonElement element = JsonParser.parseReader(reader);

                if (!element.isJsonObject()) {
                    throw new IOException(
                            "item_texture.json bukan JSON object."
                    );
                }

                atlas = element.getAsJsonObject();
            }

            if (!atlas.has("texture_data")
                    || !atlas.get("texture_data").isJsonObject()) {
                throw new IOException(
                        "item_texture.json tidak memiliki texture_data."
                );
            }

            JsonObject textureData =
                    atlas.getAsJsonObject("texture_data");

            validateMappingIcons(mapping, textureData);
        }
    }

    private void validateMappingIcons(
            JsonObject mapping,
            JsonObject textureData
    ) throws IOException {

        if (mapping == null
                || !mapping.has("items")
                || !mapping.get("items").isJsonObject()) {
            throw new IOException(
                    "Mapping tidak memiliki objek items."
            );
        }

        JsonObject items = mapping.getAsJsonObject("items");

        int checked = 0;

        for (Map.Entry<String, JsonElement> itemEntry
                : items.entrySet()) {

            if (!itemEntry.getValue().isJsonArray()) {
                throw new IOException(
                        "Mapping definitions bukan array: "
                                + itemEntry.getKey()
                );
            }

            for (JsonElement definitionElement
                    : itemEntry.getValue().getAsJsonArray()) {

                if (!definitionElement.isJsonObject()) {
                    throw new IOException(
                            "Mapping definition bukan object: "
                                    + itemEntry.getKey()
                    );
                }

                JsonObject definition =
                        definitionElement.getAsJsonObject();

                if (!definition.has("bedrock_options")
                        || !definition.get("bedrock_options").isJsonObject()) {
                    throw new IOException(
                            "bedrock_options tidak ditemukan: "
                                    + itemEntry.getKey()
                    );
                }

                JsonObject options =
                        definition.getAsJsonObject("bedrock_options");

                if (!options.has("icon")
                        || !options.get("icon").isJsonPrimitive()
                        || !options.get("icon").getAsJsonPrimitive().isString()) {
                    throw new IOException(
                            "Icon mapping tidak valid: "
                                    + itemEntry.getKey()
                    );
                }

                String icon = options.get("icon").getAsString();

                if (!textureData.has(icon)) {
                    throw new IOException(
                            "Tekstur atlas untuk icon '"
                                    + icon
                                    + "' tidak ditemukan. Java item: "
                                    + itemEntry.getKey()
                    );
                }

                checked++;
            }
        }

        if (checked == 0) {
            throw new IOException(
                    "Tidak ada icon mapping yang berhasil diperiksa."
            );
        }
    }

    private void validateExportedMapping(File mappingFile)
            throws IOException {

        JsonElement element = JsonParser.parseString(
                Files.readString(
                        mappingFile.toPath(),
                        StandardCharsets.UTF_8
                )
        );

        if (!element.isJsonObject()) {
            throw new IOException(
                    "File hasil ekspor mapping bukan JSON object."
            );
        }

        JsonObject mapping = element.getAsJsonObject();

        ItemsAdderMappingValidator.ValidationResult result =
                validator.validate(mapping);

        if (!result.isSuccessful()) {
            throw new IOException(
                    "Mapping hasil ekspor tidak valid: "
                            + String.join(" | ", result.errors())
            );
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

    private String backupPath(FileBackup backup) {
        if (backup == null || backup.backup() == null) {
            return "tidak tersedia";
        }

        return backup.backup().toString();
    }

    private void deleteBackup(FileBackup backup) {
        if (backup == null || backup.backup() == null) {
            return;
        }

        try {
            Files.deleteIfExists(backup.backup());
        } catch (IOException ignored) {
            // Backup sementara dapat dibersihkan secara manual.
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
