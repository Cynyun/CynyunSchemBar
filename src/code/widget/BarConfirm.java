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
 * 删除栏目/序列前的确认框。样式与其它窗口一致，只多一句“删了就没了”。
 *
 * 点外面 = 取消（不是确认），Esc 也取消：删除是不可逆动作，误触不该生效。
 */
public class BarConfirm extends Table{

    private final InputListener outside = new InputListener(){
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
            for(Element e = event.targetActor; e != null; e = e.parent){
                if(e == BarConfirm.this) return false;
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

    public BarConfirm(String title, String message, String okText, Runnable onOk){
        touchable = Touchable.enabled;
        setTransform(false);

        margin(BarMetrics.dialogMargin());
        top();

        add(BarTheme.accentText(title)).growX().left().row();
        add(new BarDivider()).growX().height(BarMetrics.hairline())
            .padTop(BarMetrics.dialogSepTop()).padBottom(BarMetrics.dialogSepBottom()).row();

        Table body = new Table();
        body.add(BarTheme.dimText(message)).growX().left().row();
        add(body).growX().row();

        Table btns = new Table();
        btns.defaults().growX().height(BarMetrics.dialogBtnH()).pad(BarMetrics.sp2());
        btns.add(new BarButton("取消").onClick(this::close));
        btns.add(new BarButton(okText).accent(true).onClick(() -> {
            close();
            onOk.run();
        }));
        add(btns).growX().padTop(BarMetrics.dialogBtnRowPad()).row();

        setSize(BarMetrics.dialogW(), getPrefHeight());
        show();
    }

    private void show(){
        if(Core.scene == null || Vars.ui == null || Vars.ui.hudGroup == null) return;

        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);
        setPosition(Mathf.round((Core.graphics.getWidth() - width) / 2f), Mathf.round((Core.graphics.getHeight() - height) / 2f));
        setPosition(Mathf.clamp(x, 0f, maxX), Mathf.clamp(y, 0f, maxY));

        Vars.ui.hudGroup.addChild(this);
        toFront();
        Core.scene.addCaptureListener(outside);
    }

    public void close(){
        remove();
    }

    @Override
    public boolean remove(){
        if(Core.scene != null) Core.scene.removeCaptureListener(outside);
        return super.remove();
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        BarTheme.fill(x, y, width, height, 0.78f * a);
        super.draw();
        BarTheme.frame(x, y, width + 1f, height + 1f, 0.55f * a);
    }
}
