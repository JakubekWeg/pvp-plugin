package jwegrzyn.pvp;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

@SuppressWarnings("unused")
public final class Pvp extends JavaPlugin {

    private static final String CONFIG_FILE_NAME = "pvp-profiles";

    @Override
    public void onEnable() {
        try {
            getConfig().load(CONFIG_FILE_NAME);
        } catch (Exception e) {
            // ignore
        }
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
