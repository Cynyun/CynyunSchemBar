package test;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import mindustry.ui.Styles;

/**
 * 临时 UI 验证件：半透明面板 + 普通按钮 + 滚轮滚动的列表。
 * 只验证 arc 的交互组件能不能在 hudGroup 里正常收发输入，
 * 里面的“点击次数 / 开关 / 选择”都是假数据，真实逻辑后续由 cynyunschembar 包接管。
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
        defaults().pad(4f);
        top(); // 剩余空间留在底部，避免内容整体垂直居中后与描边不齐

        // 标题
        add(new Label("Cynyun SchemBar · UI 测试", Styles.defaultLabel)).growX().left().height(24f).row();

        // ① 普通按钮行：宽度固定，避免按钮被 Table 拉成半屏宽
        Table buttons = new Table();
        buttons.defaults().height(38f).pad(4f);
        buttons.button("点击 +1", Styles.defaultt, this::onPlus).width(100f);
        buttons.button("切换开关", Styles.defaultt, this::onToggle).width(100f);
        buttons.button("重置", Styles.defaultt, this::onReset).width(72f);
        add(buttons).growX().row();

        // ② 状态文字，按钮是否生效看这里
        statusLabel = new Label("", Styles.defaultLabel);
        add(statusLabel).growX().left().height(20f).row();

        // ③ 滚轮列表：内容比可视区高，ScrollPane 才会出现滚动条并响应滚轮
        Table list = new Table();
        list.top();
        for(int i = 1; i <= 12; i++){
            int index = i;
            list.button("蓝图条目 " + index, Styles.defaultt, () -> onSelect(index))
                .growX().height(34f).pad(2f).row();
        }

        ScrollPane pane = new ScrollPane(list, Styles.defaultPane);
        pane.setScrollingDisabled(true, false); // 只留纵向滚动，滚轮上下翻
        pane.setFadeScrollBars(false);          // 不淡出，方便确认滚动条真的存在
        // 必须给固定高度：ScrollPane 的 prefHeight 是“内容全高”（12 项约 456），
        // 若用 grow() 让它自己撑，Table 的 pref 会超过面板高度，列表会溢到面板外面。
        add(pane).growX().height(130f).row();

        // ④ 列表选择结果
        selectedLabel = new Label("", Styles.defaultLabel);
        add(selectedLabel).growX().left().height(20f).row();

        refresh();
        placeAtTopLeft();
    }

    /** 锚定屏幕左上角（y 轴向上，顶部留出 HUD 状态栏高度）。窗口尺寸变化时由外部重调。 */
    public void placeAtTopLeft(){
        setPosition(MARGIN, Core.graphics.getHeight() - HEIGHT - TOP_OFFSET);
    }

    @Override
    public void draw(){
        // 半透明底色。
        // 注意：arc 里 Fill.rect 的 (x, y) 是矩形中心（内部走 Draw.rect，会减掉 w/2、h/2），
        // 而 Lines.rect 的 (x, y) 是左下角。要给一个元素铺底色必须用 crect（corner rect），
        // 否则底色会整体偏移半个宽高，与描边错位。
        Draw.color(0f, 0f, 0f, 0.45f);
        Fill.crect(x, y, width, height);

        // 1px 描边，方便确认边界
        Draw.color(1f, 1f, 1f, 0.35f);
        Lines.stroke(1f);
        Lines.rect(x, y, width, height);

        Draw.reset();

        // 先画自己的底色，再交给 Table 画子元素（validate + 布局 + 绘制）。
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
        selected = "无";
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
