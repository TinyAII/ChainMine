package com.mcadmin.chainmine;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * 连锁破坏核心：BFS 洪泛，沿当前形状（+ / X 型）连锁同种类方块。
 *
 * 场景（每种场景可自定义方块、限定工具）：
 * - 挖矿 (ORE)：_ORE 后缀 + mining.blocks 自定义，镐
 * - 砍树 (WOOD/LEAF)：原木只连原木、树叶才连树叶
 * - 石头 (STONE)：stone.blocks 清单（默认开）
 * - 挖土 (DIRT)：digging.blocks，默认 need 铲子，可配徒手
 * 兼容 1.16：避免 1.17+ 才有的 MINEABLE_PICKAXE tag。
 */
public final class ChainBreaker implements Listener {

    private final ChainMinePlugin plugin;

    private static final int[][] PLUS = {
            {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1} };
    private static final int[][] X8 = {
            {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1},
            {1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},
            {1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},
            {0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1},
            {1,1,1},{-1,1,1},{1,-1,1},{-1,-1,1},{1,1,-1},{-1,1,-1},{1,-1,-1},{-1,-1,-1} };

    private static final String[] SHOVELS = {"SHOVEL"};
    private static final String[] PICKAXES = {"PICKAXE"};
    private static final String[] AXES = {"AXE"};

    public ChainBreaker(ChainMinePlugin plugin) { this.plugin = plugin; }

    public void reload() { }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        if (!plugin.enabled) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("chainmine.use")) return;
        // per-player 开关（含全局 trigger-mode 关）
        if (!plugin.isPlayerEnabled(p)) return;
        Block origin = e.getBlock();

        // 判断场景 + 工具要求
        Scene scene = classify(origin);
        if (scene == null) return;
        if (!isSceneEnabled(scene)) return;
        // 工具限定检查
        if (!toolOk(p, scene)) return;

        int[][] dirs = "x".equalsIgnoreCase(plugin.shape) ? X8 : PLUS;
        int max = plugin.maxBlocks;
        Queue<Block> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        for (int[] d : dirs) {
            Block nb = origin.getRelative(d[0], d[1], d[2]);
            if (scene.matches(nb) && visited.add(nb)) queue.add(nb);
        }
        visited.add(origin);
        java.util.List<Block> toBreak = new java.util.ArrayList<>();
        while (!queue.isEmpty() && toBreak.size() < max) {
            Block b = queue.poll();
            if (!scene.matches(b)) continue;
            toBreak.add(b);
            if (toBreak.size() >= max) break;
            for (int[] d : dirs) {
                Block nb = b.getRelative(d[0], d[1], d[2]);
                if (visited.add(nb) && scene.matches(nb)) queue.add(nb);
            }
        }
        if (toBreak.isEmpty()) return;
        breakChain(p, toBreak, origin);
    }

    private void breakChain(Player p, java.util.List<Block> blocks, Block origin) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean merge = plugin.mergeDrops;
        org.bukkit.Location dropAt = origin.getLocation().add(0.5, 0.5, 0.5);
        java.util.List<ItemStack> merged = new java.util.ArrayList<>();
        for (Block b : blocks) {
            if (merge) {
                for (ItemStack it : b.getDrops(hand)) merged.add(it);
                b.setType(Material.AIR, false);
            } else {
                b.breakNaturally(hand);
            }
        }
        if (merge) {
            org.bukkit.World w = origin.getWorld();
            if (w != null) for (ItemStack it : merged) w.dropItemNaturally(dropAt, it);
        }
        if (plugin.consumeDurability && !blocks.isEmpty()) {
            ItemMeta im = hand.getItemMeta();
            if (im instanceof Damageable d) {
                d.setDamage(d.getDamage() + blocks.size());
                hand.setItemMeta(im);
                p.getInventory().setItemInMainHand(hand);
            }
        }
    }

    // ---------- 场景识别 ----------

    private Scene classify(Block b) {
        Material m = b.getType();
        String name = m.name();
        // 矿石
        if (name.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS || m == Material.OBSIDIAN
                || matchSet(name, plugin.miningBlocks)) {
            if (plugin.miningOn) return Scene.ore(m);
        }
        // 石头（默认清单 + 自定义；深板岩等靠 config 的 stone.blocks 匹配，避免 1.16 无 DEEPSLATE）
        if (matchSet(name, plugin.stoneBlocks) || (m == Material.STONE || m == Material.COBBLESTONE)) {
            if (plugin.stoneOn) return Scene.stone(m);
        }
        // 原木
        if (Tag.LOGS.isTagged(m)) {
            if (plugin.fellingOn) return Scene.log(m);
        }
        // 树叶
        if (Tag.LEAVES.isTagged(m)) {
            if (plugin.fellingOn) return Scene.leaf(m);
        }
        // 土
        if (matchSet(name, plugin.diggingBlocks)) {
            if (plugin.diggingOn) {
                Scene s = Scene.dirt(m);
                s.requiresShovel = plugin.diggingRequireTool;
                return s;
            }
        }
        return null;
    }

    private static boolean matchSet(String name, java.util.Set<String> set) {
        return set != null && set.contains(name);
    }

    private boolean isSceneEnabled(Scene s) {
        switch (s.kind) {
            case ORE: return plugin.miningOn;
            case WOOD:
            case LEAF: return plugin.fellingOn;
            case STONE: return plugin.stoneOn;
            default: return plugin.diggingOn;
        }
    }

    /** 工具检查（按场景 kind）：矿石/石头要镐，原木/树叶要斧，泥土要铲子（若 requireShovel），其它自定义靠 blocks 判。 */
    private boolean toolOk(Player p, Scene s) {
        Material hand = p.getInventory().getItemInMainHand().getType();
        String hn = hand.name();
        switch (s.kind) {
            case ORE:
            case STONE:
                return nameEndsWith(hn, PICKAXES);
            case WOOD:
            case LEAF:
                return nameEndsWith(hn, AXES);
            case DIRT:
                if (s.requiresShovel) return nameEndsWith(hn, SHOVELS);
                return true; // 徒手/任意工具都行
            default:
                return true;
        }
    }

    private static boolean nameEndsWith(String name, String[] suffixes) {
        for (String sfx : suffixes) if (name.endsWith(sfx)) return true;
        return false;
    }

    enum Kind { ORE, WOOD, LEAF, STONE, DIRT }

    static final class Scene {
        final Material type;
        final Kind kind;
        boolean requiresShovel = false;
        Scene(Material t, Kind k) { this.type = t; this.kind = k; }
        boolean matches(Block b) { return b.getType() == type; }

        static Scene ore(Material m) { return new Scene(m, Kind.ORE); }
        static Scene log(Material m) { return new Scene(m, Kind.WOOD); }
        static Scene leaf(Material m) { return new Scene(m, Kind.LEAF); }
        static Scene stone(Material m) { return new Scene(m, Kind.STONE); }
        static Scene dirt(Material m) { return new Scene(m, Kind.DIRT); }
    }
}
