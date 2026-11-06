
package id.vortexconverter.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockbenchModelReader {

    public record Vector3(double x, double y, double z) {}

    public record Cube(
            String uuid,
            String name,
            Vector3 from,
            Vector3 to,
            Vector3 origin,
            Vector3 rotation
    ) {}

    public record Bone(
            String uuid,
            String name,
            String parent,
            Vector3 pivot,
            Vector3 rotation,
            List<Cube> cubes,
            List<Bone> children
    ) {}

    public record DataPoint(Vector3 value) {}

    public record Keyframe(
            double time,
            String channel,
            String interpolation,
            List<DataPoint> dataPoints
    ) {}

    public record Animation(
            String name,
            String loop,
            double length,
            double overrideSpeed,
            Map<String, List<Keyframe>> boneAnimations
    ) {}

    public record BlockbenchModel(
            String name,
            int textureWidth,
            int textureHeight,
            List<Bone> bones,
            List<Animation> animations
    ) {}

    private final Map<String, Cube> cubeIndex = new LinkedHashMap<>();

    public BlockbenchModel read(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("BBModel file does not exist.");
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader)
                    .getAsJsonObject();

            cubeIndex.clear();

            JsonObject resolution = object(root, "resolution");

            indexCubes(root);

            List<Bone> bones = new ArrayList<>();

            for (JsonElement element : array(root, "outliner")) {
                Bone bone = readBone(element, null);
                if (bone != null) {
                    bones.add(bone);
                }
            }

            List<Animation> animations = readAnimations(root);

            return new BlockbenchModel(
                    string(root, "name", "unnamed"),
                    integer(resolution, "width", 16),
                    integer(resolution, "height", 16),
                    Collections.unmodifiableList(bones),
                    Collections.unmodifiableList(animations)
            );

        } catch (RuntimeException exception) {
            throw new IOException(
                    "Invalid BBModel: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void indexCubes(JsonObject root) {
        for (JsonElement element : array(root, "elements")) {
            JsonObject object = element.getAsJsonObject();

            String uuid = string(object, "uuid", "");

            if (uuid.isBlank()) {
                continue;
            }

            cubeIndex.put(uuid, new Cube(
                    uuid,
                    string(object, "name", uuid),
                    vector(object, "from"),
                    vector(object, "to"),
                    vector(object, "origin"),
                    vector(object, "rotation")
            ));
        }
    }

    private Bone readBone(JsonElement element, String parent) {
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();

        String uuid = string(object, "uuid", "");
        String name = string(object, "name", "bone");

        List<Cube> cubes = new ArrayList<>();
        List<Bone> children = new ArrayList<>();

        for (JsonElement child : array(object, "children")) {
            if (child.isJsonPrimitive()) {
                Cube cube = cubeIndex.get(child.getAsString());

                if (cube != null) {
                    cubes.add(cube);
                }
            } else if (child.isJsonObject()) {
                Bone nested = readBone(child, uuid);

                if (nested != null) {
                    children.add(nested);
                }
            }
        }

        return new Bone(
                uuid,
                name,
                parent,
                vector(object, "origin"),
                vector(object, "rotation"),
                Collections.unmodifiableList(cubes),
                Collections.unmodifiableList(children)
        );
    }

    private List<Animation> readAnimations(JsonObject root) {
        List<Animation> animations = new ArrayList<>();

        for (JsonElement element : array(root, "animations")) {
            JsonObject object = element.getAsJsonObject();

            Map<String, List<Keyframe>> boneAnimations =
                    new LinkedHashMap<>();

            JsonObject animators = object(object, "animators");

            for (Map.Entry<String, JsonElement> entry
                    : animators.entrySet()) {

                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                JsonObject animator = entry.getValue().getAsJsonObject();

                List<Keyframe> keyframes = readKeyframes(animator);

                boneAnimations.put(
                        entry.getKey(),
                        Collections.unmodifiableList(keyframes)
                );
            }

            animations.add(new Animation(
                    string(object, "name", "unnamed"),
                    string(object, "loop", "false"),
                    decimal(object, "length", 0),
                    decimal(object, "override_speed", 0),
                    Collections.unmodifiableMap(boneAnimations)
            ));
        }

        return animations;
    }

    private List<Keyframe> readKeyframes(JsonObject animator) {
        List<Keyframe> keyframes = new ArrayList<>();

        for (JsonElement element : array(animator, "keyframes")) {
            JsonObject object = element.getAsJsonObject();

            String channel = string(
                    object,
                    "channel",
                    "rotation"
            );

            List<DataPoint> points = new ArrayList<>();

            for (JsonElement point : array(object, "data_points")) {
                if (!point.isJsonObject()) {
                    continue;
                }

                JsonObject data = point.getAsJsonObject();

                points.add(new DataPoint(
                        new Vector3(
                                decimalString(data, "x", 0),
                                decimalString(data, "y", 0),
                                decimalString(data, "z", 0)
                        )
                ));
            }

            keyframes.add(new Keyframe(
                    decimal(object, "time", 0),
                    channel,
                    string(object, "interpolation", "linear"),
                    Collections.unmodifiableList(points)
            ));
        }

        return keyframes;
    }

    private Vector3 vector(JsonObject object, String key) {
        JsonArray values = array(object, key);

        if (values.size() != 3) {
            return new Vector3(0, 0, 0);
        }

        return new Vector3(
                values.get(0).getAsDouble(),
                values.get(1).getAsDouble(),
                values.get(2).getAsDouble()
        );
    }

    private JsonObject object(JsonObject object, String key) {
        if (object == null || !object.has(key)
                || !object.get(key).isJsonObject()) {
            return new JsonObject();
        }

        return object.getAsJsonObject(key);
    }

    private JsonArray array(JsonObject object, String key) {
        if (object == null || !object.has(key)
                || !object.get(key).isJsonArray()) {
            return new JsonArray();
        }

        return object.getAsJsonArray(key);
    }

    private String string(
            JsonObject object,
            String key,
            String fallback
    ) {
        if (object == null || !object.has(key)
                || object.get(key).isJsonNull()) {
            return fallback;
        }

        return object.get(key).getAsString();
    }

    private int integer(
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

    private double decimal(
            JsonObject object,
            String key,
            double fallback
    ) {
        try {
            return object.get(key).getAsDouble();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private double decimalString(
            JsonObject object,
            String key,
            double fallback
    ) {
        try {
            return Double.parseDouble(
                    string(object, key, String.valueOf(fallback))
            );
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
