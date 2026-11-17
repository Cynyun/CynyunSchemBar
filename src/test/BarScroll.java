package test;

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

    /** 滑块粗细。 */
    public static final float THUMB = 2f;
    /** 轨道中心离右边框的距离。 */
    public static final float INSET = 3f;
    /** 内容区必须为滚动条预留的宽度：滑块 2 + 轨道 1 + 两侧各约 1.5 的呼吸空间。 */
    public static final float BAR_WIDTH = 6f;

    public BarScroll(Element content){
        // 把内容包一层再交给 ScrollPane，预留宽度做进组件，调用方不需要记得加 padRight。
        super(reserve(content), Styles.noBarPane);
        setScrollingDisabled(true, false); // 只允许纵向，横向交给外层
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

    /** 内容 + 右侧预留空间的包装层。 */
    private static Element reserve(Element content){
        Table table = new Table();
        table.add(content).grow().padRight(BAR_WIDTH);
        return table;
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
        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, 0.12f * a);
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
