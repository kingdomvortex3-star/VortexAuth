
package id.vortexconverter.commands;

import id.vortexconverter.VortexConverter;
import id.vortexconverter.converter.BlockbenchPackPipeline;
import id.vortexconverter.converter.ItemsAdderBedrockConversionService;
import id.vortexconverter.converter.ItemsAdderBedrockConversionService.ConversionResult;
import id.vortexconverter.mappings.ItemsAdderMappingService;
import id.vortexconverter.scanner.ItemsAdderScanner;
import id.vortexconverter.scanner.ItemsAdderConfigParser;
import id.vortexconverter.scanner.ItemsAdderAssetResolver;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandManager implements CommandExecutor, TabCompleter {

    private final VortexConverter plugin;
    private final ItemsAdderScanner scanner;
    private final ItemsAdderConfigParser parser;
    private final ItemsAdderAssetResolver resolver;
    private final BlockbenchPackPipeline blockbenchPipeline;
    private final ItemsAdderMappingService mappingService;
    private final ItemsAdderBedrockConversionService bedrockConversionService;

    public CommandManager(VortexConverter plugin) {
        this.plugin = plugin;
        this.scanner = new ItemsAdderScanner(plugin);
        this.parser = new ItemsAdderConfigParser();
        this.resolver = new ItemsAdderAssetResolver();
        this.blockbenchPipeline = new BlockbenchPackPipeline();
        this.mappingService = new ItemsAdderMappingService();
        this.bedrockConversionService =
                new ItemsAdderBedrockConversionService();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("vortexconverter.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> sendHelp(sender);

            case "scan" -> scanPlugins(sender);

            case "itemsadder" -> handleItemsAdder(sender, args);

            case "report" -> sendReport(sender);

            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aConfiguration reloaded.");
            }

            case "convert" -> convertBlockbench(sender, args);

            case "mappings" -> handleMappings(sender, args);

            default -> sender.sendMessage(
                    "§cUnknown command. Use /vortexconvert help."
            );
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter");
        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§b/vortexconvert help §7- Help");
        sender.sendMessage("§b/vortexconvert scan §7- Scan integrations");
        sender.sendMessage("§b/vortexconvert itemsadder §7- Scan ItemsAdder");
        sender.sendMessage(
                "§b/vortexconvert itemsadder convert §7- Build Bedrock resource pack"
        );
        sender.sendMessage("§b/vortexconvert report §7- Server report");
        sender.sendMessage("§b/vortexconvert reload §7- Reload config");
        sender.sendMessage(
                "§b/vortexconvert convert <file> [name] [geometry]"
        );
        sender.sendMessage(
                "§b/vortexconvert mappings generate §7- Generate Geyser mappings"
        );
        sender.sendMessage("§8--------------------------------");
    }

    private void handleItemsAdder(
            CommandSender sender,
            String[] args
    ) {
        if (args.length == 1
                || args[1].equalsIgnoreCase("scan")) {
            scanItemsAdder(sender);
            return;
        }

        if (args[1].equalsIgnoreCase("convert")) {
            convertItemsAdder(sender);
            return;
        }

        sender.sendMessage("§cUnknown ItemsAdder command.");
        sender.sendMessage("§7Use /vortexconvert itemsadder scan");
        sender.sendMessage("§7Use /vortexconvert itemsadder convert");
    }

    private void convertItemsAdder(CommandSender sender) {
        Plugin itemsAdder = Bukkit.getPluginManager()
                .getPlugin("ItemsAdder");

        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            sender.sendMessage(
                    "§cItemsAdder is not installed or enabled."
            );
            return;
        }

        File contents = new File(
                itemsAdder.getDataFolder(),
                "contents"
        );

        if (!contents.isDirectory()) {
            sender.sendMessage(
                    "§cItemsAdder contents folder not found:"
            );
            sender.sendMessage("§7" + contents.getPath());
            return;
        }

        File outputDirectory = plugin.getOutputFolder();

        if (outputDirectory == null) {
            sender.sendMessage("§cOutput directory is unavailable.");
            return;
        }

        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§b§lVortexConverter Bedrock");
        sender.sendMessage("§7Starting ItemsAdder conversion...");
        sender.sendMessage("§7Source: §f" + contents.getPath());
        sender.sendMessage("§7Output: §f" + outputDirectory.getPath());
        sender.sendMessage("§8--------------------------------");

        try {
            ConversionResult result =
                    bedrockConversionService.convert(
                            contents,
                            outputDirectory
                    );

            sender.sendMessage("§8--------------------------------");
            sender.sendMessage("§a§lBEDROCK PACK GENERATED");
            sender.sendMessage("§7YAML files read: §f"
                    + result.filesRead());
            sender.sendMessage("§7Total assets: §f"
                    + result.totalAssets());
            sender.sendMessage("§7Items discovered: §f"
                    + result.discoveredItems());
            sender.sendMessage("§7Textures copied: §a"
                    + result.convertedTextures());
            sender.sendMessage("§7Items skipped: §e"
                    + result.skippedItems());

            if (result.packFile() != null) {
                sender.sendMessage("§8--------------------------------");
                sender.sendMessage("§aResource pack created:");
                sender.sendMessage("§f" + result.packFile().getPath());
            }

            sendWarnings(sender, result.warnings());

            if (result.skippedDetails() != null
                    && !result.skippedDetails().isEmpty()) {
                sender.sendMessage("§8--------------------------------");
                sender.sendMessage("§e[SKIPPED ITEMS]");

                int shown = 0;

                for (String detail : result.skippedDetails()) {
                    if (shown >= 10) {
                        sender.sendMessage(
                                "§7...and more skipped items."
                        );
                        break;
                    }

                    sender.sendMessage("§e- " + detail);
                    shown++;
                }
            }

            sender.sendMessage("§8--------------------------------");

            if (result.isSuccessful()) {
                sender.sendMessage(
                        "§aResource pack generation completed."
                );
                sender.sendMessage(
                        "§7This pack currently contains converted textures "
                                + "and basic Bedrock pack metadata."
                );
                sender.sendMessage(
                        "§7Advanced model conversion is not included yet."
                );
            } else {
                sender.sendMessage(
                        "§cResource pack generation was unsuccessful."
                );
            }

            sender.sendMessage("§8--------------------------------");

        } catch (IOException exception) {
            sender.sendMessage("§cItemsAdder conversion failed:");
            sender.sendMessage("§7" + exception.getMessage());

            plugin.getLogger().severe(
                    "ItemsAdder Bedrock conversion failed: "
                            + exception.getMessage()
            );

        } catch (Exception exception) {
            sender.sendMessage(
                    "§cUnexpected conversion error. Check console."
            );

            plugin.getLogger().severe(
                    "Unexpected ItemsAdder Bedrock conversion error:"
            );

            exception.printStackTrace();
        }
    }

    private void handleMappings(
            CommandSender sender,
            String[] args
    ) {
        if (args.length < 2) {
            sendMappingsHelp(sender);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "help" -> sendMappingsHelp(sender);

            case "generate" -> generateItemsAdderMappings(sender);

            default -> {
                sender.sendMessage("§cUnknown mappings command.");
                sendMappingsHelp(sender);
            }
        }
    }

    private void sendMappingsHelp(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter Mappings");
        sender.sendMessage("§8--------------------------------");
        sender.sendMessage(
                "§b/vortexconvert mappings generate"
        );
        sender.sendMessage("§7Generate and export ItemsAdder mappings.");
        sender.sendMessage(
                "§b/vortexconvert mappings help"
        );
        sender.sendMessage("§7Show mapping commands.");
        sender.sendMessage("§8--------------------------------");
    }

    private void generateItemsAdderMappings(CommandSender sender) {
        Plugin itemsAdder = Bukkit.getPluginManager()
                .getPlugin("ItemsAdder");

        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            sender.sendMessage("§cItemsAdder is not installed or enabled.");
            return;
        }

        Plugin geyser = Bukkit.getPluginManager()
                .getPlugin("Geyser-Spigot");

        if (geyser == null || !geyser.isEnabled()) {
            geyser = Bukkit.getPluginManager().getPlugin("Geyser");
        }

        if (geyser == null || !geyser.isEnabled()) {
            sender.sendMessage("§cGeyser is not installed or enabled.");
            return;
        }

        File contents = new File(
                itemsAdder.getDataFolder(),
                "contents"
        );

        if (!contents.isDirectory()) {
            sender.sendMessage("§cItemsAdder contents folder not found:");
            sender.sendMessage("§7" + contents.getPath());
            return;
        }

        File geyserData = geyser.getDataFolder();

        if (geyserData == null) {
            sender.sendMessage("§cCannot locate Geyser data folder.");
            return;
        }

        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§b§lVortexConverter Mappings");
        sender.sendMessage("§7Reading ItemsAdder configurations...");
        sender.sendMessage("§7ItemsAdder: §f" + contents.getPath());
        sender.sendMessage("§7Geyser: §f" + geyserData.getPath());
        sender.sendMessage("§8--------------------------------");

        try {
            ItemsAdderMappingService.ServiceResult result =
                    mappingService.generateAndExport(
                            contents,
                            geyserData
                    );

            sender.sendMessage("§8--------------------------------");
            sender.sendMessage("§b§lMAPPING REPORT");
            sender.sendMessage("§7YAML files read: §f" + result.filesRead());
            sender.sendMessage("§7Total assets: §f" + result.totalAssets());
            sender.sendMessage("§7Items discovered: §f"
                    + result.discoveredItems());
            sender.sendMessage("§7Mappings exported: §a"
                    + result.exportedMappings());
            sender.sendMessage("§7Items skipped: §e"
                    + result.skippedItems());
            sender.sendMessage("§7Warnings: §e"
                    + result.warningCount());
            sender.sendMessage("§7Parse errors: §c"
                    + result.parseErrorCount());

            if (result.hasOutput()) {
                sender.sendMessage("§8--------------------------------");
                sender.sendMessage("§aMapping file created:");
                sender.sendMessage("§f" + result.outputFile().getPath());
            }

            if (!result.skippedDetails().isEmpty()) {
                sender.sendMessage("§8--------------------------------");
                sender.sendMessage("§e[SKIPPED ITEMS]");

                int shown = 0;

                for (String detail : result.skippedDetails()) {
                    if (shown >= 10) {
                        sender.sendMessage("§7...and more skipped items.");
                        break;
                    }

                    sender.sendMessage("§e- " + detail);
                    shown++;
                }
            }

            if (!result.warnings().isEmpty()) {
                sender.sendMessage("§8--------------------------------");
                sender.sendMessage("§e[WARNINGS]");

                int shown = 0;

                for (String warning : result.warnings()) {
                    if (shown >= 10) {
                        sender.sendMessage("§7...and more warnings.");
                        break;
                    }

                    sender.sendMessage("§e- " + warning);
                    shown++;
                }
            }

            if (!result.parseErrors().isEmpty()) {
                sender.sendMessage("§8--------------------------------");
                sender.sendMessage("§c[CONFIG ERRORS]");

                int shown = 0;

                for (String error : result.parseErrors()) {
                    if (shown >= 10) {
                        sender.sendMessage("§7...and more errors.");
                        break;
                    }

                    sender.sendMessage("§c- " + error);
                    shown++;
                }
            }

            if (result.successful()) {
                sender.sendMessage("§8--------------------------------");
                sender.sendMessage("§a§lMAPPING GENERATION COMPLETED");
                sender.sendMessage(
                        "§7Geyser may need a restart or reload "
                                + "to load the new mapping."
                );
            } else {
                sender.sendMessage("§8--------------------------------");
                sender.sendMessage("§cNo mappings were exported.");
                sender.sendMessage(
                        "§7Check the ItemsAdder material and "
                                + "custom_model_data settings."
                );
            }

            sender.sendMessage("§8--------------------------------");

        } catch (IOException exception) {
            sender.sendMessage("§cMapping generation failed:");
            sender.sendMessage("§7" + exception.getMessage());

            plugin.getLogger().severe(
                    "ItemsAdder mapping export failed: "
                            + exception.getMessage()
            );

        } catch (Exception exception) {
            sender.sendMessage("§cUnexpected mapping error.");
            sender.sendMessage("§7Check the server console.");

            plugin.getLogger().severe(
                    "Unexpected ItemsAdder mapping error:"
            );

            exception.printStackTrace();
        }
    }

    private void scanPlugins(CommandSender sender) {
        sender.sendMessage("§b§lIntegration Scanner");

        checkPlugin(sender, "ItemsAdder");
        checkPlugin(sender, "Oraxen");
        checkPlugin(sender, "Nexo");
        checkPlugin(sender, "ModelEngine");
        checkPlugin(sender, "Geyser-Spigot");
        checkPlugin(sender, "Geyser");

        sender.sendMessage("§aIntegration scan completed.");
    }

    private void checkPlugin(CommandSender sender, String name) {
        Plugin detected = Bukkit.getPluginManager().getPlugin(name);

        if (detected != null && detected.isEnabled()) {
            sender.sendMessage(
                    "§a[FOUND] §f" + name
                            + " §7v" + detected.getPluginMeta().getVersion()
            );
        } else {
            sender.sendMessage("§c[NOT FOUND] §f" + name);
        }
    }

    private void scanItemsAdder(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter");
        sender.sendMessage("§7ItemsAdder asset analysis started...");

        ItemsAdderScanner.ScanResult scan = scanner.scan();

        if (!scan.isPluginInstalled()) {
            sender.sendMessage("§cItemsAdder directory not found.");
            return;
        }

        if (!scan.isContentsFound()) {
            sender.sendMessage("§cItemsAdder contents directory not found.");
            return;
        }

        if (!scan.isScanSuccessful()) {
            sender.sendMessage("§cScanner error: " + scan.getError());
            return;
        }

        File contents = new File(
                plugin.getDataFolder().getParentFile(),
                "ItemsAdder/contents"
        );

        ItemsAdderConfigParser.ParseResult parsed =
                parser.parse(contents);

        ItemsAdderAssetResolver.ResolutionResult resolved =
                resolver.resolve(parsed, contents);

        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§aItemsAdder scan completed.");
        sender.sendMessage("§8--------------------------------");

        sender.sendMessage("§e[FILE ANALYSIS]");
        sender.sendMessage("§7Namespaces: §f" + scan.getNamespaceCount());
        sender.sendMessage("§7Total files: §f" + scan.getTotalFiles());
        sender.sendMessage("§7YAML files: §f" + scan.getConfigurations());
        sender.sendMessage("§7JSON files: §f" + scan.getJsonFiles());
        sender.sendMessage("§7PNG textures: §f" + scan.getTextures());
        sender.sendMessage("§7BBModels: §f" + scan.getBbmodels());
        sender.sendMessage("§7Total size: §f" + scan.getFormattedSize());

        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§e[CONFIG ANALYSIS]");
        sender.sendMessage("§7Parsed YAML: §f" + parsed.getFilesRead());
        sender.sendMessage("§7Custom items: §f"
                + parsed.count(ItemsAdderConfigParser.AssetType.ITEM));
        sender.sendMessage("§7Custom blocks: §f"
                + parsed.count(ItemsAdderConfigParser.AssetType.BLOCK));
        sender.sendMessage("§7Furniture: §f"
                + parsed.count(ItemsAdderConfigParser.AssetType.FURNITURE));
        sender.sendMessage("§7Entities: §f"
                + parsed.count(ItemsAdderConfigParser.AssetType.ENTITY));

        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§e[ASSET RESOLUTION]");
        sender.sendMessage("§7Assets analyzed: §f" + resolved.getTotalAssets());
        sender.sendMessage("§7Files indexed: §f" + resolved.getScannedFiles());
        sender.sendMessage("§7References resolved: §a"
                + resolved.getResolvedFiles());
        sender.sendMessage("§7Missing references: §c"
                + resolved.getMissingReferences());

        if (parsed.hasErrors()) {
            sender.sendMessage("§8--------------------------------");
            sender.sendMessage("§c[CONFIG ERRORS]");

            for (String error : parsed.getErrors()) {
                sender.sendMessage("§c- " + error);
            }
        }

        if (resolved.getMissingReferences() > 0) {
            sender.sendMessage("§8--------------------------------");
            sender.sendMessage("§e[WARNING] Missing references");

            int shown = 0;

            for (ItemsAdderAssetResolver.ResolvedAsset asset
                    : resolved.getAssets()) {

                for (String missing : asset.getMissingReferences()) {
                    if (shown >= 10) {
                        sender.sendMessage("§7...and more.");
                        break;
                    }

                    sender.sendMessage(
                            "§c" + asset.getAsset().getNamespace() + ":"
                                    + asset.getAsset().getId()
                                    + " -> " + missing
                    );

                    shown++;
                }

                if (shown >= 10) {
                    break;
                }
            }
        }

        sender.sendMessage("§8--------------------------------");
        sender.sendMessage(
                "§aAnalysis finished. No source files were modified."
        );
    }

    private void convertBlockbench(
            CommandSender sender,
            String[] args
    ) {
        if (args.length < 2 || args.length > 4) {
            sender.sendMessage("§cUsage:");
            sender.sendMessage(
                    "§e/vortexconvert convert <file.bbmodel> [pack-name] [geometry-id]"
            );
            return;
        }

        String fileName = args[1];

        File workspace = plugin.getWorkspaceFolder();
        File inputFile = new File(fileName);

        if (!inputFile.isAbsolute()) {
            inputFile = new File(workspace, fileName);
        }

        if (!inputFile.exists() || !inputFile.isFile()) {
            sender.sendMessage("§cBBModel file not found:");
            sender.sendMessage("§7" + inputFile.getPath());
            sender.sendMessage("§ePlace the file inside:");
            sender.sendMessage("§f" + workspace.getPath());
            return;
        }

        if (!inputFile.getName().toLowerCase(Locale.ROOT)
                .endsWith(".bbmodel")) {
            sender.sendMessage("§cOnly .bbmodel files are supported.");
            return;
        }

        String defaultName = inputFile.getName();
        defaultName = defaultName.substring(
                0,
                defaultName.length() - ".bbmodel".length()
        );

        String packName = args.length >= 3
                ? args[2]
                : defaultName;

        String geometryIdentifier = args.length >= 4
                ? args[3]
                : "geometry.vortex." + sanitizeIdentifier(defaultName);

        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§b§lVortexConverter");
        sender.sendMessage("§7Blockbench conversion started...");
        sender.sendMessage("§7Input: §f" + inputFile.getName());
        sender.sendMessage("§7Pack: §f" + packName);
        sender.sendMessage("§7Geometry: §f" + geometryIdentifier);
        sender.sendMessage("§8--------------------------------");

        try {
            BlockbenchPackPipeline.PipelineResult result =
                    blockbenchPipeline.convert(
                            inputFile,
                            plugin.getOutputFolder(),
                            packName,
                            geometryIdentifier
                    );

            sender.sendMessage("§8--------------------------------");
            sender.sendMessage("§a§lCONVERSION COMPLETED");
            sender.sendMessage("§a[VALID] Resource pack validation passed.");
            sender.sendMessage("§7Bones: §f" + result.boneCount());
            sender.sendMessage("§7Animations: §f" + result.animationCount());
            sender.sendMessage("§7UV cubes: §f" + result.uvCubeCount());
            sender.sendMessage("§7Textures copied: §f"
                    + result.copiedTextureCount());
            sender.sendMessage("§7Missing textures: §f"
                    + result.missingTextureCount());

            sendWarnings(sender, result.warnings());

            sender.sendMessage("§8--------------------------------");
            sender.sendMessage("§aMCpack created:");
            sender.sendMessage("§f" + result.mcpackFile().getPath());
            sender.sendMessage("§8--------------------------------");

        } catch (BlockbenchPackPipeline.ValidationException exception) {
            sender.sendMessage("§8--------------------------------");
            sender.sendMessage("§c§lCONVERSION REJECTED");
            sender.sendMessage("§c[INVALID] Resource pack validation failed.");
            sender.sendMessage("§7Errors: §c" + exception.getErrors().size());

            for (String error : exception.getErrors()) {
                sender.sendMessage("§c- " + error);
            }

            sendWarnings(sender, exception.getWarnings());

            sender.sendMessage("§cMCpack was not created.");
            sender.sendMessage("§8--------------------------------");

            plugin.getLogger().warning(
                    "Blockbench pack validation failed for " + packName
            );

        } catch (IOException exception) {
            sender.sendMessage("§cConversion failed:");
            sender.sendMessage("§7" + exception.getMessage());

            plugin.getLogger().severe(
                    "Blockbench conversion failed: "
                            + exception.getMessage()
            );

        } catch (Exception exception) {
            sender.sendMessage("§cAn unexpected conversion error occurred.");
            sender.sendMessage("§7Check the server console for details.");

            plugin.getLogger().severe(
                    "Unexpected Blockbench conversion error:"
            );

            exception.printStackTrace();
        }
    }

    private void sendWarnings(
            CommandSender sender,
            List<String> warnings
    ) {
        if (warnings == null || warnings.isEmpty()) {
            sender.sendMessage("§a[WARNINGS] No warnings.");
            return;
        }

        sender.sendMessage("§e[WARNINGS] " + warnings.size());

        int shown = 0;

        for (String warning : warnings) {
            if (shown >= 10) {
                sender.sendMessage("§7...and more warnings.");
                break;
            }

            sender.sendMessage("§e- " + warning);
            shown++;
        }
    }

    private String sanitizeIdentifier(String input) {
        String sanitized = input
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");

        if (sanitized.isBlank()) {
            return "model";
        }

        return sanitized;
    }

    private void sendReport(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter Server Report");
        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§7Server: §f" + Bukkit.getName());
        sender.sendMessage("§7Minecraft: §f" + Bukkit.getMinecraftVersion());
        sender.sendMessage("§7Bukkit API: §f" + Bukkit.getBukkitVersion());
        sender.sendMessage("§7Java: §f" + System.getProperty("java.version"));
        sender.sendMessage("§7Online players: §f"
                + Bukkit.getOnlinePlayers().size());
        sender.sendMessage("§7Workspace: §f"
                + plugin.getWorkspaceFolder().getPath());
        sender.sendMessage("§7Output: §f"
                + plugin.getOutputFolder().getPath());
        sender.sendMessage("§8--------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("vortexconverter.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(
                    args[0],
                    List.of(
                            "help",
                            "scan",
                            "itemsadder",
                            "report",
                            "reload",
                            "convert",
                            "mappings"
                    )
            );
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("itemsadder")) {
            return filter(
                    args[1],
                    List.of("scan", "convert")
            );
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("mappings")) {
            return filter(
                    args[1],
                    List.of("help", "generate")
            );
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("convert")) {

            File workspace = plugin.getWorkspaceFolder();
            File[] files = workspace.listFiles();

            if (files == null) {
                return List.of();
            }

            List<String> suggestions = new ArrayList<>();
            String input = args[1].toLowerCase(Locale.ROOT);

            for (File file : files) {
                if (file.isFile()
                        && file.getName().toLowerCase(Locale.ROOT)
                        .endsWith(".bbmodel")
                        && file.getName().toLowerCase(Locale.ROOT)
                        .startsWith(input)) {
                    suggestions.add(file.getName());
                }
            }

            return suggestions;
        }

        return List.of();
    }

    private List<String> filter(
            String input,
            List<String> options
    ) {
        String normalized = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String option : options) {
            if (option.startsWith(normalized)) {
                result.add(option);
            }
        }

        return result;
    }
}
