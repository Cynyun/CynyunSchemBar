package code;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/**
 * 自定义复选框：12x12 方框 + 强调色勾，点整行都能切换。
 */
public class BarCheckbox extends Table implements BarControl{

    private static final float BOX = 12f;

    private final Label label;
    private boolean checked;
    /** 交互后回调。 */
    public Runnable changed;

    public BarCheckbox(CharSequence text, boolean initial){
        this.checked = initial;
        touchable = Touchable.enabled;

        label = BarTheme.text(text);
        // 左边给方框留位置，文字不压线
        add(label).left().growX().padLeft(BOX + 8f);

        clicked(() -> {
            this.checked = !this.checked;
            if(changed != null) changed.run();
        });
    }

    public boolean checked(){
        return checked;
    }

    /** 只改状态不回调：设置窗同步配置时用。 */
    public void setChecked(boolean checked){
        this.checked = checked;
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        float bx = x + 2f, by = y + (height - BOX) / 2f;

        if(checked){
            Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 0.22f * a);
            Fill.crect(bx, by, BOX, BOX);
        }

        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, (checked ? 0.9f : 0.45f) * a);
        Lines.stroke(1f);
        Lines.rect(bx + 0.5f, by + 0.5f, BOX - 1f, BOX - 1f);

        if(checked){
            Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 0.95f * a);
            Lines.stroke(1.6f);
            Lines.line(bx + 2.5f, by + 6f, bx + 5.2f, by + 3f);
            Lines.line(bx + 5.2f, by + 3f, bx + 9.5f, by + 9f);
        }

        Draw.reset();
        super.draw();
    }
}
