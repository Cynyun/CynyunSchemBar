package code;

import mindustry.Vars;
import mindustry.game.Schematic;

/**
 * 蓝图库查询。配置里只存文件名，这里负责在 {@code Vars.schematics.all()} 里找回，
 * 以及反过来的“把一张蓝图变成配置里的键”。
 *
 * 库可能还没加载完（mod 初始化早于蓝图目录扫描）、也可能用户之后才导入，
 * 所以查不到不是错误，调用方隔一会儿再查一次即可。
 */
public final class BarLibrary{

    private BarLibrary(){
    }

    /** 配置里的键：优先文件名（含扩展名），没有 file 的蓝图退化成名字。 */
    public static String keyOf(Schematic s){
        if(s == null) return null;
        return s.file != null ? s.file.name() : s.name();
    }

    /** 按配置里的键找回蓝图；找不到返回 null。 */
    public static Schematic find(String file){
        if(file == null || file.isEmpty() || Vars.schematics == null) return null;

        for(Schematic s : Vars.schematics.all()){
            if(s.file != null && s.file.name().equals(file)) return s;
        }
        // 老配置/别的入口可能只记了名字（不带 .msch），再按蓝图名兜一次
        for(Schematic s : Vars.schematics.all()){
            if(s.name().equals(file)) return s;
        }
        return null;
    }

    /** 蓝图在库里吗。 */
    public static boolean exists(Schematic s){
        return s != null && Vars.schematics != null && Vars.schematics.all().contains(s);
    }
}
