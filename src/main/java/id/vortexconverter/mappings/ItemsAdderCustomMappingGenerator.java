
package id.vortexconverter.mappings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import id.vortexconverter.scanner.ItemsAdderConfigParser;
import id.vortexconverter.scanner.ItemsAdderConfigParser.AssetInfo;
import id.vortexconverter.scanner.ItemsAdderConfigParser.AssetType;
import id.vortexconverter.scanner.ItemsAdderConfigParser.ParseResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ItemsAdderCustomMappingGenerator {

    private static final int FORMAT_VERSION = 2;

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public MappingResult generate(ParseResult parseResult) {
        if (parseResult == null) {
            throw new IllegalArgumentException(
                    "ItemsAdder parse result cannot be null."
            );
        }

        JsonObject root = new JsonObject();
        root.addProperty("format_version", FORMAT_VERSION);

        JsonObject items = new JsonObject();

        Map<String, JsonArray> mappings = new LinkedHashMap<>();
        Set<String> registeredDefinitions = new HashSet<>();

        List<String> warnings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        int discovered = 0;
        int generated = 0;

        for (AssetInfo asset : parseResult.getAssets()) {
            if (asset.getType() != AssetType.ITEM) {
                continue;
            }

            discovered++;

            String javaIdentifier = asset.getJavaIdentifier();

            if (javaIdentifier == null || javaIdentifier.isBlank()) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": missing Java material."
                );
                continue;
            }

            Float customModelData = parseCustomModelData(asset);

            if (customModelData == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": missing or invalid custom_model_data."
                );
                continue;
            }

            if (customModelData < 0) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": custom_model_data cannot be negative."
                );
                continue;
            }

            String definitionKey = javaIdentifier
                    + "#"
                    + customModelData;

            if (!registeredDefinitions.add(definitionKey)) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": duplicate Java item and custom_model_data "
                                + "combination (" + definitionKey + ")."
                );
                continue;
            }

            String bedrockIdentifier =
                    resolveBedrockIdentifier(asset);

            if (bedrockIdentifier == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": invalid Bedrock identifier."
                );
                continue;
            }

            JsonObject definition = new JsonObject();

            definition.addProperty("type", "legacy");
            definition.addProperty(
                    "custom_model_data",
                    customModelData
            );
            definition.addProperty(
                    "bedrock_identifier",
                    bedrockIdentifier
            );

            String displayName = asset.getDisplayName();

            if (displayName != null && !displayName.isBlank()) {
                definition.addProperty(
                        "display_name",
                        displayName
                );
            }

            JsonArray definitions = mappings.computeIfAbsent(
                    javaIdentifier,
                    ignored -> new JsonArray()
            );

            definitions.add(definition);
            generated++;

            if (asset.getExplicitBedrockIdentifier() == null) {
                warnings.add(
                        asset.getNamespacedId()
                                + ": generated Bedrock identifier "
                                + bedrockIdentifier
                                + ". Make sure the Bedrock resource pack "
                                + "defines this identifier."
                );
            }
        }

        for (Map.Entry<String, JsonArray> entry : mappings.entrySet()) {
            items.add(entry.getKey(), entry.getValue());
        }

        root.add("items", items);

        if (parseResult.hasErrors()) {
            warnings.add(
                    "ItemsAdder parser reported "
                            + parseResult.getErrors().size()
                            + " configuration error(s)."
            );
        }

        return new MappingResult(
                root,
                discovered,
                generated,
                skipped,
                warnings
        );
    }

    public MappingResult write(
            ParseResult parseResult,
            File outputFile
    ) throws IOException {

        if (outputFile == null) {
            throw new IllegalArgumentException(
                    "Output file cannot be null."
            );
        }

        MappingResult result = generate(parseResult);

        File parent = outputFile.getAbsoluteFile().getParentFile();

        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }

        Files.writeString(
                outputFile.toPath(),
                gson.toJson(result.mapping()),
                StandardCharsets.UTF_8
        );

        return result.withOutputFile(outputFile);
    }

    private Float parseCustomModelData(AssetInfo asset) {
        String value = asset.getCustomModelDataValue();

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            float parsed = Float.parseFloat(value.trim());

            if (!Float.isFinite(parsed)) {
                return null;
            }

            return parsed;

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String resolveBedrockIdentifier(AssetInfo asset) {
        String explicit = asset.getExplicitBedrockIdentifier();

        if (explicit != null && !explicit.isBlank()) {
            String identifier = explicit.trim().toLowerCase(Locale.ROOT);

            if (!isValidBedrockIdentifier(identifier)) {
                return null;
            }

            return identifier;
        }

        String namespace = sanitizeIdentifier(asset.getNamespace());
        String id = sanitizeIdentifier(asset.getId());

        if (namespace.isBlank() || id.isBlank()) {
            return null;
        }

        return "vortexconverter:" + namespace + "_" + id;
    }

    private boolean isValidBedrockIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }

        int separator = identifier.indexOf(':');

        if (separator <= 0
                || separator == identifier.length() - 1
                || identifier.indexOf(':', separator + 1) >= 0) {
            return false;
        }

        String namespace = identifier.substring(0, separator);
        String name = identifier.substring(separator + 1);

        if ("minecraft".equals(namespace)) {
            return false;
        }

        return namespace.matches("[a-z0-9_.-]+")
                && name.matches("[a-z0-9_./-]+");
    }

    private String sanitizeIdentifier(String input) {
        if (input == null) {
            return "";
        }

        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
    }

    public record MappingResult(
            JsonObject mapping,
            int discoveredItems,
            int generatedMappings,
            List<String> skipped,
            List<String> warnings,
            File outputFile
    ) {
        public MappingResult {
            skipped = List.copyOf(skipped);
            warnings = List.copyOf(warnings);
        }

        public MappingResult(
                JsonObject mapping,
                int discoveredItems,
                int generatedMappings,
                List<String> skipped,
                List<String> warnings
        ) {
            this(
                    mapping,
                    discoveredItems,
                    generatedMappings,
                    skipped,
                    warnings,
                    null
            );
        }

        public int skippedCount() {
            return skipped.size();
        }

        public int warningCount() {
            return warnings.size();
        }

        public boolean hasMappings() {
            return generatedMappings > 0;
        }

        public boolean isSuccessful() {
            return hasMappings();
        }

        private MappingResult withOutputFile(File file) {
            return new MappingResult(
                    mapping,
                    discoveredItems,
                    generatedMappings,
                    skipped,
                    warnings,
                    file
            );
        }
    }
}
