package test;

import arc.Core;
import arc.scene.event.Touchable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/**
 * 临时 UI 验证件：线框风格面板 + 自定义按钮 + 自绘滚动条。
 * 只验证组件能不能正常收发输入与绘制，里面的“点击次数 / 开关 / 选择”都是假数据，
 * 真实逻辑（栏目 → 序列 → 蓝图按钮）后续由 cynyunschembar 包接管。
 */
public class BarPanel extends Table{

    /** 面板尺寸与边距，先用常量，后续由设置项驱动。 */
    public static final float WIDTH = 360f, HEIGHT = 300f, MARGIN = 24f, TOP_OFFSET = 72f;

    /** 演示用的状态，纯粹为了肉眼确认按钮回调真的被触发。 */
    private int clicks;
    private boolean toggled;
    private String selected = "无";

    private final Label statusLabel, selectedLabel;

    public BarPanel(){
        // Table 负责子元素布局；关闭变换后 Group.draw 用“父坐标偏移”的方式画子元素，
        // 所以这里的 x/y 仍是屏幕坐标，不必手动换算。
        setTransform(false);
        setSize(WIDTH, HEIGHT);

        // 面板空白处不接收点击（不影响建造），但子元素要能接收，所以是 childrenOnly。
        // 注意不能用 disabled：Group.hit 开头就会因为 disabled 直接返回 null，子按钮全部点不到。
        touchable = Touchable.childrenOnly;

        margin(12f);
        defaults().pad(3f);
        top(); // 剩余空间留在底部，避免内容整体垂直居中后与描边不齐

        // 标题
        add(BarTheme.accentText("C Y N Y U N   ·   S C H E M B A R")).growX().left().height(20f).row();
        add(BarTheme.dimText("UI 组件测试：线框按钮 / 自绘滚动条")).growX().left().height(16f).row();
        add(new BarDivider()).growX().height(1f).padTop(4f).padBottom(6f).row();

        // ① 普通按钮行：四个等宽按钮，最后一个故意禁用，用来确认禁用态样式
        Table bar = new Table();
        bar.defaults().growX().height(30f).pad(2f);
        bar.add(new BarButton("+1").clicked(this::onPlus));
        bar.add(new BarButton("开关").clicked(this::onToggle));
        bar.add(new BarButton("重置").clicked(this::onReset));
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
        // ButtonGroup 做单选：点一个，其余自动取消选中，选中项画强调色框
        ButtonGroup<BarButton> group = new ButtonGroup<>();
        for(int i = 1; i <= 12; i++){
            int index = i;
            BarButton item = new BarButton(String.format("[%02d]  蓝图条目 %d", index, index));
            item.clicked(() -> onSelect(index));
            group.add(item); // group 的 minCheckCount 默认 1，第一项会被自动选中
            list.add(item).growX().height(28f).padBottom(3f).padRight(6f).row();
        }
        add(new BarScroll(list)).growX().height(128f).padTop(2f).row();

        // ④ 列表选择结果
        selectedLabel = BarTheme.dimText("");
        add(selectedLabel).growX().left().height(16f).row();

        selected = "蓝图条目 1";
        refresh();
        placeAtTopLeft();
    }

    /** 锚定屏幕左上角（y 轴向上，顶部留出 HUD 状态栏高度）。窗口尺寸变化时由外部重调。 */
    public void placeAtTopLeft(){
        setPosition(MARGIN, Core.graphics.getHeight() - HEIGHT - TOP_OFFSET);
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        // 面板底色比按钮浓一点，但也只有 40%，不遮挡战场
        BarTheme.fill(x, y, width, height, 0.40f * a);
        BarTheme.frame(x, y, width, height, 0.45f * a);

        // 交给 Table 画子元素（validate + 布局 + 绘制）
        super.draw();
    }

    private void onPlus(){
        clicks++;
        refresh();
    }

    private void onToggle(){
        toggled = !toggled;
        refresh();
    }

    private void onReset(){
        clicks = 0;
        toggled = false;
        refresh();
    }

    private void onSelect(int index){
        selected = "蓝图条目 " + index;
        refresh();
    }

    private void refresh(){
        statusLabel.setText("点击次数：" + clicks + "    开关：" + (toggled ? "开" : "关"));
        selectedLabel.setText("当前选择：" + selected);
    }
}
