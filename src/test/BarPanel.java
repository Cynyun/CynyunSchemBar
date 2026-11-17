package test;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
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

    /** 拖动状态：只有按住面板自身的空白处才会进入。位置只存在内存里，重启游戏回到初始位置。 */
    private boolean dragging;
    private float dragLastX, dragLastY;

    /** 吸附阈值：松手时离边界 ≤ 这个距离就贴边。贴边目标是 0 像素，即真正贴住窗口边。 */
    public static final float SNAP = 10f;
    /** 吸附状态，每轴独立：-1 = 贴左/贴下，0 = 自由（按比例跟随窗口），1 = 贴右/贴上。 */
    private int anchorX, anchorY;
    /** 自由态的位置比例，按“可移动空间” W - width 归一化：窗口变化时 x = ux * (W - width)。 */
    private float ux, uy;

    private final Label statusLabel, selectedLabel;
    /** 唯一保留状态的按钮（开关），状态由按钮自己存，面板只读。 */
    private final BarButton switchBtn;

    public BarPanel(){
        // Table 负责子元素布局；关闭变换后 Group.draw 用“父坐标偏移”的方式画子元素，
        // 所以这里的 x/y 仍是屏幕坐标，不必手动换算。
        setTransform(false);
        setSize(WIDTH, HEIGHT);

        // 必须是 enabled：childrenOnly 会让 Element.hit 在空白处直接返回 null，事件到不了面板，
        // 拖动就无从谈起。代价是面板矩形内的空白处不再允许穿透建造——悬浮窗本来也不该穿透。
        touchable = Touchable.enabled;
        installDrag();

        margin(10f);
        defaults().pad(3f);
        top(); // 剩余空间留在底部，避免内容整体垂直居中后与描边不齐

        // 标题
        add(BarTheme.accentText("C Y N Y U N   ·   S C H E M B A R")).growX().left().height(20f).row();
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
        placeAtTopLeft();
        // 出生点也是“自由态”，把坐标换成比例记下来，第一次改窗口大小时才知道该放哪
        captureAnchor();
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
        BarTheme.frame(x, y, width + 1f, height + 1f, 0.45f * a); // 增加一单位 确保右侧上侧边框能包住

        // 交给 Table 画子元素（validate + 布局 + 绘制）
        super.draw();
    }

    private void onPlus(){
        clicks++;
        refresh();
    }

    private void onToggle(){
        // ChangeEvent 触发时 checked 已经翻转过，直接读按钮状态即可
        toggled = switchBtn.isChecked();
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

    // ------------------------------------------------------------------ 拖动

    /** 装上拖动监听。事件由命中的子元素冒泡上来，这里只判断它是不是“交互件”。 */
    private void installDrag(){
        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(pointer != 0 || button != KeyCode.mouseLeft) return false;
                if(isInteractive(event.targetActor)) return false;

                // 用 stage 坐标的“增量”移动，不去换算父坐标：hudGroup 若带缩放/偏移，增量依然成立
                dragging = true;
                dragLastX = event.stageX;
                dragLastY = event.stageY;
                return true; // 返回 true 后 Scene 会 addTouchFocus，鼠标拖出面板也能继续收 move 事件
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                if(!dragging) return;
                dragBy(event.stageX - dragLastX, event.stageY - dragLastY);
                dragLastX = event.stageX;
                dragLastY = event.stageY;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                dragging = false;
                // 松手才判定贴边，拖动中途不判定，否则经过边界时会来回抖
                captureAnchor();
            }
        });
    }

    /** 命中的元素只要在按钮 / 滚动区里（含其子元素），就算交互件，不触发拖动。 */
    private boolean isInteractive(Element target){
        for(Element e = target; e != null && e != this; e = e.parent){
            if(e instanceof Button || e instanceof ScrollPane) return true;
        }
        return false;
    }

    /** 按增量移动，并夹在屏幕内，避免被拖到看不见的地方。 */
    private void dragBy(float dx, float dy){
        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);
        // 必须落到整数像素：x/y 带小数时，1px 描边和底色会被光栅化到不同像素上，
        // 看起来就是“一拖动，外框相对底色偏了 1px”。
        setPosition(Mathf.round(Mathf.clamp(x + dx, 0f, maxX)), Mathf.round(Mathf.clamp(y + dy, 0f, maxY)));
    }

    /** 窗口尺寸变化后调用：贴边的继续贴边，自由的按比例跟随，最后统一夹在窗口内。 */
    public void applyLayout(){
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        // 最小化/切换全屏的瞬间可能拿到 0 尺寸，直接忽略这一次
        if(w <= 0f || h <= 0f) return;

        float availW = Math.max(0f, w - width);
        float availH = Math.max(0f, h - height);

        // 贴边轴直接用边界值，自由轴按比例缩放；Mathf.round 保证 1px 描边与底色落在同一像素列
        float nx = anchorX < 0 ? 0f : anchorX > 0 ? availW : Mathf.round(ux * availW);
        float ny = anchorY < 0 ? 0f : anchorY > 0 ? availH : Mathf.round(uy * availH);

        setPosition(Mathf.clamp(nx, 0f, availW), Mathf.clamp(ny, 0f, availH));
    }

    /**
     * 记录当前位置：离边界 ≤ SNAP 的轴标记为贴边，其余轴存归一化比例（基于可移动空间而非整屏宽，
     * 这样比例 0/1 正好对应两条边界）。贴边的轴立刻吸到边界上。
     */
    private void captureAnchor(){
        float availW = Math.max(0f, Core.graphics.getWidth() - width);
        float availH = Math.max(0f, Core.graphics.getHeight() - height);

        ux = availW > 0f ? Mathf.clamp(x / availW, 0f, 1f) : 0f;
        uy = availH > 0f ? Mathf.clamp(y / availH, 0f, 1f) : 0f;

        anchorX = x <= SNAP ? -1 : x >= availW - SNAP ? 1 : 0;
        anchorY = y <= SNAP ? -1 : y >= availH - SNAP ? 1 : 0;

        // 只动贴边的轴：自由轴保持原值，避免取整误差导致松手瞬间位置微跳
        float nx = anchorX < 0 ? 0f : anchorX > 0 ? availW : x;
        float ny = anchorY < 0 ? 0f : anchorY > 0 ? availH : y;
        setPosition(nx, ny);
    }

    public boolean isDragging(){
        return dragging;
    }
}
