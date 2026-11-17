package test;

import arc.files.Fi;
import arc.graphics.Color;
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

    /** 界面设置。 */
    public static int maxVisibleCategories = 4;
    public static boolean hideOnUse = true, hoverPreview = true;

    /** 测试页的真数据（设置窗要显示，顺带验证持久化链路）。 */
    public static int clicks;
    /** 上次选择的蓝图条目序号，1 起；-1 = 还没选过。 */
    public static int selected = -1;

    /** 两个窗口的位置：贴边状态 + 自由态比例。 */
    public static final WindowState mainWindow = new WindowState(), settingsWindow = new WindowState();

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
                maxVisibleCategories = Mathf.clamp(s.getInt("maxVisibleCategories", maxVisibleCategories), 1, 10);
                hideOnUse = s.getBool("hideOnUse", hideOnUse);
                hoverPreview = s.getBool("hoverPreview", hoverPreview);

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
                }
            }

            Jval t = root.get("test");
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

    private static void readColor(Jval obj, String name, Color target){
        String hex = obj.getString(name, null);
        if(hex == null || hex.isEmpty()) return;
        target.set(Color.valueOf(hex));
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
            s.put("maxVisibleCategories", maxVisibleCategories);
            s.put("hideOnUse", hideOnUse);
            s.put("hoverPreview", hoverPreview);

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
            s.put("windows", w);
            root.put("settings", s);

            Jval t = Jval.newObject();
            t.put("clicks", clicks);
            t.put("selected", selected);
            root.put("test", t);

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

    /** 恢复出厂：颜色、开关、测试数据、两个窗口的位置全部清空。 */
    public static void defaults(){
        BarTheme.resetDefaults();
        maxVisibleCategories = 4;
        hideOnUse = true;
        hoverPreview = true;
        clicks = 0;
        selected = -1;
        resetWindow(mainWindow);
        resetWindow(settingsWindow);
    }

    private static void resetWindow(WindowState st){
        st.present = false;
        st.anchorX = 0;
        st.anchorY = 0;
        st.ux = 0f;
        st.uy = 0f;
    }
}
