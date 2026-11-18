package test;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.Element;

/**
 * 纯色块：设置窗的颜色字段与取色器的预设都复用它。
 * 尺寸由外部 cell 决定；颜色读的是传入的 Color 实例，所以换色时不用重建。
 */
public class BarSwatch extends Element{

    public final Color color;

    public BarSwatch(Color color){
        this.color = color;
        // 默认不参与命中，谁需要点击谁自己 set
        touchable = arc.scene.event.Touchable.disabled;
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        Draw.color(color.r, color.g, color.b, a);
        Fill.crect(x, y, width, height);

        Draw.color(1f, 1f, 1f, 0.55f * a);
        Lines.stroke(1f);
        Lines.rect(x + 0.5f, y + 0.5f, width - 0.5f, height - 0.5f);

        Draw.reset();
    }
}
