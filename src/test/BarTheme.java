package test;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.Element;
import arc.scene.ui.Label;
import mindustry.ui.Styles;

/**
 * 本项目的视觉语言：不用任何贴图/九宫格底图，只用 1px 线 + 极淡的填充。
 *
 * 颜色在这里是“活”的：BarConfig 从 config.json 读到的值会写进这几个 Color 实例，
 * 设置窗里的取色器直接改实例本身，所以所有已经画出来的元素下一帧就是新颜色。
 * 以后要改整套观感（换色、换粗细、换角标长度）只动这个类。
 */
public final class BarTheme{

    /** 线的粗细（px）。 */
    public static final float LINE = 1f;
    /** 四角括号的默认臂长（px）。 */
    public static final float BRACKET = 7f;

    /** 强调色：选中态、滚动条滑块、标题、滑条已填充段。 */
    public static final Color accent = Color.valueOf("7fd7e8");
    /** 次要文字色。 */
    public static final Color dim = Color.valueOf("9fb0b6");
    /** 线框色（窗口边、分隔线、滑条轨道）。 */
    public static final Color line = Color.valueOf("ffffff");
    /** 主文字色。 */
    public static final Color text = Color.valueOf("ffffff");
    /** 窗口底色。 */
    public static final Color panel = Color.valueOf("000000");

    /** 窗口底色不透明度（0-1），设置窗滑条实时改，随 config.json 持久化。 */
    public static float panelOpacity = 0.40f;

    private BarTheme(){
    }

    /** 恢复出厂颜色，由 BarConfig.defaults() 调用。 */
    public static void resetDefaults(){
        accent.set(Color.valueOf("7fd7e8"));
        dim.set(Color.valueOf("9fb0b6"));
        line.set(Color.valueOf("ffffff"));
        text.set(Color.valueOf("ffffff"));
        panel.set(Color.valueOf("000000"));
        panelOpacity = 0.40f;
    }

    /** 极淡底色（左下角语义，走 Fill.crect；Fill.rect 是中心语义，别混用）。 */
    public static void fill(float x, float y, float w, float h, float alpha){
        Draw.color(panel.r, panel.g, panel.b, alpha);
        Fill.crect(x, y, w, h);
    }

    /** 强调色铺底（无边框按钮的选中态）。 */
    public static void fillAccent(float x, float y, float w, float h, float alpha){
        Draw.color(accent.r, accent.g, accent.b, alpha);
        Fill.crect(x, y, w, h);
    }

    /**
     * 指针是否真的落在该元素上（考虑遮挡与裁剪）。
     *
     * 不用“自己比矩形”的写法：滚动区里被裁掉的卡片、被别的窗口压住的元素，
     * 矩形仍然覆盖指针，但用户其实没有悬停在它上面。Scene.hit 已经把这层判断做完了。
     */
    public static boolean pointerOver(Element e){
        if(e == null || Core.scene == null) return false;
        Element hit = Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true);
        for(Element p = hit; p != null; p = p.parent){
            if(p == e) return true;
        }
        return false;
    }

    /** 完整矩形描边。加 0.5 偏移让 1px 线正好落在一像素上，不发虚。 */
    public static void frame(float x, float y, float w, float h, float alpha){
        Draw.color(line.r, line.g, line.b, alpha);
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
        // 和 crisp() 用同一个盒子：四条线都落在“盒子内侧的第一个像素”上。
        // 直接在 x 上画会让 1px 线跨 [x-0.5, x+0.5]，一半在盒子外——被父级裁剪时就像被切掉一角。
        float ix = x + 0.5f, iy = y + 0.5f, iw = w - 1f, ih = h - 1f;
        float arm = Math.min(len, Math.min(iw * 0.3f, ih * 0.45f));
        if(iw <= 2f || ih <= 2f || arm <= 1f){
            frame(x, y, w, h, alpha);
            return;
        }

        Draw.color(line.r, line.g, line.b, alpha);
        Lines.stroke(LINE);
        // 左下
        Lines.line(ix, iy + arm, ix, iy);
        Lines.line(ix, iy, ix + arm, iy);
        // 右下
        Lines.line(ix + iw, iy + arm, ix + iw, iy);
        Lines.line(ix + iw, iy, ix + iw - arm, iy);
        // 左上
        Lines.line(ix, iy + ih - arm, ix, iy + ih);
        Lines.line(ix, iy + ih, ix + arm, iy + ih);
        // 右上
        Lines.line(ix + iw, iy + ih - arm, ix + iw, iy + ih);
        Lines.line(ix + iw, iy + ih, ix + iw - arm, iy + ih);
    }

    /** 强调色的四角括号。 */
    public static void bracketsAccent(float x, float y, float w, float h, float alpha){
        float ix = x + 0.5f, iy = y + 0.5f, iw = w - 1f, ih = h - 1f;
        float arm = Math.min(BRACKET, Math.min(iw * 0.3f, ih * 0.45f));
        if(iw <= 2f || ih <= 2f){
            frameAccent(x, y, w, h, alpha);
            return;
        }
        Draw.color(accent.r, accent.g, accent.b, alpha);
        Lines.stroke(LINE);
        Lines.line(ix, iy + arm, ix, iy);
        Lines.line(ix, iy, ix + arm, iy);
        Lines.line(ix + iw, iy + arm, ix + iw, iy);
        Lines.line(ix + iw, iy, ix + iw - arm, iy);
        Lines.line(ix, iy + ih - arm, ix, iy + ih);
        Lines.line(ix, iy + ih, ix + arm, iy + ih);
        Lines.line(ix + iw, iy + ih - arm, ix + iw, iy + ih);
        Lines.line(ix + iw, iy + ih, ix + iw - arm, iy + ih);
    }

    /** 水平分隔线。 */
    public static void hline(float x1, float x2, float y, float alpha){
        Draw.color(line.r, line.g, line.b, alpha);
        Lines.stroke(LINE);
        Lines.line(x1, y + 0.5f, x2, y + 0.5f);
    }

    /** 主文字。 */
    public static BarLabel text(CharSequence s){
        return new BarLabel(s, 0);
    }

    /** 强调色文字。 */
    public static BarLabel accentText(CharSequence s){
        return new BarLabel(s, 1);
    }

    /** 次要文字。 */
    public static BarLabel dimText(CharSequence s){
        return new BarLabel(s, 2);
    }

    /**
     * 会跟着调色板自动变色的 Label。
     *
     * 不能只在创建时 setColor：设置窗换色后，屏幕上已有的标题/说明会保持旧色。
     * 在 draw() 里按 kind 重新取色，代价只有一次 Color.set，可忽略。
     */
    public static class BarLabel extends Label{
        private final int kind;
        private float alpha = 1f;

        BarLabel(CharSequence s, int kind){
            super(s, new Label.LabelStyle(Styles.defaultLabel));
            this.kind = kind;
        }

        /** 整体再乘一个 alpha（0-1），用于禁用/次要层级。 */
        public BarLabel alpha(float alpha){
            this.alpha = alpha;
            return this;
        }

        @Override
        public void draw(){
            Color c = kind == 1 ? BarTheme.accent : kind == 2 ? BarTheme.dim : BarTheme.text;
            float base = kind == 1 ? 1f : kind == 2 ? 0.85f : 0.92f;
            color.set(c.r, c.g, c.b, base * alpha);
            super.draw();
        }
    }
}
