package code;

import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/**
 * 颜色字段：色块 + 只读 hex，点击打开取色器。
 *
 * 关键点：它持有的是 BarTheme 里那个 Color 实例本身，不是副本。
 * 取色器实时改的是同一个实例，所以拖动滑条时整个界面立刻变色，不需要额外的通知机制。
 */
public class BarColorField extends Table implements BarControl{

    public final String title;
    public final Color color;
    /** 点击回调，由设置窗接到 Start.openPicker。 */
    public Runnable openPicker;

    private final BarSwatch swatch;
    private final Label hex;
    private int shown = Integer.MIN_VALUE;

    public BarColorField(String title, Color color){
        this.title = title;
        this.color = color;

        touchable = Touchable.enabled;

        swatch = new BarSwatch(color);
        hex = BarTheme.dimText("");
        hex.touchable = Touchable.disabled;

        add(swatch).size(14f).padRight(6f);
        add(hex).left().growX();

        clicked(() -> {
            if(openPicker != null) openPicker.run();
        });
    }

    @Override
    public void act(float delta){
        super.act(delta);

        int rgba = color.rgba8888();
        if(rgba == shown) return;
        shown = rgba;
        hex.setText(hexString());
    }

    private String hexString(){
        return String.format("#%02X%02X%02X",
            Math.round(color.r * 255f),
            Math.round(color.g * 255f),
            Math.round(color.b * 255f));
    }
}
