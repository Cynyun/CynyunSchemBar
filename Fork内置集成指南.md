# Fork 内置集成指南（cynyun-schembar）

> 项目：**cynyun-schembar**（显示名 Cynyun SchemBar，功能名 Blueprint Bar）
> 文档版本：v1.0（2026-11-17 创建）
> 适用对象：Cynyun Fork 客户端（下文简称 **F 端**）中 `core` 模块的构建者与维护者
> 上游规范：`mod设计.md` v1.0（§9 打包、§10 集成契约）

---

## 0. 本文的定位与维护规则

本文件是 **F 端唯一权威的集成契约**。`mod设计.md` 描述"要做什么"，本文件描述"编译进 core 时怎么不炸、改了什么必须同步"。

**维护规则（强制）**

1. 本项目任何一次提交，只要命中下列四类改动之一，就必须在本文件 **§9 变更记录** 追加一行，并在 **§10 自检表** 对应项打勾：
   - ① 新增/删除对 `Vars`、`mindustry.*`、`arc.*` 的引用（尤其是 Fork 私有包 `mindustry.client.*`）；
   - ② 新增/删除第三方依赖、改变 Java 语言级别；
   - ③ 新增资源文件，或改变图集 / 配置路径 / 资源前缀等"字符串契约"；
   - ④ 新增/修改对外入口（`BlueprintBar` 的 public 方法）、配置字段、`version` 迁移。
2. 记录格式：日期 / 提交号（无则写 —）/ 变更内容 / 是否影响 Fork 编译 / Fork 需配合的动作。
3. 不确定是否影响时，**按影响处理并记一行**，成本远低于 F 端编译失败。

**名称变更注意**：本仓库的 mod 内部名已于 2026-11-17 从模板的 `example-java-mod` 改为 **`cynyun-schembar`**，`mod设计.md` 中 14 处 `blueprint-bar` 与包名 `blueprintbar` 已同步改为 `cynyun-schembar` / `cynyunschembar`。

---

## 1. 集成模型：双轨、互斥、共用配置

| 轨道 | 入口 | 何时生效 |
|---|---|---|
| 独立 Mod | `mod.hjson` 的 `main: cynyunschembar.BlueprintBarMod` → `BlueprintBar.init()` | `Vars.mods.getMod("cynyun-schembar") != null` |
| 内置（F 端） | `core` 的 `BlueprintBarAdapter.init()` → `BlueprintBar.init()` | `Vars.mods.getMod("cynyun-schembar") == null` |

三条铁律：

1. **互斥**：同一进程只允许一条轨初始化。内置侧必须先查 `getMod`，Mod 侧不查（它自己就是 Mod）。
2. **共用配置**：两条轨读写同一个文件 `<Vars.dataDirectory>/cynyun-schembar/config.json`，切换轨道不丢配置。因此两侧的 `ConfigStore` 必须是同一份源码，不得各写一套。
3. **共用源码**：`src/` 下的类被两边同时编译，因此必须同时满足两边的编译环境（见 §5）。任何"只在 Fork 里能编译"的代码都不允许出现在 `src/` 下。

---

## 2. 仓库布局与源集注入

### 2.1 真实布局（以本仓库为准）

> `mod设计.md` §9.1 画的 `src/main/java`、`build.gradle`、`libs/Mindustry.jar`、`src/main/resources/mod.json` 是**初稿假设**，与当前仓库（Anuken Java Mod Template）不一致。**以本节为准。**

```
Cynyun-SchemBar/                ← Git 仓库（名称不参与任何契约）
  mod.hjson                     ← 描述文件在根目录（不是 mod.json、不放进 resources）
  build.gradle.kts              ← Kotlin DSL；依赖由 Ivy 从 GitHub Releases 拉 dependencies.jar
  gradle/wrapper/…              ← Gradle 9.8.0
  src/                          ← Java 源码根就是 src（sourceSets 被改成 listOf("src")）
    example/ExampleJavaMod.java ← 模板示例，M1 时删除
  assets/sprites/…              ← 贴图（Mod 模式自动加 "cynyun-schembar-" 前缀）
  .github/workflows/…           ← 本仓库自己的 CI（用 deploy，产出桌面+安卓合一 jar）
```

F 端以 **Git Submodule** 方式引用，固定 commit，放到 `mods/cynyun-schembar/`。**只取 `src/` 与 `assets/`，不要 import 本仓库的 `build.gradle.kts`、`gradle/`、`.github/`、`mod.hjson`。**

### 2.2 注入写法

Kotlin DSL（若 F 端 `core/build.gradle.kts`）：

```kotlin
val modSrc = (findProperty("modSrc") as String?) ?: "$rootDir/mods/cynyun-schembar/src"
if (file(modSrc).exists()) {
    sourceSets["main"].java.srcDir(modSrc)
    sourceSets["main"].java.exclude(
        "cynyunschembar/BlueprintBarMod.java", // 内置模式下不需要 Mod 子类
        "example/**"                          // 模板残留，M1 删除后可去掉
    )
}
```

Groovy（若 `core/build.gradle`）：

```groovy
def modSrc = project.findProperty("modSrc") ?: "$rootDir/mods/cynyun-schembar/src"
if (file(modSrc).exists()) {
    sourceSets.main.java.srcDirs += file(modSrc)
    sourceSets.main.java.exclude 'cynyunschembar/BlueprintBarMod.java'
    sourceSets.main.java.exclude 'example/**'
}
```

### 2.3 注入时的六个坑

| 坑 | 后果 | 做法 |
|---|---|---|
| 把 `src/test` 也挂进来 | junit 进 core 的 classpath，F 端构建变慢/冲突 | 只加 `main` |
| 把 `BlueprintBarMod`（`extends Mod`）编进 core | 无害但会被误认为可加载入口；且它引用 `Vars.mods` 语义 | `exclude` 掉 |
| 直接复制源码进 `core/src` | 两份代码分叉，修一处漏一处 | 只用 submodule |
| submodule 没初始化 | `file(modSrc).exists()` 为 false，静默不编译，功能"消失" | CI 与本地都 `git submodule update --init --recursive` |
| F 端自己 patch 共享代码 | 下次更新 submodule 被覆盖 | 需要 Fork 专属行为时改 `BlueprintBarAdapter.java`，不改 `src/` |
| 依赖 `modSrc` 绝对路径写死 | 换机器/CI 失败 | 用 `$rootDir/mods/...` 或 `-PmodSrc` |

---

## 3. 标识一致性：六条线必须一起改

改任何一项都会同时影响 F 端，**必须六项一起改并在 §9 记录**：

| # | 项 | 当前值 | 定义位置 |
|---|---|---|---|
| 1 | mod 内部名 | `cynyun-schembar` | `mod.hjson` 的 `name`；`Vars.mods.getMod()` 的判定字符串 |
| 2 | 显示名 | `Cynyun SchemBar` | `mod.hjson` 的 `displayName` |
| 3 | Java 包名 | `cynyunschembar` | `src/cynyunschembar/`（包名不能含 `-`） |
| 4 | 配置路径 | `cynyun-schembar/config.json` | `BlueprintBar.CONFIG_PATH` |
| 5 | 图集前缀 | Mod 模式 `cynyun-schembar-`；内置模式 `""` | `BlueprintBar.init()` 注入（见 §6） |
| 6 | jar 名 | 由 Gradle `project.name` 决定（当前仓库目录名 `Cynyun-SchemBar` → `Cynyun-SchemBar.jar`） | 构建脚本，不参与契约 |

> 注意：`Vars.mods.getMod(name)` 按 mod 的**内部名**匹配，与 jar 文件名、仓库名、文件夹名无关。F 端不要用 jar 名或目录名做判定。

---

## 4. 初始化契约（F 端调用方必读）

```java
public final class BlueprintBar {
    public static void init();      // 幂等：重复调用只生效一次
    public static void dispose();   // 注销 hudGroup 子元素 + flush 配置
    public static void toggle();
    public static boolean isActive();
}
```

F 端必须遵守：

1. **时点**：在客户端**加载完成之后**调用（`ClientLoadEvent` 之后，或 `ClientLogic` 客户端初始化完成处）。过早调用会导致 `Vars.schematics.all()` 为空、`Vars.ui.hudGroup` 尚未建立。内置侧建议放在原版 `ClientLoadEvent` 监听里，而不是构造函数。
2. **只调 `init()`**：适配层不得直接操作 `BarTable`、`ConfigStore` 等内部类，否则 Mod 侧重构会连带炸 F 端。
3. **判重**：`getMod("cynyun-schembar") != null` 时直接 return；同时 `init()` 内部还要有静态幂等标志，防抖双重调用（例如设置里开关两次）。
4. **失败隔离**：`init()` 内部任何异常必须被捕获并 `Log.err`，不得抛出到 core 启动流程；UI 注册失败时功能降级为"不显示"，而不是崩溃。
5. **关闭**：F 端设置总开关关闭时调 `dispose()`，再次开启时调 `init()`；`dispose()` 必须能从 `hudGroup` 完整移除，不能残留透明层拦截点击。
6. **开关状态**不要写进共享的 `config.json`（否则独立 Mod 轨道读不到"被 F 端关掉"这个意图，或不认识该字段）。放 `Core.settings`（原版 settings），键名建议 `schembar-enabled`。

---

## 5. 编译期硬性约束（最重要的一节）

`src/` 下的代码会被 **上游 160.7 jar** 和 **F 端 core** 分别编译一次。因此：

| # | 约束 | 违反后的现象 |
|---|---|---|
| 1 | 只使用上游 public API（`Vars`、`Schematic`、`Schematics`、`BaseDialog` 等） | 本仓库 CI（上游 jar）直接编译失败 |
| 2 | **禁止** `import mindustry.client.*` | F 端能过、上游门禁失败 → 独立 Mod 无法发布 |
| 3 | 禁止引用 `PingService`、`QuickSwap`、`SpawnInfoOverlay`、`SchematicBrowserDialog`、`SchematicTransmission` 等 Fork 类 | 同上，且双轨行为分叉 |
| 4 | 禁止把"是否已装 Mod"的判断写进共享代码 | 内置模式下 `getMod` 恒为 null，逻辑错乱 |
| 5 | 禁止新增第三方依赖（JUnit 仅 `testImplementation`，不进 core） | core 构建污染、jar 膨胀 |
| 6 | 禁止反射改写 core 私有字段 | 上游升级即崩 |
| 7 | Java 语言级别固定 17，与 F 端 core 一致 | 编译期 `Unsupported class file major version` |
| 8 | 共享代码不得依赖 Kotlin（F 端 core 是 Kotlin，但 Mod 侧没有 Kotlin 运行时） | 独立 Mod 轨道 `NoClassDefFoundError` |

**Fork 专属代码的唯一合法位置**：`core/src/mindustry/client/features/BlueprintBarAdapter.java`（以及必要时同级新增的 Fork 文件）。这里可以 import `mindustry.client.*`，可以做 `SchematicBrowserDialog` 入口联动，但**不得**复制 `src/` 下的业务逻辑。

> 判断口诀：**能在独立 Mod 里跑的，放 `src/`；只有 F 端才存在的 API 或入口，放 Adapter。**

---

## 6. 资源与图集（最容易踩的一条）

Mindustry 加载 Mod 的 `assets/sprites/` 时会给贴图**自动加 `<mod名>-` 前缀**（模板示例即 `assets/sprites/frog.png` → 查找 `example-java-mod-frog`）。而 core 的贴图**没有**这层前缀。因此：

- **不要**在图集查找里拼接 mod 名（`Core.atlas.find(modName + "-icon")`），否则内置模式必然找不到。
- **做法**：`init()` 时注入一个前缀字符串——Mod 模式 `"cynyun-schembar-"`，内置模式 `""`——所有查找统一走 `prefix + 资源名` 的封装方法。
- **文件命名**：两侧使用**相同的文件名**，不带前缀。即 `assets/sprites/icon-x.png` 与 `core/assets-raw/sprites/icon-x.png` 同名。
- `icon.png`（Mod 列表里的图标）只属于独立 Mod，**不要**放进 core。
- 内置模式下新增/替换贴图后，F 端需要重新生成图集（`gradlew core:…` 的 texture packing 任务），否则运行时是白块/错误贴图。

---

## 7. 配置共享与双写防护

| 关注点 | 规则 |
|---|---|
| 路径 | 唯一：`<Vars.dataDirectory>/cynyun-schembar/config.json`；**不要**给两条轨各自加子目录 |
| 写入方 | 同一时刻只有一条轨在写（由 §4 判重保证）；`dispose()` 必须 flush |
| 写入方式 | tmp 文件 + `moveTo` 覆盖；300 ms 防抖；失败不崩、`Log.err` |
| 损坏恢复 | 备份 `config.json.bad-<timestamp>`，回退默认配置，弹一次提示 |
| 版本迁移 | 只有 `ConfigStore.migrate(json)` 一处；两条轨同版本代码，禁止只在一侧加字段 |
| 未知字段 | 读时忽略、写时保留（避免 F 端不认识的新字段被 Mod 端写没） |

---

## 8. F 端需要做的改动清单

| # | 位置 | 动作 | 备注 |
|---|---|---|---|
| 1 | 仓库根 | `.gitmodules` 增加 submodule，固定 commit | 路径 `mods/cynyun-schembar` |
| 2 | CI | `actions/checkout` 加 `submodules: recursive` | 漏了会静默不编译 |
| 3 | `core/build.gradle(.kts)` | 源集注入 + exclude（§2.2） | 只加 main |
| 4 | `core/src/mindustry/client/features/BlueprintBarAdapter.java` | 新建适配层：`getMod` 判重 + `BlueprintBar.init()/dispose()` | 唯一可引用 Fork API 的文件 |
| 5 | `ClientLogic.kt` | 客户端加载完成后调 `BlueprintBarAdapter.init()` | 时点见 §4.1 |
| 6 | `SettingsMenuDialog` | 增加「蓝图快捷栏」总开关（`Core.settings` 持久化） | 关闭调 `dispose()` |
| 7 | CI | 独立 Mod 侧门禁 job：用上游 160.7 jar 编译 `src/`，出现 `mindustry.client.*` 即失败 | 双轨兼容的守门人 |
| 8 | （可选）`SchematicBrowserDialog` | 与快捷栏入口联动 | 只能写在 Adapter 层 |

---

## 9. 变更记录（每次影响 Fork 编译的改动都追加到这里）

| 日期 | 提交 | 变更内容 | 影响 Fork？ | Fork 需配合的动作 |
|---|---|---|---|---|
| 2026-11-17 | — | 创建本文件；mod 内部名 `example-java-mod` → `cynyun-schembar`；显示名 → `Cynyun SchemBar`；作者 → `Cynyun`；`mod设计.md` 同步改名（含包名 `cynyunschembar`） | 是（标识契约） | 适配层判重字符串用 `cynyun-schembar`；配置路径改 `cynyun-schembar/config.json`；资源前缀按 §6 注入 |

---

## 10. 每次改动前的自检表

| 改动类型 | 必须做 |
|---|---|
| 引用了新的 `mindustry.*` 类/方法 | 确认是上游 public API；在 §9 记一行 |
| 引用了任何 Fork 类 | **立即停止**，改到 Adapter 层 |
| 新增贴图/字体/音效 | §6 前缀与同名文件；记一行；提醒 F 端重新生成图集 |
| 新增/修改配置字段 | 提升 `version`；写 `migrate`；记一行 |
| 修改 `BlueprintBar` 的 public 方法签名 | 记一行；同步 F 端 Adapter |
| 新增依赖 / 改 Java 级别 | 记一行；F 端确认 core 兼容 |
| 修改 `mod.hjson` 的 `name` | 六条线一起改（§3）；记一行 |
| 删除 `src/example/` | 同步删除 §2.2 的 exclude 项 |

---

## 11. 常见故障与排查

| 现象 | 首要怀疑 | 处理 |
|---|---|---|
| 面板出现两份 / 配置被反复覆盖 | 两条轨都初始化了 | 检查 `getMod("cynyun-schembar")` 是否返回非 null；检查 jar 的内部名是否仍是旧名 |
| 内置模式下图标全白/全是问号 | 图集前缀或未重新打包图集 | 按 §6 检查注入前缀与文件名 |
| F 端编译报"找不到 mindustry.client.xxx" | 共享代码越界引用了 Fork 类 | 移到 Adapter 层 |
| 上游 jar 编译失败但 F 端能过 | 同上，反向 | 用本仓库 CI 的"上游 jar 编译"job 复现 |
| 配置每次启动都被重置 | 两轨路径不一致，或写入被 tmp 覆盖失败 | 打印 `config.json` 的绝对路径核对；检查磁盘只读/满 |
| 开启后游戏启动即崩 | `init()` 时点过早或异常未捕获 | 移到 `ClientLoadEvent` 之后；`init()` 内 try/catch |
| 面板挡住了建造点击 | `hudGroup` 子元素尺寸过大 | 面板/按钮显式设置尺寸；`touchable` 只作用于子元素 |
| 类重复 / `NoSuchMethodError` | 两边版本不一致 | 固定 submodule commit；升级时两边一起编 |

---

## 12. 禁止项汇总

- 禁止 `import mindustry.client.*` 出现在 `src/` 下（Adapter 层除外）。
- 禁止把 Mindustry/Arc 的类打进任何 jar（依赖一律 `compileOnly`）。
- 禁止调用 `schematics.add()/remove()/load()`，禁止写 `schematics/` 目录，禁止删改 `.msch`。
- 禁止绑定快捷键；交互只允许鼠标点击 / 悬停。
- 禁止在共享代码里读 `mod.hjson`、`Vars.mods` 的元数据做业务判断。
- 禁止把本仓库源码复制进 `core/src`（必须 submodule，单一份源码）。
- 禁止绕过原版的预览生成预算（`schemloadtime`、`maxschematicpreviews`）。
