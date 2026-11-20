package test;

import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/**
 * 主悬浮窗：标题行 + 按钮行 + 滚轮列表，全部用本项目的线框组件。
 * 里面的“点击次数 / 开关 / 选择”仍是测试数据，但已经写进 config.json，
 * 设置窗要读它们，顺带验证整条持久化链路。
 *
 * 收起态：整块面板换成一个只显示名字的窄条，右侧 “+” 点开恢复原样。
 * 收起前后的位置/尺寸只记在内存里，不进 config.json——收起是临时把画面让开，不是设置项。
 */
public class BarPanel extends BarWindow{

    /** 面板尺寸与边距，先用常量，后续由设置项驱动。 */
    public static final float WIDTH = 384f, HEIGHT = 352f, MARGIN = 24f, TOP_OFFSET = 24f;

    /** 面板名字：展开态在标题行，收起态是窄条上唯一的文字。 */
    private static final String TITLE = "C Y N Y U N   ·   S C H E M B A R";

    /** 收起态显示的名字：标题那串字距拉得宽，窄条上换成紧凑写法，否则收起条和展开时一样宽。 */
    private static final String SHORT_NAME = "Cynyun SchemBar";

    /** 两个输入框的持久化键：放 Core.settings（不是 config.json），仅用于测试组件本身。 */
    private static final String KEY_UNDERLINE = "schembar-input-underline", KEY_SIDE = "schembar-input-side";

    /** 展开态内容与收起态窄条，同一时刻只挂一个到面板上。 */
    private final Table content, collapsed;

    /** 唯一保留状态的按钮（开关），状态由按钮自己存，面板只读。 */
    private final BarButton switchBtn;
    private final Label statusLabel, selectedLabel;

    /** 两种输入框外观的样例，顺便验证 Core.settings 的短暂持久化。 */
    private final BarTextField underlineField, sideField;
    private final Label inputEcho;

    /** 上次写进 Core.settings 的文本，只在真变化时才 put，避免每帧写。 */
    private String shownUnderline = "", shownSide = "";

    private boolean toggled;
    private int shownClicks = Integer.MIN_VALUE, shownSelected = Integer.MIN_VALUE;

    /** 收起前的位置与尺寸，展开时原样还原（屏幕变了还会被夹回可视区）。 */
    private boolean shrunk;
    private float prevX, prevY, prevW, prevH;

    public BarPanel(){
        super(WIDTH, HEIGHT);

        // 面板本身不留边距，边距交给两个子表各自决定，否则收起态也会被撑出 10px 空白
        margin(0f);
        defaults().pad(0f);

        // ------------------------------------------------------------ 展开态
        content = new Table();
        content.margin(10f);
        content.defaults().pad(3f);
        content.top(); // 剩余空间留在底部，避免内容整体垂直居中后与描边不齐

        // 标题行：右侧是“设置”“收起”两个瞬时按钮（不保存状态）
        Table head = new Table();
        head.add(BarTheme.accentText(TITLE)).growX().left();
        // 无边框入口按钮：常态不画任何东西，悬停才浮出一层极淡底色
        head.add(new BarButton("⚙").borderless(true).onClick(TestMod::openSettings)).size(22f, 18f);
        head.add(new BarButton("−").borderless(true).onClick(() -> setCollapsed(true))).size(22f, 18f);
        content.add(head).growX().height(20f).row();

        content.add(BarTheme.dimText("UI 组件测试：线框按钮 / 输入框 / 自绘滚动条")).growX().left().height(16f).row();
        content.add(new BarDivider()).growX().height(1f).padTop(4f).padBottom(6f).row();

        // ① 输入框两种外观：底线 / 侧标。两者都实现 BarControl，按上去不会拖动窗口。
        // 文本存进 Core.settings（不是 config.json）：Mindustry 关掉了 settings 自动写盘，
        // 正常退出时由 Control 统一 forceSave，够“短暂持久化”用。
        underlineField = new BarTextField(Core.settings.getString(KEY_UNDERLINE, ""), "点这里输入（底线）", BarTextField.MAX_LENGTH);
        content.add(inputRow("底线", underlineField)).growX().height(24f).padTop(4f).row();

        sideField = new BarTextField(Core.settings.getString(KEY_SIDE, ""), "点这里输入（侧标）", BarTextField.MAX_LENGTH, BarTextField.Style.SIDE);
        content.add(inputRow("侧标", sideField)).growX().height(24f).row();

        inputEcho = BarTheme.dimText("");
        // minWidth(0) 是保险：Label 的 min 宽默认等于文字宽（Label.java:231-233），
        // 一旦回显里塞进长文本，整张 content 表的最小宽度就会被它顶大，所有行一起变宽。
        inputEcho.setEllipsis(true);
        content.add(inputEcho).growX().left().height(16f).minWidth(0f).row();

        content.add(new BarDivider()).growX().height(1f).padTop(2f).padBottom(6f).row();

        // ② 普通按钮行：四个等宽按钮，最后一个故意禁用，用来确认禁用态样式
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
        content.add(bar).growX().row();

        // ③ 状态文字，按钮是否生效看这里
        statusLabel = BarTheme.dimText("");
        content.add(statusLabel).growX().left().height(16f).row();

        // ④ 滚轮列表：BarScroll 自带 1px 轨道 + 2px 强调色滑块
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
        content.add(new BarScroll(list)).grow().minHeight(88f).padTop(2f).row();

        // ⑤ 列表选择结果
        selectedLabel = BarTheme.dimText("");
        content.add(selectedLabel).growX().left().height(16f).row();

        // ------------------------------------------------------------ 收起态
        // 只留名字 + “+” 展开按钮；尺寸由内容自己决定，见 setCollapsed()
        collapsed = new Table();
        collapsed.margin(4f, 10f, 4f, 10f);
        collapsed.add(BarTheme.accentText(SHORT_NAME)).left();
        collapsed.add(new BarButton("+").borderless(true).onClick(() -> setCollapsed(false))).size(22f, 18f);

        add(content).grow();

        shownUnderline = underlineField.text();
        shownSide = sideField.text();

        refresh();
        updateEcho();
        applyLayout();
    }

    /** 标签 + 输入框的一行，“底线”“侧标”这类说明放左边。 */
    private static Table inputRow(String label, BarTextField field){
        Table row = new Table();
        // 标签列给固定宽度再留 12px 间隙：中文两个字正好占满 32px，
        // 不加间隙时输入框的线会直接顶在字后面，看着像同一段文字。
        row.add(BarTheme.dimText(label)).left().width(32f).padRight(12f);
        row.add(field).growX().growY();
        return row;
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
        syncInputs();
    }

    /** 输入框内容变了才写 Core.settings 并刷新回显（每帧 setText 会让 Label 反复失效）。 */
    private void syncInputs(){
        String u = underlineField.text(), s = sideField.text();
        boolean changed = false;

        if(!u.equals(shownUnderline)){
            shownUnderline = u;
            Core.settings.put(KEY_UNDERLINE, u);
            changed = true;
        }
        if(!s.equals(shownSide)){
            shownSide = s;
            Core.settings.put(KEY_SIDE, s);
            changed = true;
        }

        if(changed) updateEcho();
    }

    /**
     * 回显只报字数，不回显内容本身。
     * 之前把整段文字拼进这一个 Label，Label 的 min 宽 = 文字宽（Label.java:231-233），
     * content 表的最小宽度被顶到近千像素，于是所有行都按那个宽度排、文字画到面板外，
     * 而 TextField 也跟着拿到近千像素的宽度，自然就不需要“向左隐藏”了。
     */
    private void updateEcho(){
        inputEcho.setText("输入回显：底线 " + shownUnderline.length() + " 字 · 侧标 " + shownSide.length() + " 字");
    }

    /** 清空两个输入框；下一帧 act() 会把空串写回 Core.settings。 */
    private void clearInputs(){
        underlineField.text("");
        sideField.text("");
    }

    // ------------------------------------------------------------------ 收起 / 展开

    public boolean isCollapsed(){
        return shrunk;
    }

    /**
     * 收起或展开。
     *
     * 收起时保持左上角不动（高度变小，y 要补上差值），展开时按收起前的坐标还原；
     * 两种情况都夹在屏幕内，免得窗口尺寸变化后收起条跑到屏幕外找不回来。
     * 尺寸不写进 config.json：next 次启动仍是展开态 + 已存的窗口位置。
     */
    public void setCollapsed(boolean collapse){
        if(collapse == shrunk) return;

        if(collapse){
            prevX = x;
            prevY = y;
            prevW = width;
            prevH = height;

            shrunk = true;
            swapChild(collapsed);

            // 窄条的尺寸完全由内容决定，宽度 = 名字 + “+” + 边距
            float w = Math.max(80f, Mathf.round(collapsed.getPrefWidth()));
            float h = Math.max(20f, Mathf.round(collapsed.getPrefHeight()));
            setSize(w, h);
            placeClamped(prevX, prevY + prevH - h);
        }else{
            shrunk = false;
            swapChild(content);

            setSize(prevW, prevH);
            placeClamped(prevX, prevY);
        }
    }

    /** 同一时刻只挂一个子表，换掉之后要让 Table 重新算布局。 */
    private void swapChild(Table target){
        clearChildren();
        add(target).grow();
        invalidate();
    }

    /** 设置位置，并夹在可见区内（用当前尺寸算，所以必须在 setSize 之后调用）。 */
    private void placeClamped(float nx, float ny){
        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);
        // 取整：x/y 带小数会让 1px 描边与底色落在不同像素上
        setPosition(Mathf.round(Mathf.clamp(nx, 0f, maxX)), Mathf.round(Mathf.clamp(ny, 0f, maxY)));
    }

    @Override
    public void resetToDefault(){
        // 恢复默认位置时先展开，否则会用收起态的小尺寸算落点
        setCollapsed(false);
        clearInputs();
        super.resetToDefault();
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
        clearInputs();
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
