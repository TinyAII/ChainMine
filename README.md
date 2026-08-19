# ChainMine 连锁采集

> 连锁挖矿 / 砍树 / 石头 / 泥土，一挖一大片。零依赖，Paper/Spigot 1.16~26.2 通吃。

镐挖一颗矿石，连着附近同种矿一起挖掉；斧砍一段木头，整棵连树带叶搞定；锹挖一块泥土，一片土沙全带走。支持 X 型 + 加号型连锁，上限、掉落、耐久、工具全都可配。

---

## 功能

| 功能 | 说明 |
| --- | --- |
| ⛏️ 连锁挖矿 | 镐挖 `_ORE` 矿石，连锁同种矿 |
| 🪓 连锁砍树 | 砍原木只连原木、砍树叶只连树叶，各连各的 |
| 🧱 连锁石头 | 石头/圆石/深板岩/花岗岩等（默认开，可关，可自定义） |
| 🏜️ 连锁挖土 | 泥土/沙砾/沙子等，默认需铲子，可配徒手 |
| ➕✖️ 连锁形状 | `plus`（上下左右/十字）或 `x`（含对角 8 方向），可配 |
| 🔢 连锁上限 | 默认 16 格，可调 |
| 💰 掉落物合并 | 默认关（各掉各），可开（集中到一个点） |
| ⚒️ 耐久扣减 | 每连锁挖一格扣一格耐久（默认） |
| 🎮 玩家开关 | 非 OP 也能 `/连锁采集 开` / `关` |
| 🔧 可自定义 | 每场景可加要连锁的方块、限定工具 |

**核心特点：纯 Bukkit API、零 NMS、零依赖，用最老 API 编译，老服新服全兼容（1.16 ~ 26.2 实测可加载）。**

---

## 安装

1. 下载 `chainmine-1.1.0.jar`
2. 放入服务器 `plugins/` 目录
3. 重启服务器（或 `/reload`）

启动后控制台出现 TinyAII 像素字横幅 + `连锁上限 16 | 形状 plus...` 即加载成功。

## 命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/连锁采集 开`（`on`） | 所有玩家 | 开启自己的连锁 |
| `/连锁采集 关`（`off`） | 所有玩家 | 关闭自己的连锁（变普通单挖） |
| `/连锁采集 重载`（`reload`） | 管理员 | 热重载 config.yml |
| `/连锁采集` | 所有玩家 | 查看状态/帮助 |

> 别名 Alias：`/连锁采集`、`/连锁`、`/cm`、`/chainmine`

## 配置

`plugins/ChainMine/config.yml`，全部可调：

```yaml
enabled: true
max-blocks: 16            # 一次连锁最多方块数
shape: plus               # plus / x / both（连锁形状）

mining:
  enabled: true
  blocks: []              # 额外矿石
  tools: []               # 空不限；[pickaxe] 限镐
felling:
  enabled: true
  logs: true
  leaves: true
stone:
  enabled: true
  blocks: [stone, cobblestone, deepslate, granite, diorite, andesite, tuff, calcite]  # 可加
  tools: []
digging:
  enabled: true
  require-tool: true      # true=必须铲子；false=徒手也行
  blocks: [dirt, grass_block, sand, gravel, clay, ...]
```

## 兼容

- Paper / Spigot / Purpur / Leaves：**Minecraft 1.16 ~ 26.2**（26.2 实测加载成功）
- Java 17+（26.x 需 Java 25+）
- 零依赖（无前置插件）

---

# ChainMine (English)

Chain-mine ores, fell trees, dig stone & dirt all at once. Zero dependencies, works on Paper/Spigot 1.16~26.2.

## Features
- Chain ore mining (pickaxe), tree felling (axe: logs-only / leaves-only), stone mining, dirt digging
- `plus` / `x` chain shapes
- Configurable chain limit (default 16), drop merging (default off), durability cost (default on)
- Per-player on/off command (non-OP usable)
- Customizable blocks & tools per scene
- Vacant Bukkit API, works across versions (1.16 ~ 26.2 tested)

## Commands
`/连锁采集 开|关` (all players) · `/连锁采集 重载` (admin)

## Compatibility
- Paper / Spigot / Purpur / Leaves 1.16 ~ 26.2
- Java 17+ (26.x needs Java 25+)
- Zero dependencies

## Author
TinyAII · 免费但闭源 · 零依赖
