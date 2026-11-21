package code;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;

/**
 * 关闭按钮：两条 1px 斜线画出来的 ×，不依赖字体里有没有这个字形。
 * 继承 Button 只是白拿 isOver()/isPressed() 与 ChangeEvent 的禁用判定，
 * 底图仍然为空，外观全部自绘。
 */
public class BarCloseButton extends Button{

    public BarCloseButton(){
        super(new ButtonStyle());
        touchable = Touchable.enabled;
        setSize(getPrefWidth(), getPrefHeight());
    }

    /** 点击回调。一次性按钮：点完把 checked 复位，样式只看悬停/按下。 */
    public BarCloseButton onClick(Runnable r){
        changed(() -> {
            r.run();
            // arc 的 Button 每次点击都会翻 checked，下一帧清掉，原因同 BarButton.onClick
            Core.app.post(() -> setChecked(false));
        });
        return this;
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        boolean over = isOver(), pressed = isPressed();

        if(over) BarTheme.fill(x, y, width, height, (pressed ? 0.28f : 0.16f) * a);
        super.draw();

        float m = BarTheme.u(5f);
        Draw.color(BarTheme.line.r, BarTheme.line.g, BarTheme.line.b, (over ? 0.95f : 0.65f) * a);
        Lines.stroke(1.2f);
        Lines.line(x + m, y + m, x + width - m, y + height - m);
        Lines.line(x + m, y + height - m, x + width - m, y + m);

        Draw.reset();
    }
}
