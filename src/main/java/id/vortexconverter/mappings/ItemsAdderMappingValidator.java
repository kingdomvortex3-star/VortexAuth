
package id.vortexconverter.mappings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ItemsAdderMappingValidator {

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public ValidationResult validate(
            ItemsAdderCustomMappingGenerator.MappingResult result
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (result == null) {
            errors.add("MappingResult tidak tersedia.");
            return new ValidationResult(false, 0, errors, warnings);
        }

        if (result.mapping() == null) {
            errors.add("JSON mapping tidak tersedia.");
            return new ValidationResult(false, 0, errors, warnings);
        }

        return validate(result.mapping());
    }

    public ValidationResult validate(JsonObject mapping) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (mapping == null) {
            errors.add("Mapping JSON tidak tersedia.");
            return new ValidationResult(false, 0, errors, warnings);
        }

        JsonElement formatVersion = mapping.get("format_version");

        if (formatVersion == null
                || !formatVersion.isJsonPrimitive()
                || !formatVersion.getAsJsonPrimitive().isNumber()) {
            errors.add("format_version harus berupa angka.");
        } else if (formatVersion.getAsInt() != 2) {
            errors.add("format_version harus bernilai 2.");
        }

        JsonElement itemsElement = mapping.get("items");

        if (itemsElement == null || !itemsElement.isJsonObject()) {
            errors.add("Bagian items harus berupa JSON object.");
            return new ValidationResult(false, 0, errors, warnings);
        }

        JsonObject items = itemsElement.getAsJsonObject();

        if (items.size() == 0) {
            errors.add("Tidak ada item mapping yang ditemukan.");
            return new ValidationResult(false, 0, errors, warnings);
        }

        int validDefinitions = 0;

        for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
            String javaIdentifier = entry.getKey();
            JsonElement definitionsElement = entry.getValue();

            if (!isValidIdentifier(javaIdentifier)) {
                errors.add(
                        "Java item identifier tidak valid: "
                                + javaIdentifier
                );
                continue;
            }

            if (definitionsElement == null
                    || !definitionsElement.isJsonArray()) {
                errors.add(
                        javaIdentifier
                                + ": definitions harus berupa JSON array."
                );
                continue;
            }

            JsonArray definitions = definitionsElement.getAsJsonArray();

            if (definitions.isEmpty()) {
                errors.add(
                        javaIdentifier
                                + ": array definitions kosong."
                );
                continue;
            }

            for (int i = 0; i < definitions.size(); i++) {
                JsonElement definitionElement = definitions.get(i);

                if (!definitionElement.isJsonObject()) {
                    errors.add(
                            javaIdentifier
                                    + ": definition indeks "
                                    + i
                                    + " bukan JSON object."
                    );
                    continue;
                }

                JsonObject definition = definitionElement.getAsJsonObject();

                if (validateLegacyDefinition(
                        javaIdentifier,
                        i,
                        definition,
                        errors,
                        warnings
                )) {
                    validDefinitions++;
                }
            }
        }

        boolean successful = errors.isEmpty()
                && validDefinitions > 0;

        return new ValidationResult(
                successful,
                validDefinitions,
                errors,
                warnings
        );
    }

    private boolean validateLegacyDefinition(
            String javaIdentifier,
            int index,
            JsonObject definition,
            List<String> errors,
            List<String> warnings
    ) {
        String prefix = javaIdentifier + " [" + index + "]";

        JsonElement typeElement = definition.get("type");

        if (typeElement == null
                || !typeElement.isJsonPrimitive()
                || !typeElement.getAsJsonPrimitive().isString()) {
            errors.add(prefix + ": type wajib berupa string.");
            return false;
        }

        String type = typeElement.getAsString();

        if (!"legacy".equals(type)) {
            errors.add(
                    prefix
                            + ": hanya type legacy yang didukung generator ini."
            );
            return false;
        }

        JsonElement customModelDataElement =
                definition.get("custom_model_data");

        if (customModelDataElement == null
                || !customModelDataElement.isJsonPrimitive()
                || !customModelDataElement.getAsJsonPrimitive().isNumber()) {
            errors.add(
                    prefix
                            + ": custom_model_data wajib berupa angka."
            );
            return false;
        }

        double customModelData;

        try {
            customModelData = customModelDataElement.getAsDouble();
        } catch (NumberFormatException exception) {
            errors.add(prefix + ": custom_model_data tidak valid.");
            return false;
        }

        if (!Double.isFinite(customModelData) || customModelData < 0) {
            errors.add(
                    prefix
                            + ": custom_model_data harus angka positif atau nol."
            );
            return false;
        }

        JsonElement bedrockIdentifierElement =
                definition.get("bedrock_identifier");

        if (bedrockIdentifierElement == null
                || !bedrockIdentifierElement.isJsonPrimitive()
                || !bedrockIdentifierElement.getAsJsonPrimitive().isString()) {
            errors.add(
                    prefix
                            + ": bedrock_identifier wajib berupa string."
            );
            return false;
        }

        String bedrockIdentifier =
                bedrockIdentifierElement.getAsString();

        if (!isValidIdentifier(bedrockIdentifier)) {
            errors.add(
                    prefix
                            + ": bedrock_identifier tidak valid: "
                            + bedrockIdentifier
            );
            return false;
        }

        if (bedrockIdentifier.startsWith("minecraft:")) {
            errors.add(
                    prefix
                            + ": bedrock_identifier tidak boleh memakai "
                            + "namespace minecraft."
            );
            return false;
        }

        JsonElement displayName = definition.get("display_name");

        if (displayName != null
                && !displayName.isJsonPrimitive()) {
            errors.add(
                    prefix
                            + ": display_name harus berupa string atau "
                            + "JSON text component."
            );
            return false;
        }

        if (displayName != null
                && displayName.isJsonPrimitive()
                && !displayName.getAsJsonPrimitive().isString()
                && !displayName.getAsJsonPrimitive().isNumber()
                && !displayName.getAsJsonPrimitive().isBoolean()) {
            errors.add(prefix + ": display_name tidak valid.");
            return false;
        }

        warnings.add(
                prefix
                        + ": pastikan Bedrock resource pack menyediakan "
                        + "item "
                        + bedrockIdentifier
                        + "."
        );

        return true;
    }

    private boolean isValidIdentifier(String identifier) {
        return identifier != null
                && IDENTIFIER_PATTERN.matcher(identifier).matches();
    }

    public record ValidationResult(
            boolean successful,
            int validDefinitions,
            List<String> errors,
            List<String> warnings
    ) {
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }

        public boolean isSuccessful() {
            return successful && !hasErrors();
        }
    }
}
