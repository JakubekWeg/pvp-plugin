package jwegrzyn.pvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("unused")
public final class Pvp extends JavaPlugin {

    private static final String CONFIG_FILE_NAME = "pvp-profiles";
    private final Map<String, Kit> kits = new HashMap<>();
    private final Map<String, String> playersPreferences = new HashMap<>();

    private static class Kit implements ConfigurationSerializable {

        private final List<ItemStack> items;

        private Kit(List<ItemStack> items) {
            this.items = items;
        }

        public static Kit fromInventory(@NotNull PlayerInventory inventory) {
            int size = inventory.getSize();
            ArrayList<ItemStack> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ItemStack item = inventory.getItem(size);
                if (item != null)
                    items.add(item.clone());
                else
                    items.add(null);
            }
            return new Kit(items);
        }

        public void applyToInventory(@NotNull PlayerInventory inventory) {
            for (int i = 0, size = items.size(); i < size; i++) {
                inventory.setItem(i, this.items.get(i).clone());
            }
        }

        @Override
        public @NotNull Map<String, Object> serialize() {
            HashMap<String, Object> map = new HashMap<>();
            map.put("items", items);
            return map;
        }

        @NotNull
        public static Kit deserialize(@NotNull Map<String, Object> args) {
            //noinspection unchecked
            List<ItemStack> items = ((List<ItemStack>) args.get("items"));
            return new Kit(items);
        }
    }

    @Override
    public void onEnable() {
        try {
            FileConfiguration config = getConfig();
            config.load(CONFIG_FILE_NAME);
            final ConfigurationSection kitsSection = config.getConfigurationSection("kits");
            if (kitsSection != null) {
                for (String key : kitsSection.getKeys(false)) {
                    this.kits.put(key, (Kit) kitsSection.get(key));
                }
            }
            final ConfigurationSection preferencesSection = config.getConfigurationSection("preferences");
            if (preferencesSection != null) {
                for (String key : preferencesSection.getKeys(false)) {
                    this.playersPreferences.put(key, preferencesSection.getString(key, null));
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        if ("pvp-kit".equals(command.getName())) {
            switch (args.length) {
                case 0:
                case 1:
                    return Arrays.asList("use", "add", "remove", "list");
                case 2:
                    if ("use".equals(args[0]) || "remove".equals(args[0]))
                        return new ArrayList<>(kits.keySet());
                    else
                        return Collections.emptyList();
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if ("pvp-kit".equals(command.getName())) {
            switch (args.length) {
                case 0:
                    sender.sendMessage(ChatColor.RED + "Missing action word");
                    return false;
                default:
                    sender.sendMessage(ChatColor.RED + "Too many arguments");
                    return false;
                case 1:
                    switch (args[0]) {
                        case "use":
                        case "add":
                        case "remove":
                            sender.sendMessage(ChatColor.RED + "Missing kit name");
                            return false;
                        case "list":
                            final Collection<String> values = kits.keySet();
                            if (values.isEmpty())
                                sender.sendMessage(ChatColor.BLUE + "There are no defined kits");
                            else {
                                sender.sendMessage(ChatColor.AQUA + "There are kits:");
                                for (String value : values) {
                                    sender.sendMessage(ChatColor.GREEN + " " + value);
                                }
                            }
                            return true;
                        default:
                            sender.sendMessage(ChatColor.RED + "Unknown action");
                            return false;
                    }
                case 2:
                    switch (args[0]) {
                        case "use": {
                            final Kit kit = kits.get(args[1]);
                            if (kit == null) {
                                sender.sendMessage(ChatColor.RED + "Kit with this name doesn't exist");
                                return false;
                            }
                            this.playersPreferences.put(sender.getName(), args[1]);
                            sender.sendMessage(ChatColor.GREEN + "Kit assigned");
                            return true;
                        }
                        case "add": {
                            if (kits.containsKey(args[1])) {
                                sender.sendMessage(ChatColor.RED + "Kit with this name already exists");
                                return false;
                            }
                            if (!(sender instanceof InventoryHolder)) {
                                sender.sendMessage(ChatColor.RED + "You doesn't contain inventory");
                                return false;
                            }
                            final Inventory inventory = ((InventoryHolder) sender).getInventory();
                            if (!(inventory instanceof PlayerInventory)) {
                                sender.sendMessage(ChatColor.RED + "You must have player inventory");
                                return false;
                            }
                            final Kit kit = Kit.fromInventory(((PlayerInventory) inventory));
                            this.kits.put(args[1], kit);
                            sender.sendMessage(ChatColor.GREEN + "Kit added");
                            this.playersPreferences.put(sender.getName(), args[1]);
                            return true;
                        }
                        case "remove":
                            if (!kits.containsKey(args[1])) {
                                sender.sendMessage(ChatColor.RED + "Kit with this name doesn't exist");
                                return false;
                            }
                            kits.remove(args[1]);
                            sender.sendMessage(ChatColor.GREEN + "Kit removed");
                            return true;
                        case "list":
                            sender.sendMessage(ChatColor.RED + "Too many arguments");
                            return false;
                        default:
                            sender.sendMessage(ChatColor.RED + "Unknown action");
                            return false;
                    }
            }
        }
        return false;
    }

    private void applyToPlayer(@Nullable Player player,
                               @Nullable String kitName) {
        if (player == null) return;
        if (kitName == null) return;
        final Kit kit = this.kits.get(kitName);
        if (kit == null) return;
        final PlayerInventory inventory = player.getInventory();
        inventory.clear();
        kit.applyToInventory(inventory);
    }

    @Override
    public void onDisable() {
        try {
            getConfig().save(CONFIG_FILE_NAME);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
