package code;

import mindustry.game.Schematic;

/**
 * “能从蓝图库里挑一张蓝图”的组件实现这个接口。
 *
 * 选择窗（BarSchematicPicker）原来直接吃 BarSlot；真正的悬浮窗里绑定入口在
 * 蓝图按钮上（点 “+” 往某条序列里加一条），所以把这三个方法抽成接口，
 * 选择窗不再依赖具体是谁在用它。
 */
public interface BarPickerHost{

    /** 当前已经绑定的蓝图（用来在选择窗里高亮）；没有就返回 null。 */
    Schematic currentSchematic();

    /** 用户在选择窗里点了一张蓝图。 */
    void picked(Schematic schematic);

    /** 关掉选择窗。 */
    void closePicker();
}
