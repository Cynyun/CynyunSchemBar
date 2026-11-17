package test;

import arc.Core;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Label;
import arc.util.Align;
import mindustry.ui.Styles;

/**
 * 自定义按钮：没有底图，全部由 {@link BarTheme} 的线画出来。
 *
 * 状态 → 外观：
 *   常态    只有四角括号（alpha 0.35），几乎不遮挡画面
 *   悬停    括号变亮 + 极淡填充
 *   按下    完整白框 + 更浓填充
 *   选中    完整强调色框 + 强调色文字（配合 ButtonGroup 做单选）
 *   禁用    括号更暗、文字半透明，且点击无效
 */
public class BarButton extends Button{

    private final Label label;
    private boolean accent;
    /** true = 像开关一样保留选中状态；false = 一次性按钮，点完立刻回到常态。 */
    private boolean stateful;

    public BarButton(CharSequence text){
        this(text, false);
    }

    public BarButton(CharSequence text, boolean accent){
        // 空的 ButtonStyle：up/down/over 全为 null，Button.draw 会把 background 设成 null，
        // 于是不会画任何底图；外观完全交给下面的 draw()。
        super(new ButtonStyle());
        this.accent = accent;

        label = new Label(text, new Label.LabelStyle(Styles.defaultLabel));
        label.setAlignment(Align.center);
        // 左右留出 8px，否则文字会顶到四角括号的竖臂上
        add(label).grow().pad(2f, 8f, 2f, 8f);

        touchable = Touchable.enabled;
        setSize(getPrefWidth(), getPrefHeight());
    }

    /** 常驻强调色（用于主操作，不依赖选中态）。 */
    public BarButton accent(boolean accent){
        this.accent = accent;
        return this;
    }

    /** 设为 true 后按钮保留选中状态（开关类）；默认 false，点完即恢复。 */
    public BarButton stateful(boolean stateful){
        this.stateful = stateful;
        return this;
    }

    public boolean isStateful(){
        return stateful;
    }

    /**
     * 点击回调。内部走 arc 的 ChangeEvent，禁用状态下不会触发。
     * 注意不能重载成 clicked(Runnable)：arc 的 Element 已有同名方法且返回 ClickListener，
     * 这里返回 BarButton 会与它冲突（return type is not compatible）。
     */
    public BarButton onClick(Runnable r){
        changed(() -> {
            r.run();
            if(!stateful && isChecked()){
                // arc 的 Button 每次点击都会把 checked 取反（Button.java:92），一次性按钮必须复位。
                // 不能在事件里直接 setChecked(false)：setChecked 末尾的
                // "if(fire(changeEvent)) this.isChecked = !isChecked" 会把复位又翻回去，
                // 所以放到事件派发结束后，下一帧再清（programmaticChangeEvents 默认 false，不会再触发 ChangeEvent）。
                Core.app.post(() -> setChecked(false));
            }
        });
        return this;
    }

    public BarButton text(CharSequence text){
        label.setText(text);
        return this;
    }

    public CharSequence text(){
        return label.getText();
    }

    @Override
    public void draw(){
        validate();

        float a = parentAlpha;
        boolean disabled = isDisabled(), over = isOver(), pressed = isPressed(), checked = stateful && isChecked();

        // 文字色
        if(disabled){
            label.color.set(1f, 1f, 1f, 0.28f);
        }else if(checked || accent){
            label.color.set(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 1f);
        }else if(over){
            label.color.set(Color.white);
        }else{
            label.color.set(1f, 1f, 1f, 0.88f);
        }

        // 底色：只在不悬停/不按下时保持几乎全透明
        float fill = disabled ? 0f : pressed ? 0.30f : over ? 0.20f : checked ? 0.14f : 0.06f;
        if(fill > 0f) BarTheme.fill(x, y, width, height, fill * a);

        // 先画文字，再画线框，保证 1px 边不被文字压住
        super.draw();

        if(disabled){
            BarTheme.brackets(x, y, width, height, 0.12f * a);
        }else if(pressed){
            BarTheme.frame(x, y, width, height, 0.95f * a);
        }else if(checked || accent){
            BarTheme.frameAccent(x, y, width, height, (over ? 1f : 0.85f) * a);
        }else if(over){
            BarTheme.brackets(x, y, width, height, 0.85f * a);
        }else{
            BarTheme.brackets(x, y, width, height, 0.35f * a);
        }
    }
}
