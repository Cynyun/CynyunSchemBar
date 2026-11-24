package code;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.ui.Styles;

/**
 * 单行输入框：扒掉原版九宫格底图，只用一条线表示“这里能输入”。
 *
 * 原版 {@link Styles#defaultField} 自带四条底图（常态/聚焦/禁用/非法），
 * 在这个全线条的界面里会显出一块硬边方框，所以拷贝一份样式把底图全部置空——
 * TextField.draw() 对 background/selection/cursor 都做了 null 判断，置空是安全的。
 *
 * 不能再用四角括号：那是 BarButton 常态的样子，输入框和按钮长得一样会分不清。
 * 两种外观：
 * <ul>
 * <li>{@link Style#UNDERLINE} 底线——下方一条线，最不占地方，适合表单里成排的字段；</li>
 * <li>{@link Style#SIDE} 侧标——左侧一条 2px 竖线，聚焦时整行铺一层极淡强调色，
 *     适合需要强调“这一行是一组数据”的地方（比如详情窗里的别名）。</li>
 * </ul>
 *
 * 实现 {@link BarControl}，否则按在输入框上会被窗口当成拖动热区。
 */
public class BarTextField extends Table implements BarControl{

    /** 两种外观。 */
    public enum Style{
        /** 底部一条线，常态 0.30 / 悬停 0.55 / 聚焦强调色 0.95 且 2px 厚。 */
        UNDERLINE,
        /** 左侧 2px 竖条，聚焦时整行铺 0.08 淡底。 */
        SIDE
    }

    /** 单行输入的统一上限：100 字够放一段话，再多也不适合单行编辑。 */
    public static final int MAX_LENGTH = 100;

    public final TextField field;

    private final Style style;

    /** 鼠标是否停在本组件上。只看鼠标（pointer == -1）；触屏没有悬停这回事。 */
    private boolean over;

    public BarTextField(String text, String message, int maxLength){
        this(text, message, maxLength, Style.UNDERLINE);
    }

    public BarTextField(String text, String message, int maxLength, Style style){
        this.style = style;

        field = new TextField(text == null ? "" : text, borderless());
        field.setMaxLength(maxLength);
        field.setMessageText(message);
        field.setAlignment(Align.left);

        // 文字左留白要避开侧标那道竖线，底线样式则可以贴得紧一点
        float left = style == Style.SIDE ? BarMetrics.fieldInsetSide() : BarMetrics.fieldInset();
        add(field).growX().pad(BarMetrics.fieldPadY(), left, BarMetrics.fieldPadY(), BarMetrics.fieldRightPad());

        touchable = Touchable.enabled;
        // 点在文字和线之间的留白上也应该聚焦，所以表格自己也要接一下按下事件
        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                focus();
                return false;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                if(pointer == -1) over = true;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                if(pointer != -1) return;
                // 指针在本组件内部从表格挪到子输入框（或反过来）也会收到 exit，不能算离开
                if(toActor != null && toActor.isDescendantOf(BarTextField.this)) return;
                over = false;
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

    public Style style(){
        return style;
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        // 聚焦必须比到 TextField 本身：表格拿到键盘焦点没有意义
        boolean focused = Core.scene != null && Core.scene.getKeyboardFocus() == field;

        // 文字色/占位符色跟着调色板走（同 BarTheme.BarLabel 的理由：设置窗换色后已有控件要立刻变）
        TextField.TextFieldStyle st = field.getStyle();
        if(st.fontColor != null) st.fontColor.set(BarTheme.text.r, BarTheme.text.g, BarTheme.text.b, 1f);
        if(st.messageFontColor != null) st.messageFontColor.set(BarTheme.dim.r, BarTheme.dim.g, BarTheme.dim.b, 0.45f);

        // 侧标的聚焦底色要垫在文字下面，所以先铺再画子元素
        if(style == Style.SIDE && focused) BarTheme.fillAccent(x, y, width, height, 0.08f * a);

        // 把子元素裁在自己的盒子里：TextField 自己会按宽度决定露出哪一段文字，
        // 但一旦样式底图被置空、或者组件被塞进更窄的行里，文字就可能画到框外。
        // 裁一刀之后，“输入很多”最多只是左边字符被切掉，绝不会把整个界面撑宽。
        if(clipBegin()){
            super.draw();
            clipEnd();
        }

        if(style == Style.SIDE){
            float alpha = focused ? 0.95f * a : (over ? 0.60f : 0.35f) * a;
            if(focused){
                Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, alpha);
            }else{
                Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, alpha);
            }
            // 上下各留 3 逻辑像素，免得和相邻行的线粘成一条
            float edge = BarMetrics.fieldSideEdge();
            Fill.crect(x, y + edge, BarMetrics.fieldSideW(), Math.max(0f, height - edge * 2f));
        }else{
            float alpha = focused ? 0.95f * a : (over ? 0.55f : 0.30f) * a;
            if(focused){
                // 聚焦时加粗到 2 逻辑像素：线向上长，底部仍贴着原位置
                Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, alpha);
                Lines.stroke(BarMetrics.fieldFocusStroke());
                Lines.line(x + 1f, y + 1f, x + width - 1f, y + 1f);
            }else{
                BarTheme.hline(x + 1f, x + width - 1f, y, alpha);
            }
        }
    }
}
