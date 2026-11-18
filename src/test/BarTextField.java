package test;

import arc.Core;
import arc.input.KeyCode;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.ui.Styles;

/**
 * 单行输入框：扒掉原版九宫格底图，只用本项目的四角括号表示边界。
 *
 * 原版 {@link Styles#defaultField} 自带四条不同的底图（常态/聚焦/禁用/非法），
 * 在这个全线条的界面里会显出一块硬边方框，所以拷贝一份样式把底图全部置空——
 * TextField.draw() 对 background/selection/cursor 都做了 null 判断，置空是安全的。
 */
public class BarTextField extends Table{

    public final TextField field;

    public BarTextField(String text, String message, int maxLength){
        field = new TextField(text == null ? "" : text, borderless());
        field.setMaxLength(maxLength);
        field.setMessageText(message);
        field.setAlignment(Align.left);

        add(field).growX().pad(3f, 6f, 3f, 6f);

        touchable = Touchable.enabled;
        // 点在边框和文字之间的留白上也应该聚焦，所以表格自己也要接一下按下事件
        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                focus();
                return false;
            }
        });
    }

    private static TextField.TextFieldStyle borderless(){
        TextField.TextFieldStyle st = new TextField.TextFieldStyle(Styles.defaultField);
        st.background = null;
        st.focusedBackground = null;
        st.disabledBackground = null;
        st.invalidBackground = null;
        return st;
    }

    public void focus(){
        if(Core.scene != null) Core.scene.setKeyboardFocus(field);
    }

    public String text(){
        return field.getText();
    }

    public void text(String s){
        field.setText(s == null ? "" : s);
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        boolean focused = Core.scene != null && Core.scene.getKeyboardFocus() == field;

        super.draw();

        // 聚焦时换成强调色括号，既是“这里能输入”的提示，也不引入新的底图
        if(focused) BarTheme.bracketsAccent(x, y, width, height, 0.9f * a);
        else BarTheme.brackets(x, y, width, height, 0.30f * a);
    }
}
