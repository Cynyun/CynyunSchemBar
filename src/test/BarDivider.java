package test;

import arc.scene.Element;

/**
 * 1px 水平分隔线。宽度由外部 cell 的 growX 决定，高度固定 1px（cell 上设 height(1f)）。
 */
public class BarDivider extends Element{

    @Override
    public void draw(){
        BarTheme.hline(x, x + width, y + height * 0.5f, 0.16f * parentAlpha);
    }
}
