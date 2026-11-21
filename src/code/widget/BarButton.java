package code;

import arc.Core;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Cell;
import arc.util.Align;
import mindustry.ui.Styles;

/**
 * 自定义按钮：没有底图，全部由 {@link BarTheme} 的线画出来。
 *
 * 两种样式，状态 → 外观：
 *
 * 线框（默认）
 *   常态    只有四角括号（alpha 0.35），几乎不遮挡画面
 *   悬停    括号变亮 + 极淡填充
 *   按下    完整白框 + 更浓填充
 *   选中    完整强调色框 + 强调色文字（配合 ButtonGroup 做单选）
 *
 * 无边框（{@link #borderless(boolean)}）
 *   常态    什么都不画，只有文字
 *   悬停    极淡暗底（与 × 关闭按钮同亮度）
 *   按下    更浓的暗底
 *   选中    极淡强调色底 + 强调色文字，开关状态仍然看得出来
 *
 * 两种样式都支持 stateful（保留选中）/ 一次性（点完复原）与禁用。
 */
public class BarButton extends Button{

    private final Label label;
    private final Cell<Label> labelCell;
    private boolean accent;
    /** true = 像开关一样保留选中状态；false = 一次性按钮，点完立刻回到常态。 */
    private boolean stateful;
    /**
     * true = 无边框样式：不画任何线，只有指针悬停/按下/选中时用极淡底色高亮。
     * 用在标题行这类“功能入口”按钮上（⚙、×），不抢内容的视觉重心。
     */
    private boolean borderless;

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
        // 左右留出 8 逻辑像素，否则文字会顶到四角括号的竖臂上（无边框时会收紧，见 borderless()）
        labelCell = add(label).grow().pad(BarTheme.u(2f), BarTheme.u(8f), BarTheme.u(2f), BarTheme.u(8f));

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

    /** 无边框样式：只靠悬停/按下/选中的底色提示，不画四角括号。 */
    public BarButton borderless(boolean borderless){
        this.borderless = borderless;
        // 没有括号要躲，内边距收回 5 逻辑像素，否则窄按钮里文字会被挤歪
        labelCell.pad(BarTheme.u(1f), BarTheme.u(5f), BarTheme.u(1f), BarTheme.u(5f));
        // 改了 cell 的 pad 就要重算尺寸，否则未显式 size() 的按钮会留着旧的最小宽度
        invalidateHierarchy();
        setSize(getPrefWidth(), getPrefHeight());
        return this;
    }

    public boolean isBorderless(){
        return borderless;
    }

    /** 文字过长时用省略号截断。只在按钮拿到有限宽度时生效（窗口把宽度封顶的场景）。 */
    public BarButton ellipsis(boolean ellipsis){
        label.setEllipsis(ellipsis);
        return this;
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
            label.color.set(BarTheme.text.r, BarTheme.text.g, BarTheme.text.b, 0.28f);
        }else if(checked || accent){
            label.color.set(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, 1f);
        }else if(over){
            label.color.set(BarTheme.text.r, BarTheme.text.g, BarTheme.text.b, 1f);
        }else{
            label.color.set(BarTheme.text.r, BarTheme.text.g, BarTheme.text.b, 0.88f);
        }

        if(borderless){
            // 无边框：常态完全不画东西，只有交互反馈。
            // 选中态给一层极淡强调色底，否则用户看不出开关是开的。
            if(!disabled){
                if(checked) BarTheme.fillAccent(x, y, width, height, 0.14f * a);
                // 数值与 BarCloseButton（×）对齐，两种无边框按钮悬停亮度一致
                float highlight = pressed ? 0.28f : over ? 0.16f : 0f;
                if(highlight > 0f) BarTheme.fill(x, y, width, height, highlight * a);
            }
            // 没有线要压在文字上，直接画子元素即可
            super.draw();
            return;
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
