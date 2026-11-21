package code;

import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResizeEvent;

/**
 * 启动器：由 {@link Main} 在 mod 构造时调用 {@link #init()}。
 *
 * 负责三件事：读配置、把主悬浮窗（{@link BarPanel}）挂到 hudGroup、在窗口尺寸变化时重排；
 * 另外提供设置窗与取色器的开关（它们不是常驻窗口，按需创建）。
 */
public class Start{

    private static BarPanel panel;
    private static BarSettingsWindow settings;
    private static BarColorPicker picker;

    /** 只能调一次：重复调用会重复注册 ClientLoadEvent。 */
    public static void init(){
        Events.on(ClientLoadEvent.class, e -> {
            // 先读配置：颜色要在任何界面创建之前就位
            BarConfig.load();

            if(Vars.ui == null || Vars.ui.hudGroup == null) return;

            panel = new BarPanel();
            // hudGroup 为 childrenOnly，且只在游戏中可见
            Vars.ui.hudGroup.addChild(panel);
            panel.applyLayout();
            panel.toFront();

            // 手机端排查用：单位是屏幕像素，u(1)=Scl.scl(1) 就是“一个逻辑像素有多大”
            Log.info("[schembar] scl=@ screen=@x@ panel=@x@",
                BarTheme.u(1f), Core.graphics.getWidth(), Core.graphics.getHeight(),
                panel.getWidth(), panel.getHeight());

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
