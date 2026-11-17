package test;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.ui.Label;
import mindustry.ui.Styles;

/**
 * 本项目的视觉语言：不用任何贴图/九宫格底图，只用 1px 线 + 极淡的填充。
 *
 * 设计文档要求悬浮窗“尽量少遮挡游戏画面”，所以这里刻意做成“线框 + 透明底”：
 * 常态只有四角括号，悬停/按下/选中才逐步加亮加填充，信息量集中在文字上。
 * 以后要改整套观感（换色、换粗细、换角标长度）只动这个类。
 */
public final class BarTheme{

    /** 线的粗细（px）。 */
    public static final float LINE = 1f;
    /** 四角括号的默认臂长（px）。 */
    public static final float BRACKET = 7f;

    /** 强调色：选中态、滚动条滑块、标题。 */
    public static final Color accent = Color.valueOf("7fd7e8");
    /** 次要文字色。 */
    public static final Color dim = Color.valueOf("9fb0b6");

    private BarTheme(){
    }

    /** 极淡底色（左下角语义，走 Fill.crect；Fill.rect 是中心语义，别混用）。 */
    public static void fill(float x, float y, float w, float h, float alpha){
        Draw.color(0f, 0f, 0f, alpha);
        Fill.crect(x, y, w, h);
    }

    /** 完整矩形描边，白色。加 0.5 偏移让 1px 线正好落在一像素上，不发虚。 */
    public static void frame(float x, float y, float w, float h, float alpha){
        Draw.color(1f, 1f, 1f, alpha);
        Lines.stroke(LINE);
        crisp(x, y, w, h);
    }

    /** 完整矩形描边，强调色。 */
    public static void frameAccent(float x, float y, float w, float h, float alpha){
        Draw.color(accent.r, accent.g, accent.b, alpha);
        Lines.stroke(LINE);
        crisp(x, y, w, h);
    }

    private static void crisp(float x, float y, float w, float h){
        // 1px 线覆盖的像素是 [x-0.5, x+0.5]，直接在整数坐标上画会跨两行像素变虚。
        if(w > 2f && h > 2f){
            Lines.rect(x + 0.5f, y + 0.5f, w - 1f, h - 1f);
        }else{
            Lines.rect(x, y, w, h);
        }
    }

    /** 只画四角括号（常态按钮的样式：几乎不遮挡，但边界可辨）。 */
    public static void brackets(float x, float y, float w, float h, float alpha){
        brackets(x, y, w, h, BRACKET, alpha);
    }

    public static void brackets(float x, float y, float w, float h, float len, float alpha){
        float arm = Math.min(len, Math.min(w * 0.3f, h * 0.45f));
        if(arm <= 1f){
            frame(x, y, w, h, alpha);
            return;
        }

        Draw.color(1f, 1f, 1f, alpha);
        Lines.stroke(LINE);
        // 左下
        Lines.line(x, y + arm, x, y);
        Lines.line(x, y, x + arm, y);
        // 右下
        Lines.line(x + w, y + arm, x + w, y);
        Lines.line(x + w, y, x + w - arm, y);
        // 左上
        Lines.line(x, y + h - arm, x, y + h);
        Lines.line(x, y + h, x + arm, y + h);
        // 右上
        Lines.line(x + w, y + h - arm, x + w, y + h);
        Lines.line(x + w, y + h, x + w - arm, y + h);
    }

    /** 强调色的四角括号。 */
    public static void bracketsAccent(float x, float y, float w, float h, float alpha){
        float arm = Math.min(BRACKET, Math.min(w * 0.3f, h * 0.45f));
        Draw.color(accent.r, accent.g, accent.b, alpha);
        Lines.stroke(LINE);
        Lines.line(x, y + arm, x, y);
        Lines.line(x, y, x + arm, y);
        Lines.line(x + w, y + arm, x + w, y);
        Lines.line(x + w, y, x + w - arm, y);
        Lines.line(x, y + h - arm, x, y + h);
        Lines.line(x, y + h, x + arm, y + h);
        Lines.line(x + w, y + h - arm, x + w, y + h);
        Lines.line(x + w, y + h, x + w - arm, y + h);
    }

    /** 水平分隔线。 */
    public static void hline(float x1, float x2, float y, float alpha){
        Draw.color(1f, 1f, 1f, alpha);
        Lines.stroke(LINE);
        Lines.line(x1, y + 0.5f, x2, y + 0.5f);
    }

    /** 白色文字。每个 Label 都持有自己的 LabelStyle 副本，避免共享样式被别处改色。 */
    public static Label text(CharSequence s){
        return styled(s, 1f, 1f, 1f, 0.92f);
    }

    /** 强调色文字。 */
    public static Label accentText(CharSequence s){
        return styled(s, accent.r, accent.g, accent.b, 1f);
    }

    /** 次要文字。 */
    public static Label dimText(CharSequence s){
        return styled(s, dim.r, dim.g, dim.b, 0.85f);
    }

    private static Label styled(CharSequence s, float r, float g, float b, float a){
        Label label = new Label(s, new Label.LabelStyle(Styles.defaultLabel));
        label.setColor(r, g, b, a);
        return label;
    }
}
