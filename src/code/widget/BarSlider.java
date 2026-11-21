package code;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;

/**
 * 自定义滑条：1px 轨道 + 强调色已填充段 + 方形滑块，风格与线框按钮一致。
 *
 * 数值对外只暴露 value()/setValue()，显示由调用方放到独立 Label 里——
 * 这样同一行可以摆 "R [====----] 127"，也不用在本组件里塞字体绘制。
 */
public class BarSlider extends Element implements BarControl{

    /** 轨道两端留白，滑块圆心不会压到元素边界外。 */
    private static final float PAD = 4f;

    public final float min, max;
    /** 步长，整数档位时给 1。 */
    public float step = 1f;
    /** 值变化回调（拖动过程中每帧都可能触发，实时应用就靠它）。 */
    public Runnable changed;

    private float value;

    public BarSlider(float min, float max, float value){
        this.min = min;
        this.max = max;
        this.value = Mathf.clamp(value, min, max);
        touchable = Touchable.enabled;

        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(button != KeyCode.mouseLeft) return false;
                // 点轨道直接跳值，再按住拖动微调
                setFromX(x);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                setFromX(x);
            }
        });
    }

    public float value(){
        return value;
    }

    /** 值在 0-1 之间的位置。 */
    public float fraction(){
        return max - min <= 0f ? 0f : Mathf.clamp((value - min) / (max - min), 0f, 1f);
    }

    public void setValue(float v){
        float snapped = min + Mathf.round((v - min) / Math.max(1e-4f, step)) * step;
        snapped = Mathf.clamp(snapped, min, max);
        if(Math.abs(snapped - value) < 1e-4f) return;
        value = snapped;
        if(changed != null) changed.run();
    }

    /** 只改显示值、不触发回调：外部同步配置到滑条时用，避免回环。 */
    public void setValueSilent(float v){
        value = Mathf.clamp(v, min, max);
    }

    private void setFromX(float localX){
        float pad = BarTheme.u(PAD);
        float span = Math.max(1f, width - pad * 2f);
        float t = Mathf.clamp((localX - pad) / span, 0f, 1f);
        setValue(min + t * (max - min));
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        float pad = BarTheme.u(PAD);
        float x1 = x + pad, x2 = x + width - pad;
        if(x2 - x1 <= 2f) return;

        float mid = y + height / 2f;
        float t = fraction();
        float kx = x1 + (x2 - x1) * t;

        // 轨道
        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, 0.22f * a);
        Lines.stroke(1f);
        Lines.line(x1, mid + 0.5f, x2, mid + 0.5f);

        // 已填充段
        Draw.color(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 0.55f * a);
        Lines.stroke(1f);
        Lines.line(x1, mid + 0.5f, kx, mid + 0.5f);

        // 滑块：实心小方块，落整数像素
        float kw = BarTheme.u(5f), kh = Math.min(BarTheme.u(14f), height - 2f);
        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, 0.85f * a);
        Fill.crect(Mathf.round(kx - kw / 2f), y + (height - kh) / 2f, kw, kh);

        Draw.reset();
    }
}
