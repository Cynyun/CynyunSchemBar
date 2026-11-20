package test;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResizeEvent;
import mindustry.mod.Mod;

/**
 * 临时入口：游戏内显示测试悬浮窗，并挂上设置窗 / 取色器。
 * 真实逻辑（栏目 → 序列 → 蓝图按钮）后续由 cynyunschembar 包接管。
 */
public class TestMod extends Mod{

    private static BarPanel panel;
    private static BarSettingsWindow settings;
    private static BarColorPicker picker;

    public TestMod(){
        Events.on(ClientLoadEvent.class, e -> {
            // 先读配置：颜色要在任何界面创建之前就位
            BarConfig.load();

            if(Vars.ui == null || Vars.ui.hudGroup == null) return;

            panel = new BarPanel();
            // hudGroup 为 childrenOnly，且只在游戏中可见
            Vars.ui.hudGroup.addChild(panel);
            panel.applyLayout();
            panel.toFront();

            // 窗口尺寸变化：贴边的继续贴边，自由态的按比例跟随。用 Core.graphics 而不是
            // hudGroup 的宽高——后者要等下一次布局才更新，事件里读到的还是旧尺寸。
            Events.on(ResizeEvent.class, ev -> {
                if(panel != null && panel.parent != null) panel.applyLayout();
                if(settings != null && settings.parent != null) settings.applyLayout();
                if(picker != null && picker.parent != null) picker.applyLayout();
            });
        });
    }

    // ------------------------------------------------------------------ 设置窗

    public static void openSettings(){
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        if(settings == null) settings = new BarSettingsWindow();
        if(settings.parent == null) Vars.ui.hudGroup.addChild(settings);

        settings.sync();
        settings.applyLayout();
        settings.toFront();
    }

    public static void closeSettings(){
        // 取色器还开着就当作“确定”：颜色本来就是实时应用的
        closePicker();

        if(settings != null){
            settings.remove();
            BarConfig.save();
        }
    }

    // ------------------------------------------------------------------ 取色器

    public static void openPicker(BarColorField field){
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        closePicker();
        picker = new BarColorPicker(field);
        Vars.ui.hudGroup.addChild(picker);
        // 位置依赖字段在场景里的坐标，必须先挂进 hudGroup 再算
        picker.applyLayout();
        picker.toFront();
    }

    public static void closePicker(){
        if(picker != null){
            picker.remove();
            picker = null;
        }
    }

    // ------------------------------------------------------------------ 恢复默认

    /** 恢复默认要连窗口位置一起重置：位置存在 BarConfig 的 WindowState 里。 */
    public static void resetWindows(){
        if(panel != null) panel.resetToDefault();
        if(settings != null) settings.resetToDefault();
    }
}
