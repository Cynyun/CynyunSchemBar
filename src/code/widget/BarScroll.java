package code;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import mindustry.ui.Styles;

/**
 * 自定义滚动容器：丢掉原版滚动条贴图（用 {@link Styles#noBarPane} 让 ScrollPane 自己什么都不画），
 * 在右边缘画一条 1px 轨道 + 2px 强调色滑块。
 *
 * 两条硬约定（以后所有滚动区都靠它们，不要在调用处重复实现）：
 * 1. 内容区固定预留 {@link #BAR_WIDTH} 的右侧空间，滑块永远不会压住内容。
 * 2. 指针离开本区域时归还滚动焦点，否则 arc 的 ScrollPane 会把滚轮永久锁在自己身上。
 */
public class BarScroll extends ScrollPane{

    /** 滑块粗细（逻辑像素）。 */
    public static final float THUMB = 2f;
    /** 轨道中心离右边框的距离（逻辑像素）。 */
    public static final float INSET = 3f;
    /** 内容区必须为滚动条预留的宽度（逻辑像素）：滑块 2 + 轨道 1 + 两侧各约 1.5 的呼吸空间。 */
    public static final float BAR_WIDTH = 6f;

    /** 上面那些是逻辑像素，真正排版要用这个换算过的值。 */
    public static float barWidth(){
        return BarTheme.u(BAR_WIDTH);
    }

    /** 是否横向滚动（标签行这种一排 chips 的用）。 */
    private final boolean horizontal;

    public BarScroll(Element content){
        this(content, false);
    }

    /**
     * @param horizontal true = 横向滚动，滚动条画在底边，内容预留的是下方而不是右侧空间
     */
    public BarScroll(Element content, boolean horizontal){
        // 把内容包一层再交给 ScrollPane，预留宽度做进组件，调用方不需要记得加 padRight。
        super(reserve(content, horizontal), Styles.noBarPane);
        this.horizontal = horizontal;
        setScrollingDisabled(!horizontal, horizontal); // 只允许一个方向
        setFadeScrollBars(false);
        setOverscroll(false, false);       // 不要橡皮筋回弹，边滚边停更利落

        // arc 的 ScrollPane 只在 enter/mouseMoved/touchDown 里 requestScroll()，自己从不清除；
        // 于是指针离开后滚轮仍然全发给它（Scene.scrolled 的 target 永远是 scrollFocus）。
        // 这里在真正离开时把焦点还回去，进入时仍由它自己的 enter 重新捕获。
        addListener(new InputListener(){
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                if(Core.scene != null && Core.scene.getScrollFocus() == BarScroll.this){
                    Core.scene.setScrollFocus(null);
                }
            }
        });
    }

    /** 内容 + 滚动条预留空间的包装层。 */
    private static Element reserve(Element content, boolean horizontal){
        Table table = new Table();
        if(horizontal) table.add(content).grow().padBottom(barWidth());
        else table.add(content).grow().padRight(barWidth());
        return table;
    }

    /**
     * 最小值必须很小。
     *
     * WidgetGroup.getMinHeight() 默认返回 pref，也就是“整段内容的完整高度”。放进固定高度的
     * 窗口里时，Table 会认为这一行最少要这么高，于是把下面的行全部挤出窗口。
     * 滚动区本来就该被压扁，所以这里按“能滚就行”给一个最小值。
     */
    @Override
    public float getMinHeight(){
        return BarTheme.u(16f);
    }

    @Override
    public float getMinWidth(){
        return BarTheme.u(16f);
    }

    @Override
    public void draw(){
        super.draw();

        if(horizontal){
            drawHorizontal();
            return;
        }

        // 内容没有溢出就不画滚动条，避免面板右边多出一条没意义的竖线
        float inset = BarTheme.u(INSET), pad = BarTheme.u(4f);
        if(getMaxY() <= 1f || width <= inset * 3f || height <= pad * 3f) return;

        float a = parentAlpha;
        float trackX = x + width - inset;
        float bottom = y + pad;
        float top = y + height - pad;
        float trackH = top - bottom;
        if(trackH <= BarTheme.u(8f)) return;

        // 轨道
        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, 0.12f * a);
        Lines.stroke(1f);
        Lines.line(trackX, bottom, trackX, top);

        // 滑块：高度按“可视高度 / 内容总高”缩放，最短 8px 保证能看见
        float content = height + getMaxY();
        float frac = content <= height ? 1f : Mathf.clamp(height / content, 0.08f, 1f);
        float thumbH = Math.max(BarTheme.u(8f), trackH * frac);
        // 滚动百分比 0 = 顶部，屏幕坐标 y 向上，所以要 (1 - p)
        float thumbY = bottom + (trackH - thumbH) * (1f - getVisualScrollPercentY());

        Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 0.8f * a);
        Lines.stroke(BarTheme.u(THUMB));
        Lines.line(trackX, thumbY, trackX, thumbY + thumbH);

        Draw.reset();
    }

    /** 横向滚动条：一条贴底的横线 + 强调色滑块。 */
    private void drawHorizontal(){
        float inset = BarTheme.u(INSET), pad = BarTheme.u(4f);
        if(getMaxX() <= 1f || height <= inset * 3f) return;

        float a = parentAlpha;
        float trackY = y + inset;
        float left = x + pad, right = x + width - pad;
        float trackW = right - left;
        if(trackW <= BarTheme.u(8f)) return;

        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, 0.12f * a);
        Lines.stroke(1f);
        Lines.line(left, trackY, right, trackY);

        float content = width + getMaxX();
        float frac = content <= width ? 1f : Mathf.clamp(width / content, 0.08f, 1f);
        float thumbW = Math.max(BarTheme.u(8f), trackW * frac);
        float thumbX = left + (trackW - thumbW) * getVisualScrollPercentX();

        Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 0.8f * a);
        Lines.stroke(BarTheme.u(THUMB));
        Lines.line(thumbX, trackY, thumbX + thumbW, trackY);

        Draw.reset();
    }
}
