package code;

import arc.Core;
import arc.func.Cons;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import mindustry.Vars;

/**
 * 单行文本输入框（栏目/序列改名、蓝图按钮改标题）。
 *
 * 三条关闭路径：取消、确定、Esc；点外面按“取消”处理，避免误触把半截名字写进去。
 * 打开就把键盘焦点给输入框，回车等同“确定”。
 */
public class BarPrompt extends Table{

    /** 逻辑单位（乘 Scl 后才是像素）。 */
    public static final float WIDTH = 300f;
    private static final float BTN_H = 24f, FIELD_H = 24f, PAD = 2f, MARGIN = 10f;

    private final BarTextField field;
    private final Cons<String> onOk;

    private final InputListener outside = new InputListener(){
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
            for(Element e = event.targetActor; e != null; e = e.parent){
                if(e == BarPrompt.this) return false;
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

    public BarPrompt(String title, String text, String message, Cons<String> onOk){
        this.onOk = onOk;

        touchable = Touchable.enabled;
        setTransform(false);

        margin(BarTheme.u(MARGIN));
        top();

        add(BarTheme.accentText(title)).growX().left().row();
        add(new BarDivider()).growX().height(1f)
            .padTop(BarTheme.u(4f)).padBottom(BarTheme.u(6f)).row();

        field = new BarTextField(text, message, BarTextField.MAX_LENGTH);
        // 回车 = 确定：输入框会先收到字符，再走这里，所以文本已经是最新的
        field.field.setTextFieldListener((tf, c) -> {
            if(c == '\n' || c == '\r') confirm();
        });
        add(field).growX().height(BarTheme.u(FIELD_H)).row();

        Table btns = new Table();
        btns.defaults().growX().height(BarTheme.u(BTN_H)).pad(BarTheme.u(PAD));
        btns.add(new BarButton("取消").onClick(this::close));
        btns.add(new BarButton("确定").accent(true).onClick(this::confirm));
        add(btns).growX().padTop(BarTheme.u(MARGIN)).row();

        setSize(BarTheme.u(WIDTH), getPrefHeight());
        show();
    }

    private void show(){
        if(Core.scene == null || Vars.ui == null || Vars.ui.hudGroup == null) return;

        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);
        setPosition(Mathf.round(Mathf.clamp((Core.graphics.getWidth() - width) / 2f, 0f, maxX)),
            Mathf.round(Mathf.clamp((Core.graphics.getHeight() - height) / 2f, 0f, maxY)));

        Vars.ui.hudGroup.addChild(this);
        toFront();
        Core.scene.addCaptureListener(outside);

        // 挂进场景之后再要焦点，否则 Scene 会把焦点又还回去
        Core.app.post(field::focus);
    }

    private void confirm(){
        String value = field.text();
        close();
        onOk.get(value);
    }

    public void close(){
        remove();
    }

    @Override
    public boolean remove(){
        if(Core.scene != null){
            Core.scene.removeCaptureListener(outside);
            if(Core.scene.getKeyboardFocus() == field.field) Core.scene.setKeyboardFocus(null);
        }
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
