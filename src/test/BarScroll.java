package test;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.ui.ScrollPane;
import mindustry.ui.Styles;

/**
 * 自定义滚动容器：丢掉原版滚动条贴图（用 {@link Styles#noBarPane} 让 ScrollPane 自己什么都不画），
 * 在右边缘画一条 1px 轨道 + 2px 强调色滑块。
 *
 * 滚轮逻辑完全沿用 ScrollPane（scrolled() 里就是 setScrollY(amountY + getMouseWheelY()*sy)），
 * 这里只负责“画得符合本项目”。
 */
public class BarScroll extends ScrollPane{

    /** 滑块粗细。 */
    public static final float THUMB = 2f;
    /** 轨道离右边框的距离。 */
    public static final float INSET = 3f;

    public BarScroll(Element widget){
        super(widget, Styles.noBarPane);
        setScrollingDisabled(true, false); // 只允许纵向，横向交给外层
        setFadeScrollBars(false);
        setOverscroll(false, false);       // 不要橡皮筋回弹，边滚边停更利落
    }

    @Override
    public void draw(){
        super.draw();

        // 内容没有溢出就不画滚动条，避免面板右边多出一条没意义的竖线
        if(getMaxY() <= 1f || width <= 8f || height <= 12f) return;

        float a = parentAlpha;
        float trackX = x + width - INSET;
        float bottom = y + 4f;
        float top = y + height - 4f;
        float trackH = top - bottom;
        if(trackH <= 8f) return;

        // 轨道
        Draw.color(1f, 1f, 1f, 0.12f * a);
        Lines.stroke(1f);
        Lines.line(trackX, bottom, trackX, top);

        // 滑块：高度按“可视高度 / 内容总高”缩放，最短 8px 保证能看见
        float content = height + getMaxY();
        float frac = content <= height ? 1f : Mathf.clamp(height / content, 0.08f, 1f);
        float thumbH = Math.max(8f, trackH * frac);
        // 滚动百分比 0 = 顶部，屏幕坐标 y 向上，所以要 (1 - p)
        float thumbY = bottom + (trackH - thumbH) * (1f - getVisualScrollPercentY());

        Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 0.8f * a);
        Lines.stroke(THUMB);
        Lines.line(trackX, thumbY, trackX, thumbY + thumbH);

        Draw.reset();
    }
}
