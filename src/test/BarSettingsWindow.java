package test;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/**
 * 设置浮窗：颜色 + 实时数据两段，风格与主面板一致（半透明 + 线框）。
 *
 * 测试阶段只留真正生效的项：没有实现的开关/滑条不放上来，免得点了没反应。
 *
 * 颜色改动即时生效（取色器直接改 BarTheme 的 Color 实例），点“关闭”时才整体写盘；
 * 拖动窗口结束也会写盘，所以关掉游戏再进来位置不会丢。
 */
public class BarSettingsWindow extends BarWindow{

    public static final float WIDTH = 400f, HEIGHT = 296f;

    private BarSlider opacitySlider;
    private Label opacityValue;
    private BarButton resetBtn;

    private Label clickLabel, selectLabel;
    private int shownClicks = Integer.MIN_VALUE, shownSelected = Integer.MIN_VALUE;

    /** “恢复默认”的二次确认窗口，3 秒不用就自动复原。 */
    private boolean resetArmed;
    private float resetTimer;

    public BarSettingsWindow(){
        super(WIDTH, HEIGHT);

        margin(8f);
        defaults().pad(2f);
        top();

        Table head = new Table();
        head.add(BarTheme.accentText("设 置")).growX().left();
        head.add(new BarCloseButton().onClick(TestMod::closeSettings)).size(18f);
        add(head).growX().height(20f).row();

        add(new BarDivider()).growX().height(1f).padTop(3f).padBottom(4f).row();

        // ---------------------------------------------------------------- 外观
        add(BarTheme.accentText("· 外观")).growX().left().height(15f).row();

        colorRow("强调色", BarTheme.accent);
        colorRow("次要文字", BarTheme.dim);
        colorRow("线框", BarTheme.line);
        colorRow("主文字", BarTheme.text);
        colorRow("面板底色", BarTheme.panel);

        Table op = new Table();
        op.add(BarTheme.dimText("不透明度")).width(100f).left();
        opacitySlider = new BarSlider(30f, 100f, Mathf.round(BarTheme.panelOpacity * 100f));
        opacityValue = BarTheme.text(Mathf.round(BarTheme.panelOpacity * 100f) + "%");
        opacityValue.touchable = arc.scene.event.Touchable.disabled;
        opacitySlider.changed = () -> {
            BarTheme.panelOpacity = opacitySlider.value() / 100f;
            opacityValue.setText(Mathf.round(opacitySlider.value()) + "%");
        };
        op.add(opacitySlider).growX().height(16f).padLeft(2f).padRight(6f);
        op.add(opacityValue).width(34f).right();
        add(op).growX().height(18f).row();

        // ---------------------------------------------------------------- 实时数据
        add(BarTheme.accentText("· 实时数据")).growX().left().height(15f).padTop(2f).row();

        clickLabel = BarTheme.dimText("");
        clickLabel.touchable = arc.scene.event.Touchable.disabled;
        add(clickLabel).growX().left().height(16f).row();

        selectLabel = BarTheme.dimText("");
        selectLabel.touchable = arc.scene.event.Touchable.disabled;
        add(selectLabel).growX().left().height(16f).row();

        // ---------------------------------------------------------------- 底部按钮
        Table btns = new Table();
        btns.defaults().growX().height(24f).pad(2f);
        resetBtn = new BarButton("恢复默认").onClick(this::onReset);
        btns.add(resetBtn);
        btns.add(new BarButton("关闭").accent(true).onClick(TestMod::closeSettings));
        add(btns).growX().padTop(4f).row();

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

    /** 一行颜色字段：左侧固定宽度的名字，右侧可点击的色块 + hex。 */
    private void colorRow(String title, Color color){
        BarColorField field = new BarColorField(title, color);
        field.openPicker = () -> TestMod.openPicker(field);

        Table row = new Table();
        row.add(BarTheme.dimText(title)).width(100f).left();
        row.add(field).growX().height(18f).padLeft(2f);
        add(row).growX().height(18f).row();
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

        if(BarConfig.clicks != shownClicks){
            shownClicks = BarConfig.clicks;
            clickLabel.setText("点击次数：" + BarConfig.clicks);
        }

        if(BarConfig.selected != shownSelected){
            shownSelected = BarConfig.selected;
            selectLabel.setText("上次选择：" + (BarConfig.selected < 0 ? "无" : "蓝图条目 " + BarConfig.selected));
        }
    }

    /** 把配置回填到控件上（打开设置窗、恢复默认后调用）。 */
    public void sync(){
        opacitySlider.setValueSilent(Mathf.round(BarTheme.panelOpacity * 100f));
        opacityValue.setText(Mathf.round(BarTheme.panelOpacity * 100f) + "%");

        resetArmed = false;
        resetTimer = 0f;
        resetBtn.text("恢复默认");

        shownClicks = Integer.MIN_VALUE;
        shownSelected = Integer.MIN_VALUE;
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
        TestMod.resetWindows();
    }
}
