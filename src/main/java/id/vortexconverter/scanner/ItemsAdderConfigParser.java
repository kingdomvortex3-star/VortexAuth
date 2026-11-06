
package id.vortexconverter.scanner;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class ItemsAdderConfigParser {

    public enum AssetType {
        ITEM,
        BLOCK,
        FURNITURE,
        ENTITY,
        OTHER
    }

    public static final class AssetInfo {

        private final String namespace;
        private final String id;
        private final AssetType type;
        private final String sourceFile;
        private final Map<String, String> properties;

        public AssetInfo(
                String namespace,
                String id,
                AssetType type,
                String sourceFile,
                Map<String, String> properties
        ) {
            this.namespace = namespace;
            this.id = id;
            this.type = type;
            this.sourceFile = sourceFile;
            this.properties = new LinkedHashMap<>(properties);
        }

        public String getNamespace() {
            return namespace;
        }

        public String getId() {
            return id;
        }

        public AssetType getType() {
            return type;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public Map<String, String> getProperties() {
            return Collections.unmodifiableMap(properties);
        }

        public String getProperty(String key) {
            if (key == null) {
                return null;
            }

            String direct = properties.get(key);

            if (direct != null) {
                return direct;
            }

            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }

            return null;
        }

        public String getMaterial() {
            return firstProperty(
                    "material",
                    "resource.material",
                    "item.material",
                    "specific_properties.material",
                    "graphics.material"
            );
        }

        public String getDisplayName() {
            return firstProperty(
                    "display_name",
                    "name",
                    "item.display_name",
                    "graphics.display_name"
            );
        }

        public String getPermission() {
            return firstProperty(
                    "permission",
                    "permissions",
                    "behaviours.permission"
            );
        }

        public String getModelPath() {
            return firstProperty(
                    "graphics.models",
                    "graphics.model",
                    "graphics.model_path",
                    "resource.model_path",
                    "resource.model",
                    "resource.generate",
                    "model_path",
                    "model",
                    "geometry"
            );
        }

        public String getTexturePath() {
            return firstProperty(
                    "graphics.texture",
                    "graphics.textures",
                    "graphics.icon",
                    "resource.textures",
                    "resource.texture",
                    "textures",
                    "texture",
                    "texture_path",
                    "texture_file"
            );
        }

        public String getCustomModelDataValue() {
            return firstProperty(
                    "custom_model_data",
                    "resource.custom_model_data",
                    "model_id",
                    "resource.model_id",
                    "custom_model_data.value"
            );
        }

        public Integer getCustomModelData() {
            String value = getCustomModelDataValue();

            if (value == null || value.isBlank()) {
                return null;
            }

            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                try {
                    double number = Double.parseDouble(value.trim());

                    if (number >= 0
                            && number <= Integer.MAX_VALUE
                            && number == Math.floor(number)) {
                        return (int) number;
                    }
                } catch (NumberFormatException ignoredAgain) {
                    return null;
                }

                return null;
            }
        }

        public boolean hasCustomModelData() {
            return getCustomModelData() != null;
        }

        public String getJavaIdentifier() {
            String material = getMaterial();

            if (material == null || material.isBlank()) {
                return null;
            }

            String normalized = material
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (normalized.contains(":")) {
                return normalized;
            }

            return "minecraft:" + normalized;
        }

        public String getExplicitBedrockIdentifier() {
            return firstProperty(
                    "bedrock_identifier",
                    "bedrock.identifier",
                    "geyser.identifier",
                    "geyser.bedrock_identifier"
            );
        }

        public String getNamespacedId() {
            return namespace + ":" + id;
        }

        private String firstProperty(String... keys) {
            for (String key : keys) {
                String value = getProperty(key);

                if (value != null && !value.isBlank()) {
                    return value;
                }
            }

            return null;
        }

        private void mergeProperties(Map<String, String> incoming) {
            for (Map.Entry<String, String> entry : incoming.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }

                properties.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    public static final class ParseResult {

        private final List<AssetInfo> assets = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final Set<String> assetKeys = new HashSet<>();

        private int filesRead;
        private int namespacesRead;

        public List<AssetInfo> getAssets() {
            return Collections.unmodifiableList(assets);
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public int getFilesRead() {
            return filesRead;
        }

        public int getNamespacesRead() {
            return namespacesRead;
        }

        public int getTotalAssets() {
            return assets.size();
        }

        public int count(AssetType type) {
            return (int) assets.stream()
                    .filter(asset -> asset.getType() == type)
                    .count();
        }

        public int countCustomModelData() {
            return (int) assets.stream()
                    .filter(AssetInfo::hasCustomModelData)
                    .count();
        }

        public int countWithMaterial() {
            return (int) assets.stream()
                    .filter(asset -> asset.getMaterial() != null)
                    .count();
        }

        public int countWithTexture() {
            return (int) assets.stream()
                    .filter(asset -> asset.getTexturePath() != null)
                    .count();
        }

        public int countWithModel() {
            return (int) assets.stream()
                    .filter(asset -> asset.getModelPath() != null)
                    .count();
        }

        public int countWithBedrockIdentifier() {
            return (int) assets.stream()
                    .filter(asset ->
                            asset.getExplicitBedrockIdentifier() != null
                    )
                    .count();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        private void addAsset(AssetInfo asset) {
            String key = asset.getNamespace().toLowerCase(Locale.ROOT)
                    + ":" + asset.getType().name()
                    + ":" + asset.getId().toLowerCase(Locale.ROOT);

            if (assetKeys.add(key)) {
                assets.add(asset);
                return;
            }

            for (AssetInfo existing : assets) {
                String existingKey =
                        existing.getNamespace().toLowerCase(Locale.ROOT)
                                + ":" + existing.getType().name()
                                + ":" + existing.getId().toLowerCase(Locale.ROOT);

                if (existingKey.equals(key)) {
                    existing.mergeProperties(asset.getProperties());
                    return;
                }
            }
        }
    }

    public ParseResult parse(File contentsFolder) {
        ParseResult result = new ParseResult();

        if (contentsFolder == null
                || !contentsFolder.exists()
                || !contentsFolder.isDirectory()) {
            result.errors.add(
                    "Invalid ItemsAdder contents directory."
            );
            return result;
        }

        File[] namespaces = listDirectories(contentsFolder, result);

        if (namespaces == null) {
            result.errors.add(
                    "Cannot read ItemsAdder namespaces."
            );
            return result;
        }

        for (File namespaceFolder : namespaces) {
            if (!namespaceFolder.isDirectory()) {
                continue;
            }

            result.namespacesRead++;

            scanNamespaceRecursively(
                    namespaceFolder,
                    namespaceFolder.getName(),
                    result
            );
        }

        return result;
    }

    private void scanNamespaceRecursively(
            File folder,
            String namespace,
            ParseResult result
    ) {
        File[] files = folder.listFiles();

        if (files == null) {
            result.errors.add(
                    "Cannot access directory: " + folder.getPath()
            );
            return;
        }

        java.util.Arrays.sort(
                files,
                (first, second) ->
                        first.getName().compareToIgnoreCase(second.getName())
        );

        for (File file : files) {
            if (file.isDirectory()) {
                scanNamespaceRecursively(
                        file,
                        namespace,
                        result
                );
                continue;
            }

            String name = file.getName().toLowerCase(Locale.ROOT);

            if (!name.endsWith(".yml")
                    && !name.endsWith(".yaml")) {
                continue;
            }

            parseFile(file, namespace, result);
        }
    }

    private void parseFile(
            File file,
            String namespace,
            ParseResult result
    ) {
        YamlConfiguration yaml = new YamlConfiguration();

        try {
            yaml.load(file);
            result.filesRead++;

            scanConfigurationSection(
                    yaml,
                    namespace,
                    file,
                    result
            );

        } catch (Exception exception) {
            result.errors.add(
                    file.getPath() + ": " + exception.getMessage()
            );
        }
    }

    private void scanConfigurationSection(
            ConfigurationSection section,
            String namespace,
            File sourceFile,
            ParseResult result
    ) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);

            if (!(value instanceof ConfigurationSection child)) {
                continue;
            }

            AssetType type = identifyType(key);

            if (type != AssetType.OTHER) {
                parseAssetCategory(
                        child,
                        namespace,
                        type,
                        sourceFile,
                        result
                );
            } else {
                scanConfigurationSection(
                        child,
                        namespace,
                        sourceFile,
                        result
                );
            }
        }
    }

    private void parseAssetCategory(
            ConfigurationSection category,
            String namespace,
            AssetType type,
            File sourceFile,
            ParseResult result
    ) {
        for (String id : category.getKeys(false)) {
            Object value = category.get(id);

            if (!(value instanceof ConfigurationSection assetSection)) {
                continue;
            }

            Map<String, String> properties = new LinkedHashMap<>();

            collectProperties(
                    assetSection,
                    "",
                    properties
            );

            result.addAsset(
                    new AssetInfo(
                            namespace,
                            id,
                            type,
                            sourceFile.getPath(),
                            properties
                    )
            );
        }
    }

    private AssetType identifyType(String key) {
        String normalized = key
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");

        return switch (normalized) {
            case "items", "customitems", "item" ->
                    AssetType.ITEM;

            case "blocks", "customblocks", "block" ->
                    AssetType.BLOCK;

            case "furniture", "customfurniture" ->
                    AssetType.FURNITURE;

            case "entities", "customentities", "entity" ->
                    AssetType.ENTITY;

            default -> AssetType.OTHER;
        };
    }

    private void collectProperties(
            ConfigurationSection section,
            String prefix,
            Map<String, String> properties
    ) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);

            String path = prefix.isEmpty()
                    ? key
                    : prefix + "." + key;

            collectValue(path, value, properties);
        }
    }

    private void collectValue(
            String path,
            Object value,
            Map<String, String> properties
    ) {
        if (value == null) {
            return;
        }

        if (value instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                collectValue(
                        path + "." + key,
                        section.get(key),
                        properties
                );
            }
            return;
        }

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }

                collectValue(
                        path + "." + entry.getKey(),
                        entry.getValue(),
                        properties
                );
            }
            return;
        }

        if (value instanceof List<?> list) {
            List<String> simpleValues = new ArrayList<>();

            for (int index = 0; index < list.size(); index++) {
                Object element = list.get(index);

                if (element instanceof Map<?, ?>
                        || element instanceof ConfigurationSection
                        || element instanceof List<?>) {
                    collectValue(
                            path + "." + index,
                            element,
                            properties
                    );
                } else if (element != null) {
                    simpleValues.add(String.valueOf(element));
                }
            }

            if (!simpleValues.isEmpty()) {
                properties.put(
                        path,
                        String.join(", ", simpleValues)
                );
            }

            return;
        }

        properties.put(path, String.valueOf(value));
    }

    private File[] listDirectories(
            File folder,
            ParseResult result
    ) {
        File[] directories = folder.listFiles(File::isDirectory);

        if (directories == null) {
            return null;
        }

        java.util.Arrays.sort(
                directories,
                (first, second) ->
                        first.getName().compareToIgnoreCase(second.getName())
        );

        return directories;
    }
}
