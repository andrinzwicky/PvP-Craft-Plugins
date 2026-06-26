package com.pvpcraft.baseprotect.manager;

import com.pvpcraft.baseprotect.model.Base;
import com.pvpcraft.baseprotect.model.Membership;
import com.pvpcraft.baseprotect.model.Role;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Loads, stores and queries bases. The role hierarchy (owner/leader/member +
 * join timestamps) is persisted to {@code bases.yml} and survives restarts; the
 * region geometry itself lives in WorldGuard.
 *
 * <p>This class never talks to WorldGuard. Callers mutate a {@link Base} through
 * these methods and then mirror the result into the region via the
 * WorldGuardService.
 */
public class BaseManager {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Base> bases = new HashMap<>();

    public BaseManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bases.yml");
    }

    // --- CRUD ------------------------------------------------------------

    public Base getBase(String id) {
        return id == null ? null : bases.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return id != null && bases.containsKey(id.toLowerCase());
    }

    public java.util.Collection<Base> getBases() {
        return bases.values();
    }

    public void addBase(Base base) {
        bases.put(base.getId().toLowerCase(), base);
        save();
    }

    public Base removeBase(String id) {
        Base removed = bases.remove(id.toLowerCase());
        if (removed != null) {
            save();
        }
        return removed;
    }

    // --- owner removal / succession --------------------------------------

    /**
     * Outcome of {@link #removeAndPromote}: who (if anyone) moved up to owner.
     */
    public static final class Succession {
        public final UUID newOwner;   // null → base is now ownerless (or removed member wasn't owner)
        public final boolean wasOwner; // whether the removed player had been the owner

        Succession(UUID newOwner, boolean wasOwner) {
            this.newOwner = newOwner;
            this.wasOwner = wasOwner;
        }
    }

    /**
     * Remove {@code uuid} from the base. If the removed player was the owner,
     * apply the succession rule (Regel A): the oldest leader moves up to owner;
     * if there is no leader, the oldest plain member moves up; if nobody is left
     * the base becomes ownerless. Persists the change.
     */
    public Succession removeAndPromote(Base base, UUID uuid) {
        boolean wasOwner = base.isOwner(uuid);
        base.remove(uuid);

        UUID newOwner = null;
        if (wasOwner) {
            List<UUID> leaders = base.getLeaders();      // oldest first
            if (!leaders.isEmpty()) {
                newOwner = leaders.get(0);
            } else {
                List<UUID> members = base.getPlainMembers(); // oldest first
                if (!members.isEmpty()) {
                    newOwner = members.get(0);
                }
            }
            if (newOwner != null) {
                base.setRole(newOwner, Role.OWNER);
            }
        }
        save();
        return new Succession(newOwner, wasOwner);
    }

    // --- persistence -----------------------------------------------------

    public void load() {
        bases.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("bases");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            try {
                String world = sec.getString("world", "world");
                Base base = new Base(id, world);
                ConfigurationSection memberSec = sec.getConfigurationSection("members");
                if (memberSec != null) {
                    for (String uuidStr : memberSec.getKeys(false)) {
                        ConfigurationSection m = memberSec.getConfigurationSection(uuidStr);
                        if (m == null) {
                            continue;
                        }
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            Role role = Role.valueOf(m.getString("role", "MEMBER").toUpperCase());
                            long joined = m.getLong("joined", System.currentTimeMillis());
                            base.put(uuid, role, joined);
                        } catch (IllegalArgumentException ignored) {
                            // bad UUID or unknown role → skip that entry
                        }
                    }
                }
                bases.put(id.toLowerCase(), base);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid base '" + id + "'", ex);
            }
        }
        plugin.getLogger().info("Loaded " + bases.size() + " base(s).");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Base base : bases.values()) {
            String root = "bases." + base.getId();
            cfg.set(root + ".world", base.getWorld());
            for (Map.Entry<UUID, Membership> e : base.getMembers().entrySet()) {
                String mPath = root + ".members." + e.getKey();
                cfg.set(mPath + ".role", e.getValue().getRole().name());
                cfg.set(mPath + ".joined", e.getValue().getJoinedAt());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save bases.yml", ex);
        }
    }
}
