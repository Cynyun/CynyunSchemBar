package test;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;

/**
 * 三条横线的图标按钮（“打开原版蓝图界面”）。
 *
 * 不用原版贴图 {@code Icon.list}：贴图的像素颜色是烤死的，设置里换色时它不会跟着变。
 * 这里每帧现取 {@link BarTheme#text}，线条的灰度与同一行的文字完全一致；
 * 悬停/按下的底色与 {@link BarCloseButton}、{@link BarIconButton} 同一套数值。
 */
public class BarMenuButton extends Button{

    /** 线条长度。 */
    private static final float LINE = 13f;
    /** 相邻两条线的中心距。 */
    private static final float GAP = 4f;
    /** 线宽：跟正文文字的字重对齐，别比文字粗。 */
    private static final float THICK = 1.4f;

    public BarMenuButton(float size){
        // 空样式：底图全部由 draw() 画
        super(new ButtonStyle());
        touchable = Touchable.enabled;
        setSize(size, size);
    }

    /**
     * 点击回调。名字不能叫 clicked：Element.clicked(Runnable) 已存在，返回类型不同会冲突。
     */
    public BarMenuButton onClick(Runnable r){
        changed(r);
        return this;
    }

    @Override
    public void draw(){
        float a = parentAlpha;

        if(!isDisabled()){
            if(isPressed()) BarTheme.fill(x, y, width, height, 0.28f * a);
            else if(isOver()) BarTheme.fill(x, y, width, height, 0.16f * a);
        }

        Draw.color(BarTheme.text.r, BarTheme.text.g, BarTheme.text.b,
            (isDisabled() ? 0.25f : isOver() ? 1f : 0.7f) * a);
        Lines.stroke(THICK);

        float cx = x + width / 2f;
        float cy = y + height / 2f;
        for(int i = -1; i <= 1; i++){
            float ly = Mathf.round(cy + i * GAP);
            Lines.line(cx - LINE / 2f, ly, cx + LINE / 2f, ly);
        }

        Draw.reset();
    }
}
