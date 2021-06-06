package jwegrzyn.pvp;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unused")
public final class Pvp extends JavaPlugin implements @NotNull Listener {

    private static final String CONFIG_FILE_NAME = "pvp-profiles";
    private final Map<String, Kit> kits = new HashMap<>();
    private final Map<String, String> playersPreferences = new HashMap<>();
    private Team pvpTeam;

    public static class Kit implements ConfigurationSerializable {

        private final List<ItemStack> items;

        private Kit(List<ItemStack> items) {
            this.items = items;
        }

        public static Kit fromInventory(@NotNull PlayerInventory inventory) {
            int size = inventory.getSize();
            ArrayList<ItemStack> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null)
                    items.add(item.clone());
                else
                    items.add(null);
            }
            return new Kit(items);
        }

        public void applyToInventory(@NotNull PlayerInventory inventory) {
            inventory.clear();
            for (int i = 0, size = items.size(); i < size; i++) {
                final ItemStack stack = this.items.get(i);
                if (stack != null)
                    inventory.setItem(i, stack.clone());
            }
        }

        @Override
        public @NotNull Map<String, Object> serialize() {
            HashMap<String, Object> map = new HashMap<>();
            map.put("items", this.items);
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
        final Scoreboard mainScoreboard = Objects
                .requireNonNull(getServer().getScoreboardManager())
                .getMainScoreboard();

        pvpTeam = mainScoreboard.getTeam("pvp");
        if (pvpTeam == null)
            pvpTeam = mainScoreboard.registerNewTeam("pvp");
        pvpTeam.setColor(ChatColor.DARK_RED);
        pvpTeam.setPrefix("pvp ");

        ConfigurationSerialization.registerClass(Kit.class);
        this.getServer().getPluginManager().registerEvents(this, this);

        try {
            FileConfiguration config = getConfig();
            config.load(CONFIG_FILE_NAME);
            final ConfigurationSection kitsSection = config.getConfigurationSection("kits");
            if (kitsSection != null) {
                for (String key : kitsSection.getKeys(false)) {
                    this.kits.put(key, kitsSection.getObject(key, Kit.class));
                }
            }
            final ConfigurationSection preferencesSection = config.getConfigurationSection("preferences");
            if (preferencesSection != null) {
                for (String key : preferencesSection.getKeys(false)) {
                    this.playersPreferences.put(key, preferencesSection.getString(key, null));
                }
            }
        } catch (FileNotFoundException e) {
            // ignore
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void eventHandler(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        if (!pvpTeam.hasEntry(player.getName())) return;
        final Location location = player.getLocation();
        player.getWorld().strikeLightningEffect(location);
        player.setGameMode(GameMode.SPECTATOR);
        player.setBedSpawnLocation(location.add(0, 1, 0), true);
        event.setDroppedExp(0);
        event.getDrops().clear();

        final Collection<? extends Player> players = getServer().getOnlinePlayers();
        if (players.size() == 2) {
            // heal other player
            for (Player other : players) {
                if (other != player && pvpTeam.hasEntry(other.getName())) {
                    other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 5, true, false, false));
                    other.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 5, true, false, false));
                    other.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, true, false, false));
                    final double health = other.getHealth();
                    getServer().broadcastMessage(String.format("%s was on %s%.2f%s hp", other.getDisplayName(), ChatColor.DARK_PURPLE, health, ChatColor.RESET));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void eventHandler(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        if (!pvpTeam.hasEntry(player.getName())) return;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            getServer().getScheduler().runTaskLater(this, () -> {
                final Location location = player.getBedSpawnLocation();
                if (location != null)
                    player.teleport(location);
                player.setGameMode(GameMode.SURVIVAL);
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20, 1, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 255, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 1, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 60, 10, true, false, false));
            }, 5 * 20);
        }
        this.applyToPlayer(player, playersPreferences.get(player.getName()));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        if (!pvpTeam.hasEntry(sender.getName()))
            return null;

        if ("select-kit".equals(command.getName())) {
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
        if (!pvpTeam.hasEntry(sender.getName())) {
            sender.sendMessage(ChatColor.RED + "You are not a member of " + ChatColor.DARK_PURPLE + "pvp" + ChatColor.RED + " team!");
            if (sender.isOp()) {
                sender.sendMessage("You can add self by using " + ChatColor.DARK_PURPLE + "/team join pvp @s");
            } else {
                sender.sendMessage("Ask a server operator to make you a member");
            }
            return true;
        }

        if ("select-kit".equals(command.getName())) {
            switch (args.length) {
                case 0:
                    sender.sendMessage(ChatColor.RED + "Missing action word");
                    return true;
                default:
                    sender.sendMessage(ChatColor.RED + "Too many arguments");
                    return true;
                case 1:
                    switch (args[0]) {
                        case "use":
                        case "add":
                        case "remove":
                            sender.sendMessage(ChatColor.RED + "Missing kit name");
                            return true;
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
                            return true;
                    }
                case 2:
                    switch (args[0]) {
                        case "use": {
                            final Kit kit = kits.get(args[1]);
                            if (kit == null) {
                                sender.sendMessage(ChatColor.RED + "Kit with this name doesn't exist");
                                return true;
                            }
                            this.playersPreferences.put(sender.getName(), args[1]);
                            if (sender instanceof Player)
                                applyToPlayer(((Player) sender), args[1]);
                            sender.sendMessage(ChatColor.GREEN + "Kit assigned");
                            return true;
                        }
                        case "add": {
                            if (kits.containsKey(args[1])) {
                                sender.sendMessage(ChatColor.RED + "Kit with this name already exists");
                                return true;
                            }
                            if (!(sender instanceof InventoryHolder)) {
                                sender.sendMessage(ChatColor.RED + "You doesn't contain inventory");
                                return true;
                            }
                            final Inventory inventory = ((InventoryHolder) sender).getInventory();
                            if (!(inventory instanceof PlayerInventory)) {
                                sender.sendMessage(ChatColor.RED + "You must have player inventory");
                                return true;
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
                                return true;
                            }
                            kits.remove(args[1]);
                            sender.sendMessage(ChatColor.GREEN + "Kit removed");
                            return true;
                        case "list":
                            sender.sendMessage(ChatColor.RED + "Too many arguments");
                            return true;
                        default:
                            sender.sendMessage(ChatColor.RED + "Unknown action");
                            return true;
                    }
            }
        }
        return false;
    }

    private void applyToPlayer(@Nullable Player player,
                               @Nullable String kitName) {
        if (player == null) return;
        if (kitName == null) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Please select your kit using /pvp-kit use <name>");
            return;
        }
        final Kit kit = this.kits.get(kitName);
        if (kit == null) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Your kit cannot be found, use a different one");
            return;
        }
        final PlayerInventory inventory = player.getInventory();
        kit.applyToInventory(inventory);
    }

    @Override
    public void onDisable() {
        try {
            final FileConfiguration config = getConfig();
            config.createSection("kits", this.kits);
            config.createSection("preferences", this.playersPreferences);
            config.save(CONFIG_FILE_NAME);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
