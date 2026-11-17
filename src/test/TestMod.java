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

            // 窗口尺寸变化时重新贴左上角
            Events.on(ResizeEvent.class, r -> panel.placeAtTopLeft());
        });
    }
}
