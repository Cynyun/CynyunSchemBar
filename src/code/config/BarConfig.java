package code;

import arc.files.Fi;
import arc.graphics.Color;
import arc.struct.Seq;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.Jval;
import mindustry.Vars;

/**
 * 本 mod 的持久化状态。刻意不用 Mod.getConfig()：它落在 dataDirectory/mods/<name>/config.json，
 * Fork 内置模式（代码直接进 core）拿不到那个目录。
 *
 * 设计文档 §4.1 约定路径为 dataDirectory/cynyun-schembar/config.json，两种模式共用同一份配置。
 *
 * 颜色本身存在 BarTheme 里的活 Color 实例中，这里只负责读写 JSON：
 * 一份默认值（见 BarTheme.resetDefaults）避免两处定义漂移。
 */
public final class BarConfig{

    public static final int VERSION = 1;
    public static final String FOLDER = "cynyun-schembar", FILE = "config.json";

    /** 测试页的真数据（设置窗要显示，顺带验证持久化链路）。 */
    public static int clicks;
    /** 上次选择的蓝图条目序号，1 起；-1 = 还没选过。 */
    public static int selected = -1;

    /** 各窗口的位置：贴边状态 + 自由态比例。 */
    public static final WindowState mainWindow = new WindowState(), settingsWindow = new WindowState(), pickerWindow = new WindowState();

    /**
     * 蓝图选择窗的尺寸。0 = 还没存过，用 BarSchematicPicker 的默认值。
     * 位置走上面的 pickerWindow，尺寸只有这一个窗口能改，所以单独放两个字段。
     */
    public static float pickerWidth, pickerHeight;

    /**
     * 槽位绑定的蓝图：存文件名（不含扩展名），重启后在 {@code Vars.schematics.all()} 里按名找回。
     * 蓝图没有稳定 id，文件名是唯一在重启后还有意义的东西。null = 未绑定。
     */
    public static String slotFile;
    /** 槽位别名：null / 空串 = 用蓝图原名。 */
    public static String slotAlias;

    /**
     * 悬浮窗的蓝图布局：栏目 → 序列 → 蓝图按钮，整体存在 config.json 的 bar 节点里。
     * 这是真正的业务数据，UI 只负责读写它；改名只改 name，id 永远不动。
     */
    public static final Seq<BarCategory> categories = new Seq<>();
    /** 当前选中的栏目 id；null 或找不到时界面回落到第一个栏目。 */
    public static String activeCategory;

    /** newId 的计数器：同一毫秒里连建两个对象也不会撞 id。 */
    private static int idSeq;

    private BarConfig(){
    }

    /** 一个窗口的位置状态。 */
    public static class WindowState{
        /** false = 还没落过盘，用 defaultSpawn() 的落点。 */
        public boolean present;
        /** 每轴独立：-1 贴左/贴下，0 自由，1 贴右/贴上。 */
        public int anchorX, anchorY;
        /** 自由态时在可移动空间里的比例（0-1）。 */
        public float ux, uy;
    }

    public static Fi file(){
        return Vars.dataDirectory.child(FOLDER + "/" + FILE);
    }

    // ------------------------------------------------------------------ 读

    public static void load(){
        Fi file = file();
        if(!file.exists()) return;

        try{
            Jval root = Jval.read(file.readString());
            Jval s = root.get("settings");
            if(s != null){
                BarTheme.panelOpacity = Mathf.clamp(s.getFloat("opacity", BarTheme.panelOpacity), 0.3f, 1f);

                Jval c = s.get("colors");
                if(c != null){
                    readColor(c, "accent", BarTheme.accent);
                    readColor(c, "dim", BarTheme.dim);
                    readColor(c, "line", BarTheme.line);
                    readColor(c, "text", BarTheme.text);
                    readColor(c, "panel", BarTheme.panel);
                }

                Jval w = s.get("windows");
                if(w != null){
                    readWindow(w.get("main"), mainWindow);
                    readWindow(w.get("settings"), settingsWindow);

                    Jval picker = w.get("picker");
                    if(picker != null){
                        readWindow(picker, pickerWindow);
                        // 0 / 负数视为没存过，交给窗口用默认尺寸
                        pickerWidth = Math.max(0f, picker.getFloat("w", 0f));
                        pickerHeight = Math.max(0f, picker.getFloat("h", 0f));
                    }
                }
            }

            Jval slot = root.get("slot");
            if(slot != null){
                slotFile = emptyToNull(slot.getString("file", null));
                slotAlias = emptyToNull(slot.getString("alias", null));
            }

            Jval bar = root.get("bar");
            if(bar != null) readBar(bar);

            Jval t = root.get("");
            if(t != null){
                clicks = Math.max(0, t.getInt("clicks", clicks));
                selected = t.getInt("selected", selected);
            }
        }catch(Exception e){
            // 配置坏了不能让游戏起不来：备份现场，然后用默认值继续
            Log.err("[cynyun-schembar] config.json 解析失败，已备份原文件并回退默认值", e);
            backup(file);
            defaults();
        }
    }

    /**
     * 生成一个稳定 id。只在配置文件里有意义：栏目/序列/按钮改名时 id 不变，
     * 所以“当前栏目”这类引用不会因为改名断掉。
     */
    public static String newId(String prefix){
        return prefix + Integer.toString(++idSeq, 36) + "-" + Long.toString(Time.millis() % 100000, 36);
    }

    private static String emptyToNull(String s){
        return s == null || s.isEmpty() ? null : s;
    }

    private static void readColor(Jval obj, String name, Color target){
        String hex = obj.getString(name, null);
        if(hex == null || hex.isEmpty()) return;
        target.set(Color.valueOf(hex));
    }

    /**
     * 读栏目树。任何一层缺 id 就整段跳过：id 是唯一引用方式，补一个随机 id 会让
     * “当前栏目”对不上，不如让用户重新加一次（原数据仍在文件里，只是没被用上）。
     */
    private static void readBar(Jval bar){
        categories.clear();
        activeCategory = null;

        Jval cats = bar.get("categories");
        if(cats != null && cats.isArray()){
            for(Jval c : cats.asArray()){
                if(c == null) continue;
                String cid = emptyToNull(c.getString("id", null));
                if(cid == null) continue;
                BarCategory cat = new BarCategory(cid, c.getString("name", "栏目"));

                Jval seqs = c.get("sequences");
                if(seqs != null && seqs.isArray()){
                    for(Jval s : seqs.asArray()){
                        if(s == null) continue;
                        String sid = emptyToNull(s.getString("id", null));
                        if(sid == null) continue;
                        BarSequence seq = new BarSequence(sid, s.getString("name", "序列"));

                        Jval entries = s.get("entries");
                        if(entries != null && entries.isArray()){
                            for(Jval e : entries.asArray()){
                                if(e == null) continue;
                                seq.entries.add(new BarEntry(
                                    emptyToNull(e.getString("file", null)),
                                    emptyToNull(e.getString("title", null))));
                            }
                        }
                        cat.sequences.add(seq);
                    }
                }
                categories.add(cat);
            }
        }

        activeCategory = emptyToNull(bar.getString("active", null));
    }

    private static void readWindow(Jval obj, WindowState st){
        if(obj == null) return;
        st.present = obj.getBool("present", true);
        st.anchorX = Mathf.clamp(obj.getInt("anchorX", st.anchorX), -1, 1);
        st.anchorY = Mathf.clamp(obj.getInt("anchorY", st.anchorY), -1, 1);
        st.ux = Mathf.clamp(obj.getFloat("ux", st.ux), 0f, 1f);
        st.uy = Mathf.clamp(obj.getFloat("uy", st.uy), 0f, 1f);
    }

    // ------------------------------------------------------------------ 写

    /** 只在设置窗关闭、拖动结束、恢复默认、测试数据变化时调用，不每帧写盘。 */
    public static void save(){
        try{
            Jval root = Jval.newObject();
            root.put("version", VERSION);

            Jval s = Jval.newObject();
            s.put("opacity", (double)BarTheme.panelOpacity);

            Jval c = Jval.newObject();
            c.put("accent", hex(BarTheme.accent));
            c.put("dim", hex(BarTheme.dim));
            c.put("line", hex(BarTheme.line));
            c.put("text", hex(BarTheme.text));
            c.put("panel", hex(BarTheme.panel));
            s.put("colors", c);

            Jval w = Jval.newObject();
            w.put("main", writeWindow(mainWindow));
            w.put("settings", writeWindow(settingsWindow));

            // 选择窗多存两个尺寸；没量过（0）就不写，读的时候自然回落到默认值
            Jval picker = writeWindow(pickerWindow);
            if(pickerWidth > 0f) picker.put("w", (double)pickerWidth);
            if(pickerHeight > 0f) picker.put("h", (double)pickerHeight);
            w.put("picker", picker);

            s.put("windows", w);
            root.put("settings", s);

            Jval slot = Jval.newObject();
            if(slotFile != null) slot.put("file", slotFile);
            if(slotAlias != null) slot.put("alias", slotAlias);
            root.put("slot", slot);

            Jval bar = Jval.newObject();
            if(activeCategory != null) bar.put("active", activeCategory);

            Jval cats = Jval.newArray();
            for(BarCategory cat : categories){
                Jval cv = Jval.newObject();
                cv.put("id", cat.id);
                cv.put("name", cat.name);

                Jval seqs = Jval.newArray();
                for(BarSequence seq : cat.sequences){
                    Jval sv = Jval.newObject();
                    sv.put("id", seq.id);
                    sv.put("name", seq.name);

                    Jval entries = Jval.newArray();
                    for(BarEntry e : seq.entries){
                        // 既没绑定也没标题的空按钮不写盘：它没有任何可见内容
                        if(e.file == null && (e.title == null || e.title.isEmpty())) continue;
                        Jval o = Jval.newObject();
                        if(e.file != null) o.put("file", e.file);
                        if(e.title != null) o.put("title", e.title);
                        entries.add(o);
                    }
                    sv.put("entries", entries);
                    seqs.add(sv);
                }
                cv.put("sequences", seqs);
                cats.add(cv);
            }
            bar.put("categories", cats);
            root.put("bar", bar);

            Jval t = Jval.newObject();
            t.put("clicks", clicks);
            t.put("selected", selected);
            root.put("", t);

            Fi file = file();
            file.parent().mkdirs();
            // 先写临时文件再替换：中途崩溃不会留下半截 JSON
            Fi tmp = file.parent().child(FILE + ".tmp");
            tmp.writeString(root.toString(Jval.Jformat.formatted), false, "UTF-8");
            if(file.exists()) file.delete();
            tmp.moveTo(file);
        }catch(Exception e){
            Log.err("[cynyun-schembar] config.json 写入失败", e);
        }
    }

    private static Jval writeWindow(WindowState st){
        Jval o = Jval.newObject();
        o.put("present", st.present);
        o.put("anchorX", st.anchorX);
        o.put("anchorY", st.anchorY);
        o.put("ux", (double)st.ux);
        o.put("uy", (double)st.uy);
        return o;
    }

    /** 固定 6 位小写，Color.valueOf 只认 6 或 8 位 hex，不要输出 "0.8" 这种浮点数。 */
    private static String hex(Color color){
        return String.format("%02x%02x%02x",
            Mathf.clamp((int)(color.r * 255f), 0, 255),
            Mathf.clamp((int)(color.g * 255f), 0, 255),
            Mathf.clamp((int)(color.b * 255f), 0, 255));
    }

    private static void backup(Fi file){
        try{
            if(file.exists()) file.moveTo(file.parent().child(FILE + ".bad-" + Time.millis()));
        }catch(Exception e){
            Log.err("[cynyun-schembar] 备份损坏的 config.json 失败", e);
        }
    }

    // ------------------------------------------------------------------ 默认

    /**
     * 恢复出厂：颜色、测试数据、窗口位置与选择窗尺寸全部清空。
     *
     * 刻意不动 categories / 蓝图绑定：设置窗里的“恢复默认”是给外观用的，
     * 用户辛苦排好的栏目不该被一个配色按钮清掉。要清布局只能删 config.json。
     */
    public static void defaults(){
        BarTheme.resetDefaults();
        clicks = 0;
        selected = -1;
        resetWindow(mainWindow);
        resetWindow(settingsWindow);
        resetWindow(pickerWindow);
        pickerWidth = 0f;
        pickerHeight = 0f;
    }

    private static void resetWindow(WindowState st){
        st.present = false;
        st.anchorX = 0;
        st.anchorY = 0;
        st.ux = 0f;
        st.uy = 0f;
    }
}
