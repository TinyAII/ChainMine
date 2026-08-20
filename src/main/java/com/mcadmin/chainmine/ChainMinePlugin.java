package com.mcadmin.chainmine;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ChainMine 连锁采集插件 v1.1
 *
 * 连锁挖矿 / 砍树 / 挖土 / 石头。X+型扩散、上限可配、掉落合并可关、耐久正常扣。
 * 砍树：砍原木只连原木、砍树叶才连树叶。泥土默认需铲子（可配徒手）。
 * 支持自定义方块/工具；玩家可 /连锁采集 开启|关闭；管理员可热重载。
 * 纯 Bukkit API，多版本（1.16+ 实测 26.2 可加载）。
 */
public final class ChainMinePlugin extends JavaPlugin {

    private ChainBreaker breaker;

    // 全局配置缓存
    volatile boolean enabled = true;
    volatile int maxBlocks = 16;
    volatile String shape = "plus";
    volatile boolean mergeDrops = false;
    volatile boolean consumeDurability = true;
    volatile String triggerMode = "normal";

    // 场景开关
    volatile boolean miningOn = true;
    volatile boolean fellingOn = true;
    volatile boolean stoneOn = true;
    volatile boolean diggingOn = true;
    volatile boolean diggingRequireTool = true;

    // 自定义方块集合（每场景）
    final Set<String> miningBlocks = new HashSet<>();
    final Set<String> stoneBlocks = new HashSet<>();
    final Set<String> diggingBlocks = new HashSet<>();

    // 玩家手动开关（存 UUID -> enabled）。空集合 = 未设置过（用全局 trigger-mode）。
    private final Set<UUID> playerDisabled = new HashSet<>();

    public ChainBreaker getBreaker() { return breaker; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        breaker = new ChainBreaker(this);
        getServer().getPluginManager().registerEvents(breaker, this);
        getCommand("chainmine").setExecutor(this);

        // TinyAII 品牌横幅
        String b = """
                 _____ _                _    ___ ___
                |_   _(_)_ __  _   _   / \\  |_ _|_ _|
                  | | | | '_ \\| | | | / _ \\  | | | |
                  | | | | | | | | |_| |/ ___ \\ | | | |
                  |_| |_|_| |_|\\__, /_/   \\_\\___|___|
                               |___/
                """;
        b.lines().forEach(l -> getLogger().info(l));
        getLogger().info("ChainMine 连锁采集 v" + getDescription().getVersion() + " - TinyAII 出品");
        getLogger().info("连锁上限 " + maxBlocks + " | 形状 " + shape + " | 掉落合并 " + mergeDrops + " | 扣耐久 " + consumeDurability);
    }

    /** 重载配置为当前 config.yml 值。 */
    public void loadConfig() {
        reloadConfig();
        FileConfiguration c = getConfig();
        enabled = c.getBoolean("enabled", true);
        maxBlocks = Math.max(1, c.getInt("max-blocks", 16));
        shape = c.getString("shape", "plus");
        mergeDrops = c.getBoolean("merge-drops", false);
        consumeDurability = c.getBoolean("consume-durability", true);
        triggerMode = c.getString("trigger-mode", "normal");

        miningOn = sec(c, "mining").getBoolean("enabled", true);
        fellingOn = sec(c, "felling").getBoolean("enabled", true);
        stoneOn = sec(c, "stone").getBoolean("enabled", true);
        diggingOn = sec(c, "digging").getBoolean("enabled", true);
        diggingRequireTool = sec(c, "digging").getBoolean("require-tool", true);

        fillStrings(sec(c, "mining").getStringList("blocks"), miningBlocks);
        fillStrings(sec(c, "stone").getStringList("blocks"), stoneBlocks);
        fillStrings(sec(c, "digging").getStringList("blocks"), diggingBlocks);
    }

    private static ConfigurationSection sec(FileConfiguration c, String key) {
        ConfigurationSection s = c.getConfigurationSection(key);
        return s != null ? s : c.createSection(key);
    }

    private static void fillStrings(List<String> list, Set<String> target) {
        target.clear();
        if (list == null) return;
        for (String s : list) if (s != null) target.add(s.toUpperCase().replace(" ", ""));
    }

    /** 玩家是否开启了连锁（考虑全局触发模式 + 玩家手动开关）。 */
    public boolean isPlayerEnabled(Player p) {
        if (playerDisabled.contains(p.getUniqueId())) return false;
        if ("never".equalsIgnoreCase(triggerMode)) return false; // 全局默认关
        return true;
    }

    public void setPlayerEnabled(Player p, boolean on) {
        if (on) playerDisabled.remove(p.getUniqueId());
        else playerDisabled.add(p.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // /连锁采集 开|开启|on / 关|关闭|off  —— 非 OP 也能用（per-player）
        if (args.length > 0 && (args[0].equalsIgnoreCase("开") || args[0].equalsIgnoreCase("开启")
                || args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("关")
                || args[0].equalsIgnoreCase("关闭") || args[0].equalsIgnoreCase("off"))) {
            if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "只有玩家能用该功能。"); return true; }
            Player p = (Player) sender;
            boolean on = args[0].equalsIgnoreCase("开") || args[0].equalsIgnoreCase("开启") || args[0].equalsIgnoreCase("on");
            setPlayerEnabled(p, on);
            p.sendMessage(on
                    ? ChatColor.GREEN + "✔ 连锁采集已开启"
                    : ChatColor.YELLOW + "⏸ 连锁采集已关闭（变成普通单挖）");
            return true;
        }
        // /连锁采集 重载|reload —— 管理员
        if (args.length > 0 && (args[0].equalsIgnoreCase("重载") || args[0].equalsIgnoreCase("reload"))) {
            if (!sender.hasPermission("chainmine.admin")) { sender.sendMessage(ChatColor.RED + "你没有权限。"); return true; }
            loadConfig();
            if (breaker != null) breaker.reload();
            sender.sendMessage(ChatColor.GREEN + "✔ 连锁采集配置已重载。（上限 " + maxBlocks + "，形状 " + shape + "，石头 " + (stoneOn?"开":"关") + "，泥土需铲子 " + diggingRequireTool + "）");
            return true;
        }
        // 帮助
        sender.sendMessage(ChatColor.GOLD + "===== 连锁采集 ChainMine =====");
        sender.sendMessage(ChatColor.YELLOW + "/连锁采集 开|关" + ChatColor.WHITE + "  开启/关闭连锁（所有玩家可用）");
        if (sender.hasPermission("chainmine.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/连锁采集 重载" + ChatColor.WHITE + "  重载配置（管理员）");
        }
        sender.sendMessage(ChatColor.GRAY + "上限 " + maxBlocks + " | 形状 " + shape + " | 石头 " + (stoneOn?"开":"关") + " | 泥土需铲子 " + diggingRequireTool);
        return true;
    }
}
