
package id.vortexconverter.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BlockbenchTextureMapper {

    public record FaceUV(
            double u,
            double v,
            double width,
            double height,
            int rotation
    ) {}

    public record CubeUV(
            String uuid,
            Map<String, FaceUV> faces
    ) {}

    public Map<String, CubeUV> read(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("BBModel file does not exist.");
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader)
                    .getAsJsonObject();

            Map<String, CubeUV> result = new LinkedHashMap<>();

            JsonArray elements = getArray(root, "elements");

            for (JsonElement element : elements) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject cube = element.getAsJsonObject();
                String uuid = getString(cube, "uuid", "");

                if (uuid.isBlank()) {
                    continue;
                }

                JsonObject faceObject = getObject(cube, "faces");

                Map<String, FaceUV> faces = new LinkedHashMap<>();

                for (Map.Entry<String, JsonElement> entry
                        : faceObject.entrySet()) {

                    String direction = normalizeDirection(entry.getKey());

                    if (direction == null || !entry.getValue().isJsonObject()) {
                        continue;
                    }

                    JsonObject face = entry.getValue().getAsJsonObject();

                    JsonArray uv = getArray(face, "uv");

                    if (uv.size() != 4) {
                        continue;
                    }

                    double u1 = uv.get(0).getAsDouble();
                    double v1 = uv.get(1).getAsDouble();
                    double u2 = uv.get(2).getAsDouble();
                    double v2 = uv.get(3).getAsDouble();

                    int rotation = getInt(face, "rotation", 0);

                    faces.put(
                            direction,
                            new FaceUV(
                                    u1,
                                    v1,
                                    u2 - u1,
                                    v2 - v1,
                                    rotation
                            )
                    );
                }

                result.put(
                        uuid,
                        new CubeUV(
                                uuid,
                                Collections.unmodifiableMap(faces)
                        )
                );
            }

            return Collections.unmodifiableMap(result);

        } catch (RuntimeException exception) {
            throw new IOException(
                    "Unable to read BBModel UV data: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public JsonObject toBedrockUV(CubeUV cubeUV) {
        JsonObject result = new JsonObject();

        if (cubeUV == null || cubeUV.faces() == null) {
            return result;
        }

        for (Map.Entry<String, FaceUV> entry
                : cubeUV.faces().entrySet()) {

            FaceUV face = entry.getValue();

            JsonObject faceJson = new JsonObject();

            JsonArray uv = new JsonArray();
            uv.add(face.u());
            uv.add(face.v());

            JsonArray uvSize = new JsonArray();
            uvSize.add(face.width());
            uvSize.add(face.height());

            faceJson.add("uv", uv);
            faceJson.add("uv_size", uvSize);

            if (face.rotation() != 0) {
                faceJson.addProperty(
                        "uv_rotation",
                        normalizeRotation(face.rotation())
                );
            }

            result.add(entry.getKey(), faceJson);
        }

        return result;
    }

    private String normalizeDirection(String direction) {
        if (direction == null) {
            return null;
        }

        return switch (direction.toLowerCase()) {
            case "north" -> "north";
            case "south" -> "south";
            case "east" -> "east";
            case "west" -> "west";
            case "up" -> "up";
            case "down" -> "down";
            default -> null;
        };
    }

    private int normalizeRotation(int rotation) {
        int normalized = rotation % 360;

        if (normalized < 0) {
            normalized += 360;
        }

        return normalized;
    }

    private JsonObject getObject(JsonObject object, String key) {
        if (object == null
                || !object.has(key)
                || !object.get(key).isJsonObject()) {
            return new JsonObject();
        }

        return object.getAsJsonObject(key);
    }

    private JsonArray getArray(JsonObject object, String key) {
        if (object == null
                || !object.has(key)
                || !object.get(key).isJsonArray()) {
            return new JsonArray();
        }

        return object.getAsJsonArray(key);
    }

    private String getString(
            JsonObject object,
            String key,
            String fallback
    ) {
        if (object == null
                || !object.has(key)
                || object.get(key).isJsonNull()) {
            return fallback;
        }

        return object.get(key).getAsString();
    }

    private int getInt(
            JsonObject object,
            String key,
            int fallback
    ) {
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }
}
