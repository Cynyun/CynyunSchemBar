package test;

import arc.Core;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.game.Schematic;

/**
 * 蓝图槽位窗（测试版）：一个槽位 + 一个“编辑”开关。
 *
 * 行为完全由“编辑开关”决定（开关状态只存会话，不落盘；绑定也暂不落盘）：
 *   关 + 未绑定 → 按钮禁用，点了没反应
 *   关 + 已绑定 → 用原版逻辑放置这张蓝图
 *   开 + 未绑定 → 弹蓝图选择窗
 *   开 + 已绑定 → 弹蓝图信息窗（里面可以取消绑定、改别名）
 *
 * 这样手机上也不需要右键/长按，单击就够——重绑定 = 打开编辑开关后点一下，选新的。
 */
public class BarSlotWindow extends BarWindow{

    /** 槽位按钮的最小宽度：够放“未绑定”和常见的蓝图名。 */
    public static final float ACTION_MIN = 132f;
    /** 槽位窗与主面板之间的间距。 */
    private static final float PANEL_GAP = 8f;

    /** 位置只存会话：每个实例自带一个 WindowState，不经过 BarConfig 的读写。 */
    private final BarConfig.WindowState state = new BarConfig.WindowState();

    private final BarButton action, edit;

    private Schematic bound;
    private String alias;
    /** 编辑开关，只存内存。 */
    private boolean editing;

    private BarSchematicPicker picker;
    private BarSchematicInfoWindow info;

    public BarSlotWindow(){
        super(200f, 36f);
        margin(6f, 8f, 6f, 8f);
        defaults().pad(2f);

        action = new BarButton("未绑定").ellipsis(true);
        action.onClick(this::onAction);

        edit = new BarButton("编辑").borderless(true).stateful(true).onClick(this::onEditToggle);

        add(action).height(24f).minWidth(ACTION_MIN).growX();
        add(edit).size(40f, 24f);

        refresh();
        applyLayout();
    }

    // ------------------------------------------------------------------ 状态

    @Override
    protected BarConfig.WindowState state(){
        return state;
    }

    public Schematic bound(){
        return bound;
    }

    public String alias(){
        return alias;
    }

    /** 别名由信息窗实时写入；null / 空串表示“用蓝图原名”。 */
    public void alias(String value){
        alias = value;
        refresh();
    }

    /** 槽位按钮上显示的文字：别名优先，否则蓝图原名，未绑定就是三个字。 */
    public String displayName(){
        if(bound == null) return "未绑定";
        if(alias != null && !alias.isEmpty()) return alias;
        return bound.name();
    }

    public void setBound(Schematic s){
        // 解绑时信息窗失去意义，先关掉再改状态
        if(s == null) closeInfo();
        bound = s;
        alias = null;
        refresh();
    }

    public void unbind(){
        setBound(null);
    }

    /** 蓝图可能被原版界面删掉，每次使用前重新确认它在库里。 */
    private boolean isBound(){
        return bound != null && Vars.schematics != null && Vars.schematics.all().contains(bound);
    }

    /** 选择窗里点了一张蓝图。 */
    public void picked(Schematic s){
        setBound(s);
        closePicker();
    }

    // ------------------------------------------------------------------ 交互

    private void onAction(){
        if(bound != null && !isBound()){
            // 库里已经没有这张蓝图了，静默退回“未绑定”
            setBound(null);
            return;
        }

        if(editing){
            if(bound == null) openPicker();
            else openInfo();
        }else if(bound != null){
            useBound();
        }
    }

    private void onEditToggle(){
        editing = edit.isChecked();
        refresh();
    }

    private void useBound(){
        if(Vars.control == null || Vars.control.input == null) return;

        if(!Vars.state.isGame()){
            if(Vars.ui != null) Vars.ui.showInfo("@schematic.disabled");
            return;
        }
        if(!Vars.state.rules.schematicsAllowed){
            if(Vars.ui != null) Vars.ui.showInfo("@schematic.disabled");
            return;
        }

        // 与原版蓝图对话框点击分支走同一个入口（见 SchematicsDialog 的点击处理）
        Vars.control.input.useSchematic(bound);
    }

    // ------------------------------------------------------------------ 子窗口

    private void openPicker(){
        if(Vars.ui == null || Vars.ui.hudGroup == null || bound != null) return;

        closePicker();
        picker = new BarSchematicPicker(this);
        Vars.ui.hudGroup.addChild(picker);
        picker.applyLayout();
        picker.toFront();
    }

    public void closePicker(){
        if(picker != null){
            picker.remove();
            picker = null;
        }
    }

    private void openInfo(){
        if(Vars.ui == null || Vars.ui.hudGroup == null || bound == null) return;

        closeInfo();
        info = new BarSchematicInfoWindow(this);
        Vars.ui.hudGroup.addChild(info);
        info.applyLayout();
        info.toFront();
    }

    public void closeInfo(){
        if(info != null){
            info.remove();
            info = null;
        }
    }

    // ------------------------------------------------------------------ 布局

    /** 默认贴在主面板正下方，跟着面板左上角走。 */
    @Override
    protected void defaultSpawn(){
        float panelY = Core.graphics.getHeight() - BarPanel.HEIGHT - BarPanel.TOP_OFFSET;
        setPosition(BarPanel.MARGIN, Mathf.round(Math.max(0f, panelY - height - PANEL_GAP)));
    }

    @Override
    public void resetToDefault(){
        closePicker();
        closeInfo();
        super.resetToDefault();
    }

    /** 窗口尺寸变化：自己先重排，再把两个子窗口带一遍（它们的位置依赖槽位窗）。 */
    public void onResize(){
        applyLayout();
        if(picker != null && picker.parent != null) picker.applyLayout();
        if(info != null && info.parent != null) info.applyLayout();
    }

    private void refresh(){
        action.text(displayName());
        // 未绑定且不在编辑态时按钮没有意义，直接禁用（禁用后 BarButton 不再发 ChangeEvent）
        action.setDisabled(bound == null && !editing);

        // 文字变长会改窗口宽度：以左上角为锚点重新摆，避免窗口往右长出去
        float keepTop = y + height;
        pack();

        // 蓝图名可能很长：宽度封顶，多出来的字由 ellipsis 截掉，否则会顶出屏幕
        float maxW = Math.max(160f, Core.graphics.getWidth() - 2f * BarPanel.MARGIN);
        if(width > maxW){
            setWidth(maxW);
            invalidate();
        }

        place(keepTop);
    }

    private void place(float keepTop){
        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);
        setPosition(Mathf.round(Mathf.clamp(x, 0f, maxX)), Mathf.round(Mathf.clamp(keepTop - height, 0f, maxY)));
    }
}
