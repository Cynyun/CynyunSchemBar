package test;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResizeEvent;
import mindustry.mod.Mod;

/**
 * 临时入口：仅用于在游戏内显示一块透明矩形，验证 UI 可行性。
 * 这里不做任何配置、蓝图、交互逻辑。
 */
public class TestMod extends Mod{

    public TestMod(){
        Events.on(ClientLoadEvent.class, e -> {
            if(Vars.ui == null || Vars.ui.hudGroup == null) return;

            BarPanel panel = new BarPanel();

            // hudGroup 为 childrenOnly，且只在游戏中可见
            Vars.ui.hudGroup.addChild(panel);
            panel.toFront();

            // 窗口尺寸变化：贴边的继续贴边，自由态的按比例跟随。用 Core.graphics 而不是
            // hudGroup 的宽高——后者要等下一次布局才更新，事件里读到的还是旧尺寸。
            Events.on(ResizeEvent.class, ev -> {
                if(panel.parent != null) panel.applyLayout();
            });
        });
    }
}
