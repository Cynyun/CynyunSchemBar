package code;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/**
 * 设置浮窗：只放外观，风格与主面板一致（半透明 + 线框）。
 *
 * 测试阶段只留真正生效的项：没有实现的开关/滑条不放上来，免得点了没反应。
 *
 * 颜色改动即时生效（取色器直接改 BarTheme 的 Color 实例），点“关闭”时才整体写盘；
 * 拖动窗口结束也会写盘，所以关掉游戏再进来位置不会丢。
 */
public class BarSettingsWindow extends BarWindow{

    /** 逻辑单位（乘 Scl 后才是像素）；高度最终由内容 pref 决定。 */
    public static final float WIDTH = 400f, HEIGHT = 236f;
    /** 行高（逻辑单位）。 */
    private static final float HEAD_H = 20f, ROW_H = 18f, BTN_H = 24f, PAD = 2f;

    /** 左侧名字列的宽度（逻辑单位）：四个字的“不透明度”要放得下。 */
    private static final float NAME_W = 100f;

    private BarSlider opacitySlider;
    private Label opacityValue;
    private BarButton resetBtn;

    /** “恢复默认”的二次确认窗口，3 秒不用就自动复原。 */
    private boolean resetArmed;
    private float resetTimer;

    public BarSettingsWindow(){
        super(BarTheme.u(WIDTH), BarTheme.u(HEIGHT));

        margin(BarTheme.u(8f));
        defaults().pad(BarTheme.u(PAD));
        top();

        Table head = new Table();
        head.add(BarTheme.accentText("设 置")).growX().left();
        head.add(new BarCloseButton().onClick(Start::closeSettings)).size(BarTheme.u(18f));
        add(head).growX().height(BarTheme.rowH(HEAD_H, HEAD_H)).row();

        add(new BarDivider()).growX().height(1f)
            .padTop(BarTheme.u(3f)).padBottom(BarTheme.u(4f)).row();

        // ---------------------------------------------------------------- 外观
        add(BarTheme.accentText("· 外观")).growX().left().height(BarTheme.u(15f)).row();

        colorRow("强调色", BarTheme.accent);
        colorRow("次要文字", BarTheme.dim);
        colorRow("线框", BarTheme.line);
        colorRow("主文字", BarTheme.text);
        colorRow("面板底色", BarTheme.panel);

        Table op = new Table();
        op.add(BarTheme.dimText("不透明度")).width(BarTheme.u(NAME_W)).left();
        opacitySlider = new BarSlider(30f, 100f, Mathf.round(BarTheme.panelOpacity * 100f));
        opacityValue = BarTheme.text(Mathf.round(BarTheme.panelOpacity * 100f) + "%");
        opacityValue.touchable = arc.scene.event.Touchable.disabled;
        opacitySlider.changed = () -> {
            BarTheme.panelOpacity = opacitySlider.value() / 100f;
            opacityValue.setText(Mathf.round(opacitySlider.value()) + "%");
        };
        op.add(opacitySlider).growX().height(BarTheme.u(16f))
            .padLeft(BarTheme.u(PAD)).padRight(BarTheme.u(6f));
        op.add(opacityValue).width(BarTheme.u(34f)).right();
        add(op).growX().height(BarTheme.rowH(ROW_H, 22f)).row();

        // ---------------------------------------------------------------- 底部按钮
        Table btns = new Table();
        btns.defaults().growX().height(BarTheme.rowH(BTN_H, 28f)).pad(BarTheme.u(PAD));
        resetBtn = new BarButton("恢复默认").onClick(this::onReset);
        btns.add(resetBtn);
        btns.add(new BarButton("关闭").accent(true).onClick(Start::closeSettings));
        add(btns).growX().padTop(BarTheme.u(4f)).row();

        // 高度取表格自己的 pref：手写常量去猜会因为每行 pad 累加而差几像素，底部按钮被挤出去
        setSize(BarTheme.u(WIDTH), getPrefHeight());

        sync();
    }

    @Override
    protected BarConfig.WindowState state(){
        return BarConfig.settingsWindow;
    }

    @Override
    protected void defaultSpawn(){
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        // 默认屏幕正中；离四边都远大于 SNAP，所以初始是自由态
        setPosition(Mathf.round(Math.max(0f, (w - width) / 2f)), Mathf.round(Math.max(0f, (h - height) / 2f)));
    }

    /** 设置窗也是临时窗：开着的时候常驻面板不能盖住它（手机屏小，一盖就点不到）。 */
    @Override
    protected boolean transientWindow(){
        return true;
    }

    /** 一行颜色字段：左侧固定宽度的名字，右侧可点击的色块 + hex。 */
    private void colorRow(String title, Color color){
        BarColorField field = new BarColorField(title, color);
        field.openPicker = () -> Start.openPicker(field);

        Table row = new Table();
        row.add(BarTheme.dimText(title)).width(BarTheme.u(NAME_W)).left();
        row.add(field).growX().height(BarTheme.rowH(ROW_H, 22f)).padLeft(BarTheme.u(PAD));
        add(row).growX().height(BarTheme.rowH(ROW_H, 22f)).row();
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(resetArmed){
            resetTimer -= delta;
            if(resetTimer <= 0f){
                resetArmed = false;
                resetBtn.text("恢复默认");
            }
        }

    }

    /** 把配置回填到控件上（打开设置窗、恢复默认后调用）。 */
    public void sync(){
        opacitySlider.setValueSilent(Mathf.round(BarTheme.panelOpacity * 100f));
        opacityValue.setText(Mathf.round(BarTheme.panelOpacity * 100f) + "%");

        resetArmed = false;
        resetTimer = 0f;
        resetBtn.text("恢复默认");
    }

    /** 二次点击确认：第一次只改文字，3 秒内再点才真的重置。 */
    private void onReset(){
        if(!resetArmed){
            resetArmed = true;
            resetTimer = 3f;
            resetBtn.text("再点一次确认");
            return;
        }

        resetArmed = false;
        BarConfig.defaults();
        BarConfig.save();
        sync();
        Start.resetWindows();
    }
}
