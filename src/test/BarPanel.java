package test;

import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/**
 * 主悬浮窗：标题行 + 按钮行 + 滚轮列表，全部用本项目的线框组件。
 * 里面的“点击次数 / 开关 / 选择”仍是测试数据，但已经写进 config.json，
 * 设置窗要读它们，顺带验证整条持久化链路。
 */
public class BarPanel extends BarWindow{

    /** 面板尺寸与边距，先用常量，后续由设置项驱动。 */
    public static final float WIDTH = 360f, HEIGHT = 300f, MARGIN = 24f, TOP_OFFSET = 72f;

    /** 唯一保留状态的按钮（开关），状态由按钮自己存，面板只读。 */
    private final BarButton switchBtn;
    private final Label statusLabel, selectedLabel;

    private boolean toggled;
    private int shownClicks = Integer.MIN_VALUE, shownSelected = Integer.MIN_VALUE;

    public BarPanel(){
        super(WIDTH, HEIGHT);

        margin(10f);
        defaults().pad(3f);
        top(); // 剩余空间留在底部，避免内容整体垂直居中后与描边不齐

        // 标题行：右侧是“设置”瞬时按钮（不保存状态）
        Table head = new Table();
        head.add(BarTheme.accentText("C Y N Y U N   ·   S C H E M B A R")).growX().left();
        // 无边框入口按钮：常态不画任何东西，悬停才浮出一层极淡底色
        head.add(new BarButton("⚙").borderless(true).onClick(TestMod::openSettings)).size(22f, 18f);
        add(head).growX().height(20f).row();

        add(BarTheme.dimText("UI 组件测试：线框按钮 / 自绘滚动条")).growX().left().height(16f).row();
        add(new BarDivider()).growX().height(1f).padTop(4f).padBottom(6f).row();

        // ① 普通按钮行：四个等宽按钮，最后一个故意禁用，用来确认禁用态样式
        Table bar = new Table();
        bar.defaults().growX().height(30f).pad(2f);
        // 一次性按钮：点完恢复常态（默认），只有开关用 stateful(true) 保留选中
        bar.add(new BarButton("+1").onClick(this::onPlus));
        switchBtn = new BarButton("开关").stateful(true).onClick(this::onToggle);
        bar.add(switchBtn);
        bar.add(new BarButton("重置").onClick(this::onReset));
        BarButton disabled = new BarButton("禁用");
        disabled.setDisabled(true);
        bar.add(disabled);
        add(bar).growX().row();

        // ② 状态文字，按钮是否生效看这里
        statusLabel = BarTheme.dimText("");
        add(statusLabel).growX().left().height(16f).row();

        // ③ 滚轮列表：BarScroll 自带 1px 轨道 + 2px 强调色滑块
        Table list = new Table();
        list.top();
        // 列表项也是一次性按钮：不保留选中状态，当前选择只体现在下方文字里。
        // 右侧空间由 BarScroll 统一预留，这里不要再加 padRight，否则会双重内缩。
        for(int i = 1; i <= 12; i++){
            int index = i;
            BarButton item = new BarButton(String.format("[%02d]  蓝图条目 %d", index, index));
            item.onClick(() -> onSelect(index));
            list.add(item).growX().height(28f).padBottom(3f).row();
        }
        add(new BarScroll(list)).growX().height(128f).padTop(2f).row();

        // ④ 列表选择结果
        selectedLabel = BarTheme.dimText("");
        add(selectedLabel).growX().left().height(16f).row();

        refresh();
        applyLayout();
    }

    @Override
    protected BarConfig.WindowState state(){
        return BarConfig.mainWindow;
    }

    @Override
    protected void defaultSpawn(){
        // 屏幕左上角，y 轴向上，顶部留出 HUD 状态栏高度
        setPosition(MARGIN, Mathf.round(Math.max(0f, Core.graphics.getHeight() - height - TOP_OFFSET)));
    }

    @Override
    public void act(float delta){
        super.act(delta);
        // 设置窗里可能改了测试数据（恢复默认会把计数清零），这里跟着刷新
        if(BarConfig.clicks != shownClicks || BarConfig.selected != shownSelected){
            refresh();
        }
    }

    private void onPlus(){
        BarConfig.clicks++;
        BarConfig.save();
        refresh();
    }

    private void onToggle(){
        // ChangeEvent 触发时 checked 已经翻转过，直接读按钮状态即可
        toggled = switchBtn.isChecked();
        refresh();
    }

    private void onReset(){
        BarConfig.clicks = 0;
        toggled = false;
        switchBtn.setChecked(false);
        BarConfig.save();
        refresh();
    }

    private void onSelect(int index){
        BarConfig.selected = index;
        BarConfig.save();
        refresh();
    }

    private void refresh(){
        shownClicks = BarConfig.clicks;
        shownSelected = BarConfig.selected;

        statusLabel.setText("点击次数：" + BarConfig.clicks + "    开关：" + (toggled ? "开" : "关"));
        selectedLabel.setText("当前选择：" + (BarConfig.selected < 0 ? "无" : "蓝图条目 " + BarConfig.selected));
    }
}
