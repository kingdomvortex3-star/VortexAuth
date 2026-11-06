
package id.vortexconverter.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BedrockPackValidator {

    public ValidationResult validate(File packDirectory) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (packDirectory == null) {
            errors.add("Pack directory is null.");
            return result(errors, warnings);
        }

        if (!packDirectory.exists() || !packDirectory.isDirectory()) {
            errors.add("Pack directory does not exist.");
            return result(errors, warnings);
        }

        File manifest = new File(packDirectory, "manifest.json");

        if (!manifest.isFile()) {
            errors.add("Missing manifest.json.");
        } else {
            validateManifest(manifest, errors, warnings);
        }

        File modelsDirectory = new File(packDirectory, "models/entity");
        File animationsDirectory = new File(packDirectory, "animations");
        File texturesDirectory = new File(packDirectory, "textures/entity");

        if (!modelsDirectory.isDirectory()) {
            errors.add("Missing models/entity directory.");
        } else {
            validateJsonDirectory(
                    modelsDirectory,
                    "geometry",
                    errors,
                    warnings
            );
        }

        if (!animationsDirectory.isDirectory()) {
            warnings.add("No animations directory found.");
        } else {
            validateJsonDirectory(
                    animationsDirectory,
                    "animations",
                    errors,
                    warnings
            );
        }

        if (!texturesDirectory.isDirectory()) {
            warnings.add("No textures/entity directory found.");
        } else {
            validateTextures(texturesDirectory, warnings);
        }

        return result(errors, warnings);
    }

    private void validateManifest(
            File manifest,
            List<String> errors,
            List<String> warnings
    ) {
        JsonObject root = readJsonObject(manifest, errors);

        if (root == null) {
            return;
        }

        if (!root.has("format_version")
                || !root.get("format_version").isJsonPrimitive()) {
            errors.add("manifest.json: missing format_version.");
        }

        if (!root.has("header")
                || !root.get("header").isJsonObject()) {
            errors.add("manifest.json: missing header object.");
            return;
        }

        JsonObject header = root.getAsJsonObject("header");

        validateTextField(header, "name", "manifest header", errors);
        validateTextField(header, "description", "manifest header", errors);

        validateUuidField(
                header,
                "uuid",
                "manifest header",
                errors
        );

        if (!header.has("version")
                || !isVersionArray(header.get("version"))) {
            errors.add("manifest header: invalid version array.");
        }

        if (!header.has("min_engine_version")
                || !isVersionArray(header.get("min_engine_version"))) {
            warnings.add(
                    "manifest header: min_engine_version is missing or invalid."
            );
        }

        if (!root.has("modules")
                || !root.get("modules").isJsonArray()) {
            errors.add("manifest.json: missing modules array.");
            return;
        }

        if (root.getAsJsonArray("modules").isEmpty()) {
            errors.add("manifest.json: modules array is empty.");
            return;
        }

        for (int i = 0; i < root.getAsJsonArray("modules").size(); i++) {
            JsonElement element = root.getAsJsonArray("modules").get(i);

            if (!element.isJsonObject()) {
                errors.add("manifest module " + i + " is not an object.");
                continue;
            }

            JsonObject module = element.getAsJsonObject();

            validateUuidField(
                    module,
                    "uuid",
                    "manifest module " + i,
                    errors
            );

            if (!module.has("type")
                    || !module.get("type").isJsonPrimitive()) {
                errors.add("manifest module " + i + ": missing type.");
            }

            if (!module.has("version")
                    || !isVersionArray(module.get("version"))) {
                errors.add("manifest module " + i + ": invalid version.");
            }
        }

        checkDuplicateUuids(root, errors);
    }

    private void validateJsonDirectory(
            File directory,
            String type,
            List<String> errors,
            List<String> warnings
    ) {
        File[] files = directory.listFiles();

        if (files == null) {
            errors.add("Cannot read directory: " + directory.getPath());
            return;
        }

        int jsonCount = 0;

        for (File file : files) {
            if (file.isDirectory()) {
                validateJsonDirectory(
                        file,
                        type,
                        errors,
                        warnings
                );
                continue;
            }

            if (!file.getName().toLowerCase().endsWith(".json")) {
                continue;
            }

            jsonCount++;

            JsonObject root = readJsonObject(file, errors);

            if (root == null) {
                continue;
            }

            if (!root.has("format_version")) {
                warnings.add(
                        file.getName() + ": missing format_version."
                );
            }

            if ("geometry".equals(type)) {
                validateGeometry(root, file, errors, warnings);
            } else {
                validateAnimations(root, file, errors, warnings);
            }
        }

        if (jsonCount == 0) {
            warnings.add(
                    "No JSON files found in " + directory.getPath()
            );
        }
    }

    private void validateGeometry(
            JsonObject root,
            File file,
            List<String> errors,
            List<String> warnings
    ) {
        if (!root.has("minecraft:geometry")
                || !root.get("minecraft:geometry").isJsonArray()) {
            errors.add(
                    file.getName() + ": missing minecraft:geometry array."
            );
            return;
        }

        JsonElement geometryElement =
                root.getAsJsonArray("minecraft:geometry");

        if (geometryElement.getAsJsonArray().isEmpty()) {
            errors.add(file.getName() + ": geometry array is empty.");
            return;
        }

        for (int i = 0; i < geometryElement.getAsJsonArray().size(); i++) {
            JsonElement element = geometryElement.getAsJsonArray().get(i);

            if (!element.isJsonObject()) {
                errors.add(
                        file.getName() + ": geometry entry " + i
                                + " is not an object."
                );
                continue;
            }

            JsonObject geometry = element.getAsJsonObject();

            if (!geometry.has("description")
                    || !geometry.get("description").isJsonObject()) {
                errors.add(
                        file.getName() + ": geometry entry " + i
                                + " has no description."
                );
            }

            if (!geometry.has("bones")
                    || !geometry.get("bones").isJsonArray()) {
                errors.add(
                        file.getName() + ": geometry entry " + i
                                + " has no bones array."
                );
            } else if (geometry.getAsJsonArray("bones").isEmpty()) {
                warnings.add(
                        file.getName() + ": geometry entry " + i
                                + " has no bones."
                );
            }
        }
    }

    private void validateAnimations(
            JsonObject root,
            File file,
            List<String> errors,
            List<String> warnings
    ) {
        if (!root.has("animations")
                || !root.get("animations").isJsonObject()) {
            errors.add(
                    file.getName() + ": missing animations object."
            );
            return;
        }

        JsonObject animations = root.getAsJsonObject("animations");

        if (animations.entrySet().isEmpty()) {
            warnings.add(file.getName() + ": no animations defined.");
        }

        for (String name : animations.keySet()) {
            JsonElement animation = animations.get(name);

            if (!animation.isJsonObject()) {
                errors.add(
                        file.getName() + ": animation "
                                + name + " is not an object."
                );
            }
        }
    }

    private void validateTextures(
            File directory,
            List<String> warnings
    ) {
        File[] files = directory.listFiles();

        if (files == null) {
            warnings.add("Cannot read texture directory.");
            return;
        }

        int textureCount = 0;

        for (File file : files) {
            if (file.isDirectory()) {
                validateTextures(file, warnings);
                continue;
            }

            String name = file.getName().toLowerCase();

            if (name.endsWith(".png")) {
                textureCount++;

                if (file.length() == 0) {
                    warnings.add(
                            "Empty texture file: " + file.getPath()
                    );
                }
            }
        }

        if (textureCount == 0) {
            warnings.add("No PNG textures found.");
        }
    }

    private JsonObject readJsonObject(
            File file,
            List<String> errors
    ) {
        try {
            String content = Files.readString(
                    file.toPath(),
                    StandardCharsets.UTF_8
            );

            JsonElement element = JsonParser.parseString(content);

            if (!element.isJsonObject()) {
                errors.add(
                        file.getName() + ": JSON root must be an object."
                );
                return null;
            }

            return element.getAsJsonObject();

        } catch (IOException | JsonSyntaxException exception) {
            errors.add(
                    file.getName() + ": invalid JSON - "
                            + exception.getMessage()
            );
            return null;
        }
    }

    private void validateTextField(
            JsonObject object,
            String field,
            String context,
            List<String> errors
    ) {
        if (!object.has(field)
                || !object.get(field).isJsonPrimitive()
                || object.get(field).getAsString().isBlank()) {
            errors.add(context + ": missing or empty " + field + ".");
        }
    }

    private void validateUuidField(
            JsonObject object,
            String field,
            String context,
            List<String> errors
    ) {
        if (!object.has(field)
                || !object.get(field).isJsonPrimitive()) {
            errors.add(context + ": missing " + field + ".");
            return;
        }

        try {
            UUID.fromString(object.get(field).getAsString());
        } catch (IllegalArgumentException exception) {
            errors.add(context + ": invalid UUID in " + field + ".");
        }
    }

    private boolean isVersionArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return false;
        }

        if (element.getAsJsonArray().size() != 3) {
            return false;
        }

        for (JsonElement part : element.getAsJsonArray()) {
            if (!part.isJsonPrimitive()
                    || !part.getAsJsonPrimitive().isNumber()) {
                return false;
            }

            if (part.getAsInt() < 0) {
                return false;
            }
        }

        return true;
    }

    private void checkDuplicateUuids(
            JsonObject root,
            List<String> errors
    ) {
        if (!root.has("header")
                || !root.get("header").isJsonObject()
                || !root.has("modules")
                || !root.get("modules").isJsonArray()) {
            return;
        }

        List<String> uuids = new ArrayList<>();

        JsonObject header = root.getAsJsonObject("header");

        if (header.has("uuid")) {
            uuids.add(header.get("uuid").getAsString());
        }

        for (JsonElement element : root.getAsJsonArray("modules")) {
            if (element.isJsonObject()
                    && element.getAsJsonObject().has("uuid")) {
                uuids.add(
                        element.getAsJsonObject()
                                .get("uuid").getAsString()
                );
            }
        }

        long uniqueCount = uuids.stream().distinct().count();

        if (uniqueCount != uuids.size()) {
            errors.add("manifest.json: duplicate UUID detected.");
        }
    }

    private ValidationResult result(
            List<String> errors,
            List<String> warnings
    ) {
        return new ValidationResult(
                errors.isEmpty(),
                List.copyOf(errors),
                List.copyOf(warnings)
        );
    }

    public record ValidationResult(
            boolean valid,
            List<String> errors,
            List<String> warnings
    ) {
        public int errorCount() {
            return errors.size();
        }

        public int warningCount() {
            return warnings.size();
        }
    }
}
