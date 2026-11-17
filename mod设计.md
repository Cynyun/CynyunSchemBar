# Blueprint Bar Mod 设计文档

> 版本：v1.0（设计稿，不含实现代码）
> 目标游戏：Mindustry 160.7（Java 版客户端）
> 目标产物：一个独立 Java Mod（`cynyun-schembar`），同一份核心代码后续可编入 Fork 客户端作为内置功能
> 文档用途：交给负责新建 Mod 仓库的 agent 直接实现；也作为宿主 Fork 集成（自动拉取 + 条件编译）的契约

---

## 1. 背景与目标

原版蓝图（schematic）只能通过 `SchematicsDialog` 打开一个全屏对话框，找到蓝图后再点一次进入放置模式。在高强度 PvP 中，这个流程步骤多、界面遮挡大、切换慢。

本 Mod 提供一个**游戏内常驻的半透明悬浮窗**：

- 用「栏目 → 序列 → 蓝图按钮」三层结构快速浏览、点击即用；
- 栏目/序列/按钮的标题、图标、排列都由用户自己配置；
- 鼠标悬停蓝图按钮时显示可选的蓝图详情；
- 增删只针对悬浮窗的**配置条目**，不修改、不删除 `schematics/` 里的 `.msch` 文件；
- 不绑定任何快捷键，全部通过鼠标点击完成；
- 配置持久化，重启游戏后保持。
- 同一套核心代码既可作为独立 Mod 运行，也可被 Fork 客户端编译进核心作为内置功能。

### 非目标（v1 不做）

- 不修改原版 `SchematicsDialog` 的布局或行为；
- 不新增/删除/覆盖蓝图库文件；
- 不做快捷键、鼠标手势、滚轮绑定；
- 不做多人之间的配置同步（配置仅本地）；
- 不做云端同步、账号体系；
- 不做移动端专用交互（v1 先保证桌面端；触屏可用性后续再评估）。

---

## 2. 术语

| 术语 | 含义 |
|---|---|
| 栏目（Category） | 用户自定义的一组完整「序列集合」，用于切换不同战况/阵营/科技路线。栏目本身可增删改名、可选图标。栏目也是「切换下面全部序列」的开关。 |
| 最大栏目（Max Visible Categories） | 同时显示的栏目切换按钮数量上限；超出的栏目通过左右滚动/翻页访问。用户可自行新增更多栏目。 |
| 序列（Sequence） | 栏目下的一组同类蓝图，例如「钻头」「石墨」「炮塔」。序列可增删改名、可选图标，内部蓝图按钮纵向滚动。 |
| 蓝图按钮（Entry） | 悬浮窗里的一个可点击项，引用蓝图库中的某个 `Schematic`；可自定义标题/图标；点击即进入该蓝图的放置模式。 |
| 悬浮详情（Hover Detail） | 鼠标停在蓝图按钮上时，在按钮附近临时出现的信息层；显示哪些字段由设置开关决定。 |
| 编辑模式（Edit Mode） | 悬浮窗的配置态：可以增删栏目/序列/按钮、改名、改图标、调整顺序。非编辑态下点击按钮直接使用蓝图。 |

---

## 3. 布局与交互规格

### 3.1 总体

```
┌──────────────────────────────────────────────┐
│ [栏目1] [栏目2] [栏目3] …（最多 max 个可见）  │  ← 栏目切换行
├──────────────────────────────────────────────┤
│ [序列1] [序列2] [序列3] [序列4] … → 横向滚动 │  ← 当前栏目下的序列
│  蓝图1   蓝图1   蓝图1   蓝图1               │
│  蓝图2   蓝图2   蓝图2   蓝图2               │  ← 每个序列内纵向滚动
│  蓝图3   蓝图3   蓝图3   蓝图3               │
│  …       …       …       …                  │
└──────────────────────────────────────────────┘
```

- 点击某个栏目 → 下方整体切换为该栏目的序列集合（这就是「切换不同情况下的全部序列」）。
- 栏目行最多同时显示 `maxVisibleCategories` 个栏目按钮，多出的通过左右滚动按钮/滚轮横向滚动访问。
- 序列行横向滚动；每个序列内部的蓝图按钮纵向滚动。
- 面板半透明（默认不透明度 85%），尽量少遮挡游戏画面。
- 面板初始锚定在屏幕左上角 HUD 区域下方；v1 固定位置，不做拖动。面板层级放在 `ui.hudGroup` 内，位于其他 HUD 元素之上（`toFront()`）。

### 3.2 打开/关闭

- `ui.hudGroup` 内常驻一个**悬浮窗开关按钮**（默认图标 `Icon.paste`，可后续换自定义图标），仅在游戏中显示。
- 点击开关按钮 → 显示/隐藏悬浮窗；不使用任何快捷键。
- 面板右上角有「×」按钮，点击关闭面板（保留 HUD 开关按钮）。
- 面板不暂停游戏；不阻塞世界点击（只有面板自身区域接收点击/滚轮）。

### 3.3 非编辑态：使用

- 点击蓝图按钮：
  1. 若该条目解析不到 `Schematic`，弹出提示（`ui.showInfoFade`），不进入放置；
  2. 否则先隐藏悬浮窗（设置项 `hideOnUse` 默认开），再调用 `Vars.control.input.useSchematic(schematic)`；
  3. 进入原版蓝图放置流程（鼠标移动时显示预览，左键放置，右键取消）。
- 不改变原版放置逻辑、不自动旋转、不自动确认。
- 再次点击 HUD 开关按钮可重新打开面板。
- 多人游戏直接可用（`useSchematic` 是原版客户端行为），服务器是否接受大蓝图由服务器规则决定。

### 3.4 悬停详情

- 鼠标进入蓝图按钮 → 在按钮右侧/光标附近弹出详情面板；移出 → 消失。
- 详情是临时层（不写入配置），其内容由设置开关控制：
  - 预览图（默认开，`max 220×220`，等比缩放）
  - 名称（默认开）
  - 尺寸（宽×高，默认开）
  - 发电/耗电（默认开；为 0 时显示「无」或隐藏）
  - 物品需求（默认开；图标 + 数量）
  - 描述（默认关）
  - 标签（默认关）
- 详情面板要限制在屏幕内（靠近边缘时向左/向上翻转）。
- 预览图按需生成：只有悬停时才创建 `SchematicImage`/`getPreview`，网格内不要为每个按钮生成预览。
- 蓝图缺失时详情显示红色「缺失」和最后已知名称，允许在编辑模式下移除或重新选择。

### 3.5 编辑模式

- 面板标题栏有「编辑」按钮，点击进入/退出编辑模式。
- 编辑模式下：
  - 每个栏目按钮上有「✎ 改名/图标」「× 删除」，栏目行末尾有「+ 栏目」；
  - 每个序列标题上有「✎」「×」，序列行末尾有「+ 序列」；
  - 每个蓝图按钮变为选择态，选中后出现工具条：「↑」「↓」「✎ 标题/图标」「× 移除」；
  - 序列末尾有「+ 蓝图」按钮，打开蓝图库选择器。
- 删除栏目/序列/按钮都只改配置文件；**不删除 `.msch` 文件**，也不改蓝图库。
- 所有编辑操作即时保存（300 ms 防抖），并立即重绘。
- 删除前弹确认框（栏目/序列）；按钮删除可不确认，但提供撤销？v1 不做撤销，删除前确认即可。

### 3.6 蓝图库选择器（添加条目）

- `BaseDialog`：标题「选择蓝图」，搜索框 + 滚动列表。
- 列表项显示：蓝图名称、尺寸、（可选）缩略图；搜索支持名称子串与标签（labels）。
- 点击一项 → 加入当前序列末尾 → 关闭选择器。
- 空库时显示「蓝图库为空」提示。
- 允许同一个蓝图被加入多个序列/多个栏目（不做去重限制）。

### 3.7 设置面板

通过面板标题栏「设置」按钮打开一个 `BaseDialog`（不用原版设置菜单，保证 Mod 独立可用）：

| 设置项 | 类型 | 默认 | 说明 |
|---|---|---|---|
| 最大可见栏目数 | 1–10 整数 | 4 | 超过部分横向滚动 |
| 面板不透明度 | 30%–100% | 85% | 半透明背景 |
| 使用后隐藏面板 | 开关 | 开 | 点击蓝图后自动收起 |
| 悬停显示预览 | 开关 | 开 | 关闭可省性能 |
| 显示名称 | 开关 | 开 | |
| 显示尺寸 | 开关 | 开 | |
| 显示电力 | 开关 | 开 | |
| 显示物品需求 | 开关 | 开 | |
| 显示描述 | 开关 | 关 | |
| 显示标签 | 开关 | 关 | |
| 重置为默认 | 按钮 | — | 二次确认 |

---

## 4. 数据模型

### 4.1 配置位置

```
<Vars.dataDirectory>/cynyun-schembar/config.json
```

- 使用 `Vars.dataDirectory.child("cynyun-schembar/config.json")`。
- 与宿主 Fork 的内置模式**共用同一个文件**（同一份配置，双轨切换不丢配置）。
- 编码 UTF-8；写入采用「临时文件 + 替换」避免半截文件。
- 保留 `config.json.bak`：版本升级或解析失败时备份并回退默认配置。

### 4.2 JSON Schema（v1）

```json
{
  "version": 1,
  "settings": {
    "maxVisibleCategories": 4,
    "opacity": 0.85,
    "hideOnUse": true,
    "hover": {
      "preview": true,
      "name": true,
      "size": true,
      "power": true,
      "requirements": true,
      "description": false,
      "labels": false
    }
  },
  "activeCategoryId": "c-s",
  "categories": [
    {
      "id": "c-s",
      "name": "S科技",
      "display": { "iconType": "block", "iconName": "duo" },
      "sequences": [
        {
          "id": "s-drill",
          "name": "钻头",
          "display": { "iconType": "item", "iconName": "copper" },
          "entries": [
            {
              "id": "e-1",
              "title": "钻头1",
              "display": { "iconType": "none", "iconName": "" },
              "schematic": {
                "path": "drill-1.msch",
                "name": "drill-1"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

字段说明：

- `id`：稳定标识（UUID 或自增字符串），改名不变；用于 `activeCategoryId` 和排序。
- `name`：用户自定义文字，可为空（为空时回退显示「未命名」）。
- `display.iconType`：`"none"` / `"item"` / `"block"`。
- `display.iconName`：`Vars.content.item(name)` / `Vars.content.block(name)` 的内容名；解析失败时回退文字。
- `schematic.path`：相对 `Vars.schematicDirectory` 的路径（优先解析）；`schematic.name`：蓝图名（兜底解析）。
- `entries` 数组顺序 = 显示顺序；`sequences` / `categories` 同理。
- 未知字段：忽略；缺失字段：用默认值补全。
- `version`：当前 1；未来迁移在 `ConfigStore.migrate(json)` 中集中处理。

### 4.3 蓝图引用与解析

解析流程（每次打开面板/刷新时重建索引）：

1. 遍历 `Vars.schematics.all()`；
2. 若 `s.file != null` 且位于 `Vars.schematicDirectory` 下，记录 `relativePath → s`；
3. 记录 `name → s`（同名取第一个；path 优先）；
4. 条目解析顺序：`path` → `name` → 失败。

规则：

- 蓝图被改名/移动：`path` 失效时仍可按 `name` 命中；
- 蓝图被删除：条目保留为「缺失」状态，不自动删除配置（用户可能只是移走文件）；
- Mod 自带蓝图（`s.mod != null`）也可以引用；但编辑时提示「来自 Mod，不可删除源文件」；
- 不调用 `schematics.load()`、不修改 `Schematics.all()`；只读。

### 4.4 默认配置

首次启动：

```json
{
  "version": 1,
  "settings": { "maxVisibleCategories": 4, "opacity": 0.85, "hideOnUse": true,
    "hover": { "preview": true, "name": true, "size": true, "power": true,
               "requirements": true, "description": false, "labels": false } },
  "activeCategoryId": "",
  "categories": []
}
```

空状态：面板显示「还没有栏目，点击 + 新建」；不自动创建示例数据（避免污染用户配置）。

---

## 5. 运行时结构

```
cynyunschembar/
  BlueprintBarMod.java        // extends Mod，init() → BlueprintBar.init()
  BlueprintBar.java           // 门面：init/dispose/toggle/isActive + 配置 + UI 装配
  model/
    BarConfig.java            // 根模型 + 默认值 + 规范化
    Category.java
    Sequence.java
    Entry.java
    Display.java              // iconType/iconName + 文本
    SchematicRef.java         // path/name
    HoverSettings.java
  store/
    ConfigStore.java          // 读写 JSON、防抖、备份、迁移
  runtime/
    SchematicIndex.java       // Vars.schematics.all() → 路径/名称索引
    ContentIcons.java         // item/block → TextureRegion
    ConfigActions.java        // 增删改排序等纯操作（可单测）
  ui/
    BarTable.java             // 悬浮窗主体
    CategoryRow.java
    SequenceRow.java
    EntryButton.java
    HoverDetail.java
    BlueprintPickerDialog.java
    IconPickerDialog.java
    SettingsDialog.java
  Strings.java                // 所有中文文案集中处
```

设计原则：

- `model/`、`store/`、`runtime/ConfigActions` **不 import `mindustry.Vars`**，可在无游戏环境用 JUnit 测试；
- 只有 `runtime/SchematicIndex`、`ui/*`、`BlueprintBar` 接触 `Vars`；
- 所有对 `Vars` 的访问都通过 `BlueprintBar`/`SchematicIndex` 注入，便于内置模式替换数据源。

---

## 6. 与游戏 API 的对接（只允许公开 API）

以下均为 Mindustry 160.7 的 public 成员，独立 Mod 可直接使用。

### 6.1 读取蓝图库

```java
Seq<Schematic> all = Vars.schematics.all();          // Seq<Schematic>，已按名称排序
Schematic s = all.find(x -> x.name().equals("drill-1"));
```

`Schematic` 可读字段/方法：

```java
s.name();                 // tags.get("name")
s.description();
s.width; s.height;
s.labels;                 // Seq<String>
s.tags;                   // StringMap
s.requirements();         // ItemSeq（物品 + 数量）
s.powerProduction();
s.powerConsumption();
s.file;                   // 可能为 null
s.mod;                    // 可能为 null
```

### 6.2 使用蓝图

```java
if(Vars.state.isGame()){
    Vars.ui.hudGroup...;                    // 先隐藏自己的面板
    Vars.control.input.useSchematic(s);     // 进入原版放置模式
}
```

`useSchematic` 会按当前鼠标位置生成 `selectPlans`，不自动建造、不自动确认。

### 6.3 预览图

```java
if(Vars.schematics.hasPreview(s)){
    Texture tex = Vars.schematics.getPreview(s);
}
// 或直接复用原版控件（public static 内部类）：
SchematicsDialog.SchematicImage image = new SchematicsDialog.SchematicImage(s);
```

注意：

- 不要使用 `Schematics.previews()`；该方法的可见性在 Fork 与上游不一致，属于内部实现；
- 预览生成是 FBO 渲染，不要在网格里批量创建 `SchematicImage`；
- 原版有 `schemloadtime` 每帧预算与 `maxschematicpreviews` 缓存上限，Mod 不要绕过。

### 6.4 图标

```java
TextureRegion icon = Vars.content.item("copper").uiIcon;
TextureRegion icon = Vars.content.block("duo").uiIcon;
```

内容名解析失败时要回退为文字/默认图标，不能抛异常。

### 6.5 HUD 容器

```java
Vars.ui.hudGroup.add(barTable);   // public WidgetGroup
```

- `hudGroup` 的 `touchable = childrenOnly`，且 `visible(() -> state.isGame())`；
- 自己的面板/按钮要显式设置尺寸，避免整块拦截世界点击；
- 需要置顶时 `toFront()`；
- 该字段是 public 但非正式 SPI，升级时要回归测试。

### 6.6 对话框/输入

```java
Vars.ui.showTextInput(title, text, maxLen, def, cons);
Vars.ui.showConfirm(title, text, yes, no);
Vars.ui.showInfoFade(text);
```

自定义编辑界面用 `BaseDialog` + `Table`，不要修改原版 `SettingsMenuDialog`。

### 6.7 明确禁止

- `import mindustry.client.*`（Fork 独有包）；
- 引用 `PingService`、`QuickSwap`、`SpawnInfoOverlay`、`SchematicBrowserDialog` 等 Fork 类；
- 反射修改原版私有字段；
- 调用 `schematics.add()` / `remove()` / `load()` 或写 `schematics/` 目录；
- 绑定快捷键（`Core.input.keyDown/keyTap`）——本 Mod 明确「仅点击」；
- 把 Mindustry 的类打进 Mod jar（加载器会拒绝）。

---

## 7. 持久化细节

- `ConfigStore.load()`：文件不存在 → 默认配置；解析失败 → 备份为 `config.json.bad-<timestamp>`，使用默认配置并提示。
- `ConfigStore.save()`：300 ms 防抖，主线程写；先写 `config.json.tmp`，再 `moveTo` 覆盖，失败时保留旧文件并 `Log.err`。
- 版本迁移：`version < 1` 时补默认值；未来字段变更只增不改。
- 配置写入失败（只读目录/磁盘满）不能崩溃：弹出一次提示，内存中继续可用。
- 与内置模式共用文件：内置适配层必须检测 Mod 是否已安装，避免双写。

---

## 8. 性能与稳定性要求

| 场景 | 要求 |
|---|---|
| 打开面板（100 条目） | < 200 ms 完成首帧；不生成预览 |
| 悬停切换 | 预览只对当前项生成，复用缓存 |
| 大量条目 | 每个序列独立 `ScrollPane`；不一次性创建所有栏目的 UI |
| 内存 | 不持有 `SchematicImage` 的引用；面板关闭时释放 UI 引用（预览纹理由原版缓存管理） |
| 空库/空配置 | 不崩溃，显示空状态 |
| 蓝图被删除 | 条目显示「缺失」，点击提示，不崩溃 |
| 配置损坏 | 回退默认 + 备份，不崩溃 |

---

## 9. Mod 打包

### 9.1 仓库结构

```
cynyun-schembar/
  build.gradle
  settings.gradle
  gradle/wrapper/...
  libs/Mindustry.jar            # CI 下载的 160.7 桌面 jar（compileOnly）
  src/main/java/cynyunschembar/...
  src/main/resources/mod.json
  src/test/java/cynyunschembar/...
  README.md
```

### 9.2 mod.json

```json
{
  "name": "cynyun-schembar",
  "displayName": "Blueprint Bar",
  "author": "Cynyun",
  "description": "A quick blueprint bar overlay for Mindustry. Configurable categories, sequences and one-click schematic placement.",
  "version": "1.0.0",
  "minGameVersion": "160",
  "main": "cynyunschembar.BlueprintBarMod",
  "java": true
}
```

- `main` 必须显式写，不能依赖默认推导；
- `minGameVersion` 用 `"160"`（加载器要求 Java Mod 至少 154）；
- 不要把 `mod.json` 放进子目录。

### 9.3 build.gradle

```groovy
plugins { id 'java' }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

repositories { mavenCentral() }

dependencies {
    compileOnly files("libs/Mindustry.jar")
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

test { useJUnitPlatform() }

jar {
    archiveFileName = "cynyun-schembar.jar"
}
```

- 只 `compileOnly`，**不要** `implementation`，否则会把游戏类打进 jar；
- 用 Java 17（与 160.7 一致）；
- 若 CI 下载不到桌面 jar，备选：JitPack `com.github.Anuken.Mindustry:core:v160.7`，或从本 Fork 的 Release 取 `mindustry-f-client.jar`（但独立 Mod 应优先用上游 jar 编译，保证不依赖 Fork）。

### 9.4 CI（建议）

```yaml
name: Build Mod
on:
  push: { branches: [main] }
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - name: Fetch Mindustry jar
        run: |
          mkdir -p libs
          curl -L -o libs/Mindustry.jar <Mindustry 160.7 release jar URL>
      - run: chmod +x gradlew && ./gradlew test jar
      - uses: actions/upload-artifact@v4
        with: { name: cynyun-schembar, path: build/libs/cynyun-schembar.jar }
```

---

## 10. 与宿主 Fork 的集成契约（供后续实现）

> 落地细则、源集注入方式、编译门禁与“改动影响 Fork 时必须同步记录”的维护规则，见 [`Fork内置集成指南.md`](Fork内置集成指南.md)。

### 10.1 门面 API

Mod 核心对外只暴露一个入口（`BlueprintBar`）：

```java
public final class BlueprintBar {
    public static void init();      // 幂等；加载配置、注册 HUD 按钮
    public static void dispose();   // 注销 UI、保存配置
    public static void toggle();    // 显示/隐藏面板（HUD 按钮调用）
    public static boolean isActive();
    public static final String CONFIG_PATH = "cynyun-schembar/config.json";
}
```

- `BlueprintBarMod.init()` 只调用 `BlueprintBar.init()`；
- 内置适配层也只调用 `BlueprintBar.init()`，不直接碰内部类；
- 两边共用 `ConfigStore`/配置文件，保证配置一致。

### 10.2 宿主侧改动

1. 子模块/目录：`mods/cynyun-schembar/`（Git Submodule，固定 commit）。
2. `core/build.gradle`：

```groovy
def modSrc = project.findProperty("modSrc") ?: "$rootDir/mods/cynyun-schembar/src/main/java"
if(file(modSrc).exists()){
    sourceSets.main.java.srcDirs += file(modSrc)
}
```

3. 新增 `core/src/mindustry/client/features/BlueprintBarAdapter.java`：

```java
public class BlueprintBarAdapter{
    public static void init(){
        if(Vars.mods.getMod("cynyun-schembar") != null) return; // 已装 Mod，避免双份
        BlueprintBar.init();
    }
}
```

4. `ClientLogic.kt` 在客户端加载完成后调用 `BlueprintBarAdapter.init()`。
5. `SettingsMenuDialog` 增加「蓝图快捷栏」总开关（关闭时调 `dispose()`）。
6. CI：`actions/checkout` 加 `submodules: recursive`；独立 Mod 构建 job 与双资产 Release（后续实现）。

### 10.3 双轨注意事项

- 同一份 `cynyunschembar.*` 类可能同时存在于 core 与 Mod jar（两个 classloader）→ 以 `Vars.mods.getMod("cynyun-schembar")` 为准，只允许一边初始化；
- 内置模式下不要调用 Mod 仓库的 `mod.json` 逻辑；
- 独立 Mod 构建以「只依赖上游公开 API」为硬门禁：CI 用上游 160.7 jar 编译，出现 `mindustry.client.*` 导入即失败；
- 配置版本升级要两边同步（同一个 `ConfigStore`）。

---

## 11. 测试计划

### 11.1 单元测试（无游戏环境）

- `BarConfig` 序列化/反序列化往返一致；
- 默认配置规范化（缺字段、字段类型错误、未知字段）；
- 栏目/序列/条目 CRUD、排序、`activeCategoryId` 失效回退；
- `SchematicRef` 解析优先级（path 命中、name 兜底、缺失）；
- `maxVisibleCategories` 边界（1、10、越界 clamp）；
- `opacity` clamp；
- 配置文件损坏 → 备份 + 默认配置；
- 防抖保存不会重复写。

### 11.2 集成/手动（进游戏）

- 模组加载后 HUD 出现开关按钮；
- 新建栏目/序列/条目 → 重启后仍在；
- 切换栏目 → 序列整体切换；
- 点击条目 → 面板隐藏、进入放置模式、可正常建造；
- 多人服务器中同样可用；
- 悬停显示字段与开关一致；关闭预览后不再生成预览；
- 蓝图被删除后条目显示「缺失」；
- 蓝图库为空时不崩溃；
- 删除条目/栏目后 `schematics/` 文件数量不变（关键回归项）；
- 性能：100 条目打开不卡顿；连续悬停 20 个条目无显著掉帧。

### 11.3 兼容性

- 独立 Mod：在未改动的 Mindustry 160.7 桌面客户端加载；
- 内置：Fork 构建后功能一致、配置互通；
- 不测试 iOS（Java Mod 不支持）；Android 需 dex，v1 不作为验收项。

---

## 12. 验收标准（Definition of Done）

1. `cynyun-schembar.jar` 可被原版 160.7 加载，`mods` 列表可见，无报错。
2. 悬浮窗通过 HUD 按钮点击开关，无任何快捷键。
3. 栏目数量可扩展，`maxVisibleCategories` 控制可见数量，超出可横向访问。
4. 点击栏目切换下方全部序列。
5. 栏目/序列/条目的增删改、排序、自定义文字、item/block 图标全部可用。
6. 条目从蓝图库选择器添加；删除只删配置，不改蓝图库文件。
7. 点击条目进入原版放置模式；多人可用。
8. 悬停详情字段按设置开关显示；缺失蓝图有明确提示。
9. 配置持久化；损坏可恢复；与内置模式共用配置。
10. 代码中不存在 `mindustry.client.*` 引用，CI 以上游 jar 编译通过。
11. 单元测试全部通过。
12. README 写明安装、使用、配置路径与已知限制。

---

## 13. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 预览生成卡顿 | 悬停掉帧 | 只对悬停项生成；不缓存 `SchematicImage`；尊重原版预算设置 |
| 双层 ScrollPane 抢滚轮 | 滚动不灵 | 序列内部滚动优先；栏目行用左右按钮翻页而非滚轮；实测调整 |
| HUD 拦截点击 | 影响建造 | 面板/按钮严格限定尺寸；`touchable` 只作用于子元素；非编辑态不透明区域尽量小 |
| 蓝图路径失效 | 条目丢失 | path→name 双索引；缺失态可重新选择 |
| 配置与内置模式互相覆盖 | 配置丢失 | 同一 `ConfigStore`；检测 Mod 已装载则禁用内置初始化 |
| 上游 API 变动 | 编译/运行失败 | 只使用 public 成员；`minGameVersion`；CI 固定 160.7；升级时回归 |
| 双 classloader 双实例 | 双 UI/双写 | `Vars.mods.getMod("cynyun-schembar")` 检查，单边初始化 |
| 大量条目内存/构建慢 | 打开卡 | 只构建当前栏目的序列；序列内 `ScrollPane` 懒加载（可选） |
| 移动端 | 不可用 | v1 明确不支持；后续用长按代替悬停 |

---

## 14. 实施里程碑（建议）

1. **M1 骨架**：Mod 仓库、`mod.json`、`BlueprintBarMod`、`BlueprintBar`、配置模型 + 单测。
2. **M2 只读面板**：从配置读栏目/序列/条目，HUD 开关，点击使用蓝图。
3. **M3 悬停详情**：预览 + 字段开关 + 设置对话框。
4. **M4 编辑模式**：栏目/序列/条目 CRUD、排序、蓝图选择器、图标选择器。
5. **M5 持久化/健壮性**：防抖保存、备份恢复、缺失蓝图处理、性能优化。
6. **M6 发布**：README、CI、Release jar；随后进入宿主 Fork 集成。

---

## 15. 未决问题（实现前需确认）

1. 栏目是「单选切换」（同一时间只显示一个栏目的序列），还是「多栏并排」？本文按单选切换设计；截图 1 的并排展示视为「多栏目可见」的另一种布局，若要并排需要调整 UI 层。
2. 条目本身是否也允许自定义 item/block 图标？（本文默认允许，与栏目/序列一致。）
3. 是否需要「导入/导出配置」按钮（JSON 文本）？v1 暂不做，但配置格式已可手工迁移。
4. 是否需要英文界面？v1 先中文；`Strings.java` 集中管理，后续可加 bundle。
5. 面板位置是否要可拖动/记忆？v1 固定左上。

---

## 附录 A：宿主 Fork 中的参考位置

以下路径仅用于核对 API，不要求 Mod 引用：

- 蓝图管理器：`core/src/mindustry/Vars.java`（`schematics`、`dataDirectory`、`content`）
- `core/src/mindustry/game/Schematics.java`（`all()`、`getPreview()`、`hasPreview()`、`readBase64()`）
- `core/src/mindustry/game/Schematic.java`（字段与 `requirements()`）
- `core/src/mindustry/input/InputHandler.java`（`useSchematic`）
- `core/src/mindustry/ui/dialogs/SchematicsDialog.java`（`SchematicImage`、`showInfo`）
- `core/src/mindustry/core/UI.java`（`hudGroup`）
- `core/src/mindustry/core/ContentLoader.java`（`item(String)`、`block(String)`）
- `core/src/mindustry/game/EventType.java`（`SchematicCreateEvent`，本 Mod 不依赖）

## 附录 B：宿主 Fork 现有的相关实现（不要复用）

- `core/src/mindustry/client/ui/SchematicBrowserDialog.java`：Fork 自带的蓝图浏览器（仓库/标签/搜索）。属于 Fork 私有代码，独立 Mod 不得引用；内置模式可选择与其做入口联动。
- `core/src/mindustry/client/communication/SchematicTransmission.kt`：Fork 的蓝图传输，与本 Mod 无直接关系。