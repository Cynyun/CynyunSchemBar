package code;

import arc.graphics.g2d.Draw;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Label;
import arc.util.Align;
import mindustry.ui.Styles;

/**
 * 标签总览里的一行：左边标签名，右边该标签下的蓝图数量。
 *
 * 走 Button 而不是普通 Table，这样“选中”状态由 arc 的 checked 管，
 * 外层统一用 setChecked 让界面和筛选集合保持一致（programmaticChangeEvents 默认关，
 * 程序设置不会反过来触发一次回调）。
 */
public class BarTagRow extends Button{

    private final Label name;

    public BarTagRow(String tag, int count){
        super(new ButtonStyle());
        touchable = Touchable.enabled;

        name = new Label(tag, new Label.LabelStyle(Styles.defaultLabel));
        name.setEllipsis(true);

        Label amount = new Label(String.valueOf(count), new Label.LabelStyle(Styles.defaultLabel));
        amount.color.set(BarTheme.dim.r, BarTheme.dim.g, BarTheme.dim.b, 0.75f);

        add(name).growX().padLeft(BarMetrics.sp8()).left();
        add(amount).padRight(BarMetrics.sp8()).right();

        setSize(getPrefWidth(), getPrefHeight());
    }

    @Override
    public void draw(){
        float a = parentAlpha;

        // 选中态和悬停态用同一种淡底，只靠左侧竖线区分“已经在筛选里”
        if(isChecked()){
            BarTheme.fillAccent(x, y, width, height, (isOver() ? 0.22f : 0.14f) * a);
            BarTheme.fillAccent(x, y, BarMetrics.sp2(), height, 0.9f * a);
        }else if(isOver()){
            BarTheme.fill(x, y, width, height, (isPressed() ? 0.20f : 0.12f) * a);
        }

        name.color.set(
            isChecked() ? BarTheme.accent.r : BarTheme.text.r,
            isChecked() ? BarTheme.accent.g : BarTheme.text.g,
            isChecked() ? BarTheme.accent.b : BarTheme.text.b,
            (isChecked() || isOver() ? 1f : 0.85f) * a);

        super.draw();
    }
}
