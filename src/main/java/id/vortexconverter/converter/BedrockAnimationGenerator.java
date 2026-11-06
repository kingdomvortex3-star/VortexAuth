
package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BedrockAnimationGenerator {

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public JsonObject generate(
            BlockbenchModelReader.Animation animation,
            List<BlockbenchModelReader.Bone> bones
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");

        JsonObject animations = new JsonObject();
        JsonObject output = new JsonObject();

        String animationName = sanitize(animation.name());

        output.addProperty(
                "animation_length",
                animation.length()
        );

        output.addProperty(
                "loop",
                isLooping(animation.loop())
        );

        JsonObject boneAnimations = new JsonObject();

        Map<String, String> boneNames = new LinkedHashMap<>();
        collectBoneNames(bones, boneNames);

        for (Map.Entry<String, List<BlockbenchModelReader.Keyframe>> entry
                : animation.boneAnimations().entrySet()) {

            String boneName = boneNames.get(entry.getKey());

            if (boneName == null) {
                continue;
            }

            JsonObject bone = new JsonObject();

            for (BlockbenchModelReader.Keyframe keyframe : entry.getValue()) {
                if (keyframe.dataPoints().isEmpty()) {
                    continue;
                }

                String channel = keyframe.channel().toLowerCase(Locale.ROOT);

                if (!channel.equals("position")
                        && !channel.equals("rotation")
                        && !channel.equals("scale")) {
                    continue;
                }

                JsonObject keyframes = bone.has(channel)
                        ? bone.getAsJsonObject(channel)
                        : new JsonObject();

                JsonArray vector = createVector(
                        keyframe.dataPoints().get(0).value(),
                        channel
                );

                keyframes.add(
                        String.format(
                                Locale.ROOT,
                                "%.6f",
                                keyframe.time()
                        ),
                        vector
                );

                bone.add(channel, keyframes);
            }

            if (!bone.isEmpty()) {
                boneAnimations.add(boneName, bone);
            }
        }

        output.add("bones", boneAnimations);

        animations.add(
                "animation.vortex." + animationName,
                output
        );

        root.add("animations", animations);

        return root;
    }

    private void collectBoneNames(
            List<BlockbenchModelReader.Bone> bones,
            Map<String, String> names
    ) {
        for (BlockbenchModelReader.Bone bone : bones) {
            names.put(bone.uuid(), sanitize(bone.name()));

            collectBoneNames(bone.children(), names);
        }
    }

    private JsonArray createVector(
            BlockbenchModelReader.Vector3 value,
            String channel
    ) {
        JsonArray vector = new JsonArray();

        double x = value.x();
        double y = value.y();
        double z = value.z();

        if (channel.equals("position")) {
            x = x;
            y = -y;
            z = z;
        }

        vector.add(x);
        vector.add(y);
        vector.add(z);

        return vector;
    }

    private boolean isLooping(String loop) {
        return loop != null
                && (
                    loop.equalsIgnoreCase("true")
                    || loop.equalsIgnoreCase("loop")
                );
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }

        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
    }

    public void save(
            BlockbenchModelReader.Animation animation,
            List<BlockbenchModelReader.Bone> bones,
            File output
    ) throws IOException {

        JsonObject result = generate(animation, bones);

        File parent = output.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException(
                    "Cannot create animation output directory."
            );
        }

        try (FileWriter writer = new FileWriter(output)) {
            gson.toJson(result, writer);
        }
    }
}
