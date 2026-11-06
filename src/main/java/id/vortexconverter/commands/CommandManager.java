
package id.vortexconverter.commands;

import id.vortexconverter.VortexConverter;
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
import java.util.ArrayList;
import java.util.List;

public final class CommandManager implements CommandExecutor, TabCompleter {

    private final VortexConverter plugin;
    private final ItemsAdderScanner scanner;
    private final ItemsAdderConfigParser parser;
    private final ItemsAdderAssetResolver resolver;

    public CommandManager(VortexConverter plugin) {
        this.plugin = plugin;
        this.scanner = new ItemsAdderScanner(plugin);
        this.parser = new ItemsAdderConfigParser();
        this.resolver = new ItemsAdderAssetResolver();
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

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "scan" -> scanPlugins(sender);
            case "itemsadder" -> scanItemsAdder(sender);
            case "report" -> sendReport(sender);

            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aConfiguration reloaded.");
            }

            case "convert" -> sender.sendMessage(
                    "§eConversion engine is not implemented yet."
            );

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
        sender.sendMessage("§b/vortexconvert report §7- Server report");
        sender.sendMessage("§b/vortexconvert reload §7- Reload config");
        sender.sendMessage("§b/vortexconvert convert §7- Convert assets");
        sender.sendMessage("§8--------------------------------");
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
                    if (shown++ >= 10) {
                        sender.sendMessage("§7...and more.");
                        break;
                    }

                    sender.sendMessage(
                            "§c" + asset.getNamespace() + ":"
                                    + asset.getId() + " -> " + missing
                    );
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
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            for (String option : List.of(
                    "help",
                    "scan",
                    "itemsadder",
                    "report",
                    "reload",
                    "convert"
            )) {
                if (option.startsWith(input)) {
                    suggestions.add(option);
                }
            }

            return suggestions;
        }

        return List.of();
    }
}
