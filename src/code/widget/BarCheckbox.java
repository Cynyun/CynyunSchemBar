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

    private final Label label;
    private boolean checked;
    /** 交互后回调。 */
    public Runnable changed;

    public BarCheckbox(CharSequence text, boolean initial){
        this.checked = initial;
        touchable = Touchable.enabled;

        label = BarTheme.text(text);
        // 左边给方框留位置，文字不压线
        add(label).left().growX().padLeft(BarMetrics.checkBox() + BarMetrics.sp8());

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
        float box = BarMetrics.checkBox();
        float bx = x + BarMetrics.sp2(), by = y + (height - box) / 2f;

        if(checked){
            Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 0.22f * a);
            Fill.crect(bx, by, box, box);
        }

        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, (checked ? 0.9f : 0.45f) * a);
        Lines.stroke(1f);
        Lines.rect(bx + 0.5f, by + 0.5f, box - 1f, box - 1f);

        if(checked){
            Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 0.95f * a);
            Lines.stroke(BarMetrics.checkStroke());
            Lines.line(bx + box * 0.21f, by + box * 0.50f, bx + box * 0.43f, by + box * 0.25f);
            Lines.line(bx + box * 0.43f, by + box * 0.25f, bx + box * 0.79f, by + box * 0.75f);
        }

        Draw.reset();
        super.draw();
    }
}
