
package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.scanner.ItemsAdderAssetResolver;
import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ItemsAdderBedrockResourcePackBuilder {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private final ItemsAdderAssetResolver assetResolver =
            new ItemsAdderAssetResolver();

    public PackResult build(
            File itemsAdderContents,
            File geyserDataDirectory,
            ItemsAdderConfigParser.ParseResult parseResult,
            ItemsAdderCustomMappingGenerator.MappingResult mappingResult
    ) throws IOException {

        if (itemsAdderContents == null
                || !itemsAdderContents.isDirectory()) {
            throw new IOException("ItemsAdder contents tidak ditemukan.");
        }

        if (geyserDataDirectory == null) {
            throw new IOException("Folder data Geyser tidak ditemukan.");
        }

        if (parseResult == null || mappingResult == null
                || mappingResult.mapping() == null) {
            throw new IOException("Data parser atau mapping tidak tersedia.");
        }

        File packsDirectory = new File(geyserDataDirectory, "packs");
        Files.createDirectories(packsDirectory.toPath());

        if (!packsDirectory.isDirectory()) {
            throw new IOException("Folder Geyser packs tidak tersedia.");
        }

        ItemsAdderAssetResolver.ResolutionResult resolution =
                assetResolver.resolve(parseResult, itemsAdderContents);

        File workingDirectory = new File(
                geyserDataDirectory,
                ".vortexconverter-bedrock-work"
        );

        if (workingDirectory.exists()) {
            deleteDirectory(workingDirectory.toPath());
        }

        File texturesDirectory = new File(
                workingDirectory,
                "textures/items"
        );

        Files.createDirectories(texturesDirectory.toPath());

        List<String> warnings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        JsonObject items = mappingResult.mapping()
                .getAsJsonObject("items");

        if (items == null) {
            throw new IOException("Mapping tidak memiliki objek items.");
        }

        JsonObject textureData = new JsonObject();

        Map<String, String> generatedIdentifiers =
                new LinkedHashMap<>();

        int discoveredItems = 0;
        int copiedTextures = 0;

        for (ItemsAdderConfigParser.AssetInfo asset
                : parseResult.getAssets()) {

            if (asset.getType()
                    != ItemsAdderConfigParser.AssetType.ITEM) {
                continue;
            }

            discoveredItems++;

            String javaIdentifier = asset.getJavaIdentifier();

            if (javaIdentifier == null || !items.has(javaIdentifier)) {
                skipped.add(asset.getNamespacedId()
                        + ": mapping Java tidak ditemukan.");
                continue;
            }

            JsonArray definitions;

            try {
                definitions = items.getAsJsonArray(javaIdentifier);
            } catch (Exception exception) {
                skipped.add(asset.getNamespacedId()
                        + ": format mapping tidak valid.");
                continue;
            }

            if (definitions == null || definitions.isEmpty()) {
                skipped.add(asset.getNamespacedId()
                        + ": definisi mapping kosong.");
                continue;
            }

            Object assetCmdValue = asset.getCustomModelDataValue();

            if (assetCmdValue == null) {
                skipped.add(asset.getNamespacedId()
                        + ": Custom Model Data tidak ditemukan.");
                continue;
            }

            JsonObject definition = findMatchingDefinition(
                    definitions,
                    assetCmdValue
            );

            if (definition == null) {
                skipped.add(asset.getNamespacedId()
                        + ": mapping dengan CMD "
                        + assetCmdValue
                        + " tidak ditemukan untuk "
                        + javaIdentifier + ".");
                continue;
            }

            if (!definition.has("bedrock_identifier")
                    || definition.get("bedrock_identifier").isJsonNull()) {
                skipped.add(asset.getNamespacedId()
                        + ": Bedrock identifier tidak ditemukan.");
                continue;
            }

            String bedrockIdentifier = definition
                    .get("bedrock_identifier")
                    .getAsString();

            if (generatedIdentifiers.containsKey(bedrockIdentifier)) {
                skipped.add(asset.getNamespacedId()
                        + ": Bedrock identifier duplikat dengan "
                        + generatedIdentifiers.get(bedrockIdentifier));
                continue;
            }

            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset =
                    findResolvedAsset(resolution, asset);

            if (resolvedAsset == null) {
                skipped.add(asset.getNamespacedId()
                        + ": aset tidak ditemukan dalam hasil resolver.");
                continue;
            }

            File sourceTexture = findResolvedTexture(resolvedAsset);

            if (sourceTexture == null) {
                String missing = resolvedAsset.getMissingReferences()
                        .isEmpty()
                        ? "tidak ada file PNG yang cocok"
                        : String.join(
                                ", ",
                                resolvedAsset.getMissingReferences()
                        );

                skipped.add(asset.getNamespacedId()
                        + ": tekstur tidak ditemukan: " + missing);
                continue;
            }

            String atlasKey = toAtlasKey(bedrockIdentifier);

            if (textureData.has(atlasKey)) {
                skipped.add(asset.getNamespacedId()
                        + ": key tekstur atlas duplikat: " + atlasKey);
                continue;
            }

            File destination = new File(
                    texturesDirectory,
                    atlasKey + ".png"
            );

            Files.copy(
                    sourceTexture.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            JsonObject textureEntry = new JsonObject();
            JsonArray texturePaths = new JsonArray();
            texturePaths.add("textures/items/" + atlasKey);

            textureEntry.add("textures", texturePaths);

            // Geyser mencari icon shorthand, bukan identifier mentah.
            textureData.add(atlasKey, textureEntry);

            generatedIdentifiers.put(
                    bedrockIdentifier,
                    asset.getNamespacedId()
            );

            copiedTextures++;
        }

        if (copiedTextures == 0) {
            throw new IOException(
                    "Tidak ada tekstur item yang berhasil dikonversi. "
                            + "Resolver memindai "
                            + resolution.getScannedFiles()
                            + " file, tetapi tidak menemukan tekstur PNG "
                            + "yang cocok dengan item dan mapping CMD."
            );
        }

        JsonObject textureAtlas = new JsonObject();
        textureAtlas.addProperty(
                "resource_pack_name",
                "VortexConverter"
        );
        textureAtlas.addProperty(
                "texture_name",
                "atlas.items"
        );
        textureAtlas.add(
                "texture_data",
                textureData
        );

        writeJson(
                new File(workingDirectory, "textures/item_texture.json"),
                textureAtlas
        );

        writeManifest(workingDirectory);

        File packFile = new File(
                packsDirectory,
                "VortexConverter-Bedrock.mcpack"
        );

        File temporaryPack = new File(
                packsDirectory,
                "VortexConverter-Bedrock.tmp"
        );

        Files.deleteIfExists(temporaryPack.toPath());

        createArchive(workingDirectory, temporaryPack);

        try {
            Files.move(
                    temporaryPack.toPath(),
                    packFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException exception) {
            Files.move(
                    temporaryPack.toPath(),
                    packFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        warnings.add(
                "Resolver memindai " + resolution.getScannedFiles()
                        + " file dari seluruh ItemsAdder contents."
        );

        warnings.add(
                "Resolver menemukan " + resolution.getResolvedFiles()
                        + " referensi file dan "
                        + resolution.getMissingReferences()
                        + " referensi yang tidak ditemukan."
        );

        warnings.add(
                "Key tekstur atlas menggunakan format icon shorthand "
                        + "yang sesuai dengan aturan identifier Geyser."
        );

        warnings.add(
                "Builder saat ini mengemas tekstur item yang memiliki "
                        + "mapping. Model 3D, block, furniture, entity, "
                        + "animasi, dan suara belum dikonversi."
        );

        return new PackResult(
                true,
                discoveredItems,
                copiedTextures,
                skipped.size(),
                skipped,
                warnings,
                workingDirectory,
                packFile
        );
    }

    private JsonObject findMatchingDefinition(
            JsonArray definitions,
            Object assetCmdValue
    ) {
        BigDecimal assetCmd = toBigDecimal(assetCmdValue);

        if (assetCmd == null) {
            return null;
        }

        for (JsonElement element : definitions) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject definition = element.getAsJsonObject();

            if (!definition.has("custom_model_data")
                    || definition.get("custom_model_data").isJsonNull()) {
                continue;
            }

            if (definition.has("type")
                    && !definition.get("type").getAsString()
                    .equalsIgnoreCase("legacy")) {
                continue;
            }

            BigDecimal mappingCmd = toBigDecimal(
                    definition.get("custom_model_data")
            );

            if (mappingCmd != null
                    && mappingCmd.compareTo(assetCmd) == 0) {
                return definition;
            }
        }

        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(JsonElement value) {
        if (value == null || value.isJsonNull()
                || !value.isJsonPrimitive()) {
            return null;
        }

        try {
            return new BigDecimal(value.getAsString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ItemsAdderAssetResolver.ResolvedAsset findResolvedAsset(
            ItemsAdderAssetResolver.ResolutionResult resolution,
            ItemsAdderConfigParser.AssetInfo asset
    ) {
        for (ItemsAdderAssetResolver.ResolvedAsset candidate
                : resolution.getAssets()) {

            ItemsAdderConfigParser.AssetInfo parsed =
                    candidate.getAsset();

            if (parsed == asset) {
                return candidate;
            }

            if (parsed.getNamespace().equalsIgnoreCase(asset.getNamespace())
                    && parsed.getId().equalsIgnoreCase(asset.getId())
                    && parsed.getType() == asset.getType()) {
                return candidate;
            }
        }

        return null;
    }

    private File findResolvedTexture(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset
    ) {
        for (List<File> files
                : resolvedAsset.getResolvedFiles().values()) {

            for (File file : files) {
                if (file == null || !file.isFile()) {
                    continue;
                }

                if (file.getName().toLowerCase(Locale.ROOT)
                        .endsWith(".png")) {
                    return file;
                }
            }
        }

        return null;
    }

    private String toAtlasKey(String identifier) {
        return identifier
                .replace(':', '.')
                .replace('/', '_')
                .replace('\\', '_')
                .replaceAll("[^a-zA-Z0-9_.-]", "_")
                .toLowerCase(Locale.ROOT);
    }

    private void writeManifest(File directory)
            throws IOException {

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);

        JsonObject header = new JsonObject();
        header.addProperty(
                "name",
                "VortexConverter Bedrock Resource Pack"
        );
        header.addProperty(
                "description",
                "Generated by VortexConverter"
        );
        header.addProperty(
                "uuid",
                UUID.nameUUIDFromBytes(
                        "VortexConverter-Bedrock-Header"
                                .getBytes(StandardCharsets.UTF_8)
                ).toString()
        );
        header.add("version", versionArray(1, 0, 0));
        header.add("min_engine_version", versionArray(1, 20, 0));

        JsonObject module = new JsonObject();
        module.addProperty("type", "resources");
        module.addProperty(
                "uuid",
                UUID.nameUUIDFromBytes(
                        "VortexConverter-Bedrock-Module"
                                .getBytes(StandardCharsets.UTF_8)
                ).toString()
        );
        module.add("version", versionArray(1, 0, 0));

        JsonArray modules = new JsonArray();
        modules.add(module);

        manifest.add("header", header);
        manifest.add("modules", modules);

        writeJson(
                new File(directory, "manifest.json"),
                manifest
        );
    }

    private JsonArray versionArray(
            int major,
            int minor,
            int patch
    ) {
        JsonArray array = new JsonArray();
        array.add(major);
        array.add(minor);
        array.add(patch);
        return array;
    }

    private void writeJson(File file, JsonObject json)
            throws IOException {

        File parent = file.getParentFile();

        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        Files.writeString(
                file.toPath(),
                GSON.toJson(json),
                StandardCharsets.UTF_8
        );
    }

    private void createArchive(File source, File destination)
            throws IOException {

        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(destination.toPath()))) {

            try (var stream = Files.walk(source.toPath())) {
                for (Path path : stream
                        .filter(Files::isRegularFile)
                        .toList()) {

                    String entryName = source.toPath()
                            .relativize(path)
                            .toString()
                            .replace('\\', '/');

                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private void deleteDirectory(Path directory)
            throws IOException {

        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            for (Path path : stream
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public record PackResult(
            boolean successful,
            int discoveredItems,
            int copiedTextures,
            int skippedItems,
            List<String> skippedDetails,
            List<String> warnings,
            File workingDirectory,
            File packFile
    ) {
        public boolean isSuccessful() {
            return successful
                    && packFile != null
                    && packFile.isFile();
        }
    }
}
