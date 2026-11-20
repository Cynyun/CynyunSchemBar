package code;

import mindustry.game.Schematic;

/**
 * 一个蓝图按钮（设计文档里的 Entry）：引用蓝图库里的一张蓝图 + 用户自定义的标题。
 *
 * 只存文件名，不存 Schematic 对象：蓝图没有稳定 id，重启后只能按名字在
 * {@code Vars.schematics.all()} 里找回。{@link #file} 为 null 表示“还没绑定”，
 * 有名字但库里找不到（被原版界面删了）算“缺失”，配置保留不删。
 */
public class BarEntry{

    /** 蓝图文件名（含扩展名），与 {@link BarConfig#slotFile} 同一种写法。null = 未绑定。 */
    public String file;
    /** 自定义标题。空 = 未命名。 */
    public String title;

    /** 运行时解析结果，不进配置；每次 build 时由 BarLibrary 重新查。 */
    public transient Schematic schematic;

    public BarEntry(){
    }

    public BarEntry(String file, String title){
        this.file = file;
        this.title = title;
    }

    /** 按钮上的字：未绑定 → “未绑定”，绑定但没起名 → “未命名”。 */
    public String displayTitle(){
        if(title != null && !title.isEmpty()) return title;
        return file == null ? "未绑定" : "未命名";
    }

    /** 配置里引用的蓝图在库里还找得到吗（未绑定也算“正常”，不算缺失）。 */
    public boolean missing(){
        return file != null && schematic == null;
    }
}
