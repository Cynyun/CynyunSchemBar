package code;

import arc.scene.Element;

/**
 * 1px 水平分隔线。宽度由外部 cell 的 growX 决定，高度固定 1px（cell 上设 height(1f)）。
 */
public class BarDivider extends Element{

    @Override
    public void draw(){
        // hline 自己会 +0.5 做像素对齐，这里传元素自身的 y（行高 1px），不要再加 height/2，
        // 否则线会落在两像素之间发虚。
        BarTheme.hline(x, x + width, y, 0.16f * parentAlpha);
    }
}
