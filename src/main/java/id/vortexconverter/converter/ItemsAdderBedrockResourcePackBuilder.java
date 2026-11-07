
package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.scanner.ItemsAdderAssetResolver;
import id.vortexconverter.scanner.ItemsAdderConfigParser;
import id.vortexconverter.scanner.ItemsAdderZipExtractor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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

    private final ItemsAdderZipExtractor zipExtractor =
            new ItemsAdderZipExtractor();

    private final ItemsAdderModelParser modelParser =
            new ItemsAdderModelParser();

    public PackResult build(
            File itemsAdderContents,
            File geyserDataDirectory,
            ItemsAdderConfigParser.ParseResult parseResult,
            ItemsAdderCustomMappingGenerator.MappingResult mappingResult
    ) throws IOException {

        return buildFromSource(
                itemsAdderContents,
                null,
                geyserDataDirectory,
                parseResult,
                mappingResult
        );
    }

    public PackResult build(
            File itemsAdderContents,
            File generatedZip,
            File geyserDataDirectory,
            ItemsAdderConfigParser.ParseResult parseResult,
            ItemsAdderCustomMappingGenerator.MappingResult mappingResult
    ) throws IOException {

        if (generatedZip == null || !generatedZip.isFile()) {
            throw new IOException(
                    "ItemsAdder output/generated.zip tidak ditemukan."
            );
        }

        return buildFromSource(
                itemsAdderContents,
                generatedZip,
                geyserDataDirectory,
                parseResult,
                mappingResult
        );
    }

    private PackResult buildFromSource(
            File itemsAdderContents,
            File generatedZip,
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
            throw new IOException(
                    "Data parser atau mapping tidak tersedia."
            );
        }

        File packsDirectory = new File(geyserDataDirectory, "packs");
        Files.createDirectories(packsDirectory.toPath());

        if (!packsDirectory.isDirectory()) {
            throw new IOException("Folder Geyser packs tidak tersedia.");
        }

        File workingDirectory = new File(
                geyserDataDirectory,
                ".vortexconverter-bedrock-work"
        );

        Path workingPath = workingDirectory.toPath();

        if (Files.exists(workingPath)) {
            deleteDirectory(workingPath);
        }

        Files.createDirectories(workingPath);

        boolean zipMode = generatedZip != null;
        File sourceDirectory;

        if (zipMode) {
            sourceDirectory = zipExtractor.extract(
                    generatedZip,
                    workingPath.resolve("source")
            ).toFile();
        } else {
            sourceDirectory = itemsAdderContents;
        }

        File packRoot = new File(workingDirectory, "bedrock_pack");
        File texturesDirectory = new File(packRoot, "textures/items");

        Files.createDirectories(texturesDirectory.toPath());

        ItemsAdderAssetResolver.ResolutionResult resolution =
                assetResolver.resolve(parseResult, sourceDirectory);

        List<String> warnings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        JsonObject items =
                mappingResult.mapping().getAsJsonObject("items");

        if (items == null) {
            throw new IOException("Mapping tidak memiliki objek items.");
        }

        JsonObject textureData = new JsonObject();
        JsonObject filteredItems = new JsonObject();

        Map<String, String> generatedIdentifiers =
                new LinkedHashMap<>();

        int discoveredItems = 0;
        int copiedTextures = 0;
        int uvIconsGenerated = 0;

        for (ItemsAdderConfigParser.AssetInfo asset
                : parseResult.getAssets()) {

            if (asset.getType()
                    != ItemsAdderConfigParser.AssetType.ITEM) {
                continue;
            }

            discoveredItems++;

            String javaIdentifier = asset.getJavaIdentifier();

            if (javaIdentifier == null || !items.has(javaIdentifier)) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": mapping Java tidak ditemukan."
                );
                continue;
            }

            JsonArray definitions;

            try {
                definitions = items.getAsJsonArray(javaIdentifier);
            } catch (Exception exception) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": format mapping tidak valid."
                );
                continue;
            }

            if (definitions == null || definitions.isEmpty()) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": definisi mapping kosong."
                );
                continue;
            }

            String assetCmdValue = asset.getCustomModelDataValue();

            if (assetCmdValue == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": Custom Model Data tidak ditemukan."
                );
                continue;
            }

            JsonObject definition = findMatchingDefinition(
                    definitions,
                    assetCmdValue
            );

            if (definition == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": mapping dengan CMD "
                                + assetCmdValue
                                + " tidak ditemukan untuk "
                                + javaIdentifier + "."
                );
                continue;
            }

            if (!definition.has("bedrock_identifier")
                    || definition.get("bedrock_identifier").isJsonNull()) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": Bedrock identifier tidak ditemukan."
                );
                continue;
            }

            String bedrockIdentifier = definition
                    .get("bedrock_identifier")
                    .getAsString();

            if (generatedIdentifiers.containsKey(bedrockIdentifier)) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": Bedrock identifier duplikat dengan "
                                + generatedIdentifiers.get(bedrockIdentifier)
                );
                continue;
            }

            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset =
                    findResolvedAsset(resolution, asset);

            if (resolvedAsset == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": aset tidak ditemukan dalam hasil resolver."
                );
                continue;
            }

            File sourceTexture = findResolvedTexture(resolvedAsset);

            if (sourceTexture == null) {
                String missing = resolvedAsset.getMissingReferences()
                        .isEmpty()
                        ? "referensi tekstur tidak ditemukan dalam ZIP"
                        : String.join(
                                ", ",
                                resolvedAsset.getMissingReferences()
                        );

                skipped.add(
                        asset.getNamespacedId()
                                + ": tekstur tidak ditemukan: "
                                + missing
                );
                continue;
            }

            String atlasKey = toAtlasKey(bedrockIdentifier);

            if (textureData.has(atlasKey)) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": key tekstur atlas duplikat: "
                                + atlasKey
                );
                continue;
            }

            File destination = new File(
                    texturesDirectory,
                    atlasKey + ".png"
            );

            boolean uvGenerated = false;

            try {
                uvGenerated = createUvIcon(
                        resolvedAsset,
                        destination,
                        warnings,
                        asset.getNamespacedId()
                );
            } catch (Exception exception) {
                warnings.add(
                        asset.getNamespacedId()
                                + ": ekstraksi UV gagal: "
                                + exception.getMessage()
                );
            }

            if (!uvGenerated) {
                Files.copy(
                        sourceTexture.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            } else {
                uvIconsGenerated++;
            }

            JsonObject textureEntry = new JsonObject();
            JsonArray texturePaths = new JsonArray();

            texturePaths.add("textures/items/" + atlasKey);
            textureEntry.add("textures", texturePaths);

            textureData.add(atlasKey, textureEntry);

            generatedIdentifiers.put(
                    bedrockIdentifier,
                    asset.getNamespacedId()
            );

            JsonArray retainedDefinitions;

            if (filteredItems.has(javaIdentifier)) {
                retainedDefinitions =
                        filteredItems.getAsJsonArray(javaIdentifier);
            } else {
                retainedDefinitions = new JsonArray();
                filteredItems.add(
                        javaIdentifier,
                        retainedDefinitions
                );
            }

            retainedDefinitions.add(definition.deepCopy());

            copiedTextures++;
        }

        if (copiedTextures == 0) {
            throw new IOException(
                    "Tidak ada tekstur item yang berhasil dikonversi. "
                            + "Sumber: "
                            + (zipMode
                            ? generatedZip.getName()
                            : "ItemsAdder contents (mode lama)")
                            + ". Resolver memindai "
                            + resolution.getScannedFiles()
                            + " file."
            );
        }

        mappingResult.mapping().add("items", filteredItems);

        JsonObject textureAtlas = new JsonObject();

        textureAtlas.addProperty(
                "resource_pack_name",
                "VortexConverter"
        );

        textureAtlas.addProperty(
                "texture_name",
                "atlas.items"
        );

        textureAtlas.add("texture_data", textureData);

        writeJson(
                new File(packRoot, "textures/item_texture.json"),
                textureAtlas
        );

        writeManifest(packRoot);

        File packFile = new File(
                packsDirectory,
                "VortexConverter-Bedrock.mcpack"
        );

        File temporaryPack = new File(
                packsDirectory,
                "VortexConverter-Bedrock.tmp"
        );

        Files.deleteIfExists(temporaryPack.toPath());

        createArchive(packRoot, temporaryPack);

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
                "Sumber aset: "
                        + (zipMode
                        ? generatedZip.getAbsolutePath()
                        : itemsAdderContents.getAbsolutePath())
        );

        warnings.add(
                "Resolver memindai "
                        + resolution.getScannedFiles()
                        + " file."
        );

        warnings.add(
                "Resolver menemukan "
                        + resolution.getResolvedFiles()
                        + " referensi file dan "
                        + resolution.getMissingReferences()
                        + " referensi yang tidak ditemukan."
        );

        warnings.add(
                "Icon berhasil dibuat: " + copiedTextures
                        + ", hasil ekstraksi UV: " + uvIconsGenerated
                        + ", fallback tekstur: "
                        + (copiedTextures - uvIconsGenerated) + "."
        );

        warnings.add(
                "Mapping telah disinkronkan dengan tekstur yang berhasil dibuat."
        );

        warnings.add(
                "Ekstraksi UV menggunakan face dengan area UV terbesar. "
                        + "Ini bukan rendering model 3D lengkap."
        );

        warnings.add(
                "Model 3D Bedrock, block, furniture, entity, animasi, "
                        + "dan suara belum dikonversi secara penuh."
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

    private boolean createUvIcon(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset,
            File destination,
            List<String> warnings,
            String assetName
    ) throws IOException {

        File modelFile = findResolvedModel(resolvedAsset);

        if (modelFile == null) {
            return false;
        }

        ItemsAdderModelParser.ModelData model =
                modelParser.parse(modelFile);

        if (!model.hasElements()) {
            return false;
        }

        ItemsAdderModelParser.FaceData selectedFace = null;
        double largestArea = -1.0;

        for (ItemsAdderModelParser.FaceData face : model.faces()) {
            if (!face.hasExplicitUv()) {
                continue;
            }

            double width = Math.abs(face.getU2() - face.getU1());
            double height = Math.abs(face.getV2() - face.getV1());
            double area = width * height;

            if (area > largestArea) {
                largestArea = area;
                selectedFace = face;
            }
        }

        if (selectedFace == null || largestArea <= 0) {
            warnings.add(
                    assetName
                            + ": model tidak memiliki UV eksplisit yang dapat dipotong."
            );
            return false;
        }

        File textureFile = findFaceTexture(
                resolvedAsset,
                model,
                selectedFace
        );

        if (textureFile == null) {
            warnings.add(
                    assetName
                            + ": tekstur face UV tidak ditemukan."
            );
            return false;
        }

        BufferedImage source = ImageIO.read(textureFile);

        if (source == null) {
            warnings.add(
                    assetName
                            + ": file tekstur bukan PNG yang dapat dibaca."
            );
            return false;
        }

        BufferedImage cropped = cropUv(
                source,
                selectedFace
        );

        if (cropped == null) {
            warnings.add(
                    assetName
                            + ": koordinat UV berada di luar tekstur."
            );
            return false;
        }

        BufferedImage rotated = rotateTexture(
                cropped,
                selectedFace.rotation()
        );

        File parent = destination.getParentFile();

        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        if (!ImageIO.write(rotated, "png", destination)) {
            throw new IOException("Tidak ada PNG image writer.");
        }

        return true;
    }

    private File findResolvedModel(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset
    ) {
        for (Map.Entry<String, List<File>> entry
                : resolvedAsset.getResolvedFiles().entrySet()) {

            String key = entry.getKey().toLowerCase(Locale.ROOT);

            if (key.startsWith("textures:model_texture:")) {
                continue;
            }

            String property = getPropertyName(key);

            if (!isModelProperty(property)) {
                continue;
            }

            for (File file : entry.getValue()) {
                if (file != null
                        && file.isFile()
                        && file.getName().toLowerCase(Locale.ROOT)
                        .endsWith(".json")) {
                    return file;
                }
            }
        }

        return null;
    }

    private boolean isModelProperty(String property) {
        return property.equals("model")
                || property.endsWith(".model")
                || property.contains("model_path")
                || property.contains("model_file")
                || property.contains("item_model");
    }

    private File findFaceTexture(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset,
            ItemsAdderModelParser.ModelData model,
            ItemsAdderModelParser.FaceData face
    ) {
        String resolvedReference = modelParser.resolveTexture(
                face.texture(),
                model.textures()
        );

        String textureKey = face.texture();

        if (textureKey.startsWith("#")) {
            textureKey = textureKey.substring(1);
        }

        Map<String, List<File>> files =
                resolvedAsset.getResolvedFiles();

        for (Map.Entry<String, List<File>> entry : files.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);

            if (!key.startsWith("textures:model_texture:")) {
                continue;
            }

            boolean keyMatches = key.contains(
                    ":" + textureKey.toLowerCase(Locale.ROOT) + ":"
            );

            boolean referenceMatches = resolvedReference != null
                    && key.endsWith(
                            ":" + resolvedReference.toLowerCase(Locale.ROOT)
                    );

            if (!keyMatches && !referenceMatches) {
                continue;
            }

            File png = firstPng(entry.getValue());

            if (png != null) {
                return png;
            }
        }

        if (resolvedReference != null) {
            String normalizedReference =
                    resolvedReference.toLowerCase(Locale.ROOT);

            for (Map.Entry<String, List<File>> entry : files.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);

                if (!key.startsWith("textures:model_texture:")
                        || !key.endsWith(":" + normalizedReference)) {
                    continue;
                }

                File png = firstPng(entry.getValue());

                if (png != null) {
                    return png;
                }
            }
        }

        return null;
    }

    private BufferedImage cropUv(
            BufferedImage source,
            ItemsAdderModelParser.FaceData face
    ) {
        double minU = Math.min(face.getU1(), face.getU2());
        double maxU = Math.max(face.getU1(), face.getU2());
        double minV = Math.min(face.getV1(), face.getV2());
        double maxV = Math.max(face.getV1(), face.getV2());

        int x1 = (int) Math.floor(
                minU / 16.0 * source.getWidth()
        );

        int y1 = (int) Math.floor(
                minV / 16.0 * source.getHeight()
        );

        int x2 = (int) Math.ceil(
                maxU / 16.0 * source.getWidth()
        );

        int y2 = (int) Math.ceil(
                maxV / 16.0 * source.getHeight()
        );

        x1 = Math.max(0, Math.min(source.getWidth() - 1, x1));
        y1 = Math.max(0, Math.min(source.getHeight() - 1, y1));
        x2 = Math.max(x1 + 1, Math.min(source.getWidth(), x2));
        y2 = Math.max(y1 + 1, Math.min(source.getHeight(), y2));

        int width = x2 - x1;
        int height = y2 - y1;

        if (width <= 0 || height <= 0) {
            return null;
        }

        return source.getSubimage(x1, y1, width, height);
    }

    private BufferedImage rotateTexture(
            BufferedImage source,
            int rotation
    ) {
        int normalized = Math.floorMod(rotation, 360);

        if (normalized == 0) {
            return copyImage(source);
        }

        if (normalized != 90
                && normalized != 180
                && normalized != 270) {
            return copyImage(source);
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        int targetWidth = normalized == 180
                ? sourceWidth
                : sourceHeight;

        int targetHeight = normalized == 180
                ? sourceHeight
                : sourceWidth;

        BufferedImage target = new BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = target.createGraphics();

        try {
            if (normalized == 90) {
                graphics.translate(targetWidth, 0);
                graphics.rotate(Math.toRadians(90));
            } else if (normalized == 180) {
                graphics.translate(targetWidth, targetHeight);
                graphics.rotate(Math.toRadians(180));
            } else {
                graphics.translate(0, targetHeight);
                graphics.rotate(Math.toRadians(270));
            }

            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return target;
    }

    private BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = copy.createGraphics();

        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return copy;
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
        Map<String, List<File>> files =
                resolvedAsset.getResolvedFiles();

        for (Map.Entry<String, List<File>> entry : files.entrySet()) {
            String key = entry.getKey();

            if (key.startsWith("textures:model_texture:")) {
                continue;
            }

            String property = getPropertyName(key);

            if (!isTextureProperty(property)) {
                continue;
            }

            File file = firstPng(entry.getValue());

            if (file != null) {
                return file;
            }
        }

        File layer0 = findModelTexture(files, ":layer0:");

        if (layer0 != null) {
            return layer0;
        }

        File particle = findModelTexture(files, ":particle:");

        if (particle != null) {
            return particle;
        }

        for (Map.Entry<String, List<File>> entry : files.entrySet()) {
            if (!entry.getKey().startsWith("textures:model_texture:")) {
                continue;
            }

            File file = firstPng(entry.getValue());

            if (file != null) {
                return file;
            }
        }

        return null;
    }

    private File findModelTexture(
            Map<String, List<File>> files,
            String textureKey
    ) {
        for (Map.Entry<String, List<File>> entry : files.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);

            if (!key.startsWith("textures:model_texture:")
                    || !key.contains(textureKey)) {
                continue;
            }

            File file = firstPng(entry.getValue());

            if (file != null) {
                return file;
            }
        }

        return null;
    }

    private File firstPng(List<File> files) {
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }

            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                return file;
            }
        }

        return null;
    }

    private String getPropertyName(String key) {
        int separator = key.indexOf(':');

        return (separator >= 0
                ? key.substring(0, separator)
                : key).toLowerCase(Locale.ROOT);
    }

    private boolean isTextureProperty(String property) {
        return property.equals("texture")
                || property.equals("textures")
                || property.equals("icon")
                || property.endsWith(".texture")
                || property.endsWith(".textures")
                || property.endsWith(".icon")
                || property.contains("texture_path")
                || property.contains("texture_file");
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

        writeJson(new File(directory, "manifest.json"), manifest);
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

    private void writeJson(
            File file,
            JsonObject json
    ) throws IOException {

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

    private void createArchive(
            File source,
            File destination
    ) throws IOException {

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
