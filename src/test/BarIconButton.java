package test;

import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;

/**
 * 只有图标、没有边框的小按钮：悬停/按下的底色与 {@link BarCloseButton} 完全一致，
 * 这样同一条标题行里的 ⚙、×、列表图标看起来是一个系列。
 *
 * 图标本身用 Drawable（原版的 Icon.xxx 就是 TextureRegionDrawable），不走文字。
 */
public class BarIconButton extends Button{

    private final Image image;

    public BarIconButton(Drawable icon, float iconSize){
        // 空样式：底图全部由 draw() 画
        super(new ButtonStyle());
        touchable = Touchable.enabled;

        image = new Image(icon);
        add(image).size(iconSize);
        setSize(getPrefWidth(), getPrefHeight());
    }

    /**
     * 点击回调。名字不能叫 clicked：Element.clicked(Runnable) 已存在，返回类型不同会冲突。
     */
    public BarIconButton onClick(Runnable r){
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

        image.color.set(BarTheme.dim.r, BarTheme.dim.g, BarTheme.dim.b,
            (isDisabled() ? 0.25f : isOver() ? 1f : 0.7f) * a);

        super.draw();
    }
}
