package code;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import mindustry.Vars;

/**
 * 一个跟着光标弹出的小菜单（栏目 / 序列 / 蓝图按钮的编辑动作都走它）。
 *
 * 关掉的三条路：点菜单里的条目、点菜单外任意处、Esc。点外面关掉时返回 true 把这一下吃掉，
 * 否则会顺手点到下面的按钮（可能直接把蓝图放下去）。
 *
 * 不做成 BarWindow：菜单不该能拖动，也不参与位置持久化。它挂到 hudGroup，
 * 用 Scene 级 capture 监听判断“点在外面”——窗口自己的监听拿不到窗外的事件。
 */
public class BarMenu extends Table{

    /** 逻辑单位（乘 Scl 后才是像素）。 */
    private static final float ITEM_H = 22f;
    /** 最小宽度：两个字一列太窄，菜单看着会像一条缝。 */
    private static final float MIN_W = 104f;
    private static final float PAD = 1f, MARGIN = 6f;

    private final Table list = new Table();

    private final InputListener outside = new InputListener(){
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
            for(Element e = event.targetActor; e != null; e = e.parent){
                if(e == BarMenu.this) return false;
            }

            close();
            return true;
        }

        @Override
        public boolean keyDown(InputEvent event, KeyCode key){
            if(key == KeyCode.escape || key == KeyCode.back){
                close();
                return true;
            }
            return false;
        }
    };

    public BarMenu(String title){
        touchable = Touchable.enabled;
        setTransform(false);

        margin(BarTheme.u(MARGIN));
        top();

        if(title != null && !title.isEmpty()){
            add(BarTheme.dimText(title)).left()
                .pad(BarTheme.u(PAD), BarTheme.u(4f), BarTheme.u(4f), BarTheme.u(4f)).row();
        }

        list.defaults().pad(BarTheme.u(PAD));
        add(list).growX();
    }

    /** 加一行。点完先关菜单再执行动作：动作里常常又要弹新菜单/输入框。 */
    public BarMenu item(String label, Runnable action){
        list.add(new BarButton(label).borderless(true).ellipsis(true).onClick(() -> {
            close();
            action.run();
        })).growX().height(BarTheme.u(ITEM_H)).row();
        return this;
    }

    /** 在屏幕坐标 (x, y) 处弹出，菜单从该点向下展开；越界自动夹回来。 */
    public void showAt(float x, float y){
        if(Core.scene == null || Vars.ui == null || Vars.ui.hudGroup == null) return;

        setSize(Math.max(BarTheme.u(MIN_W), Mathf.round(getPrefWidth())), Mathf.round(getPrefHeight()));

        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);
        setPosition(Mathf.round(Mathf.clamp(x, 0f, maxX)), Mathf.round(Mathf.clamp(y - height, 0f, maxY)));

        Vars.ui.hudGroup.addChild(this);
        toFront();
        Core.scene.addCaptureListener(outside);
    }

    public void close(){
        remove();
    }

    @Override
    public boolean remove(){
        // 一定要摘掉 Scene 上的监听，否则菜单关掉后点哪儿都还会走 close()
        if(Core.scene != null) Core.scene.removeCaptureListener(outside);
        return super.remove();
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        BarTheme.fill(x, y, width, height, 0.72f * a);
        super.draw();
        BarTheme.frame(x, y, width + 1f, height + 1f, 0.55f * a);
    }
}
