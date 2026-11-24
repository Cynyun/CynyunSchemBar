package code;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Schematic;

/**
 * 绑定蓝图槽位：一个槽位 + 一个“编辑”开关，是真正可用的功能组件（不是测试窗）。
 *
 * 行为完全由“编辑开关”决定：
 *   关 + 未绑定 → 按钮禁用，点了没反应
 *   关 + 已绑定 → 用原版逻辑放置这张蓝图
 *   开 + 未绑定 → 弹蓝图选择窗
 *   开 + 已绑定 → 弹蓝图信息窗（取消绑定、改别名）
 *
 * 持久化：绑定关系与别名写进 config.json（与设置同一份文件、同一套 BarConfig）。
 * 蓝图没有稳定 id，所以记的是文件名（不含扩展名），重启时在 {@code Vars.schematics.all()} 里按名找回；
 * 库里还没加载完、或者用户之后才导入这张蓝图，act() 里每秒重试一次。
 *
 * 它自己是面板里的普通表格，位置由父表决定；只有选择窗/信息窗/预览浮层是挂在 hudGroup 上的窗口。
 */
public class BarSlot extends Table implements BarPickerHost{

    /** 槽位按钮的最小宽度（逻辑单位，用时乘 Scl）：够放“未绑定”和常见的蓝图名。 */
    public static final float ACTION_MIN = 132f;

    /** 按钮/行高（逻辑单位）。手机上触屏要更高、更窄，编辑键也要够手指点。 */
    private static final float ROW_H = 24f, EDIT_W = 40f, PAD = 2f, GAP = 8f;
    private static final float ROW_H_MOBILE = 26f, EDIT_W_MOBILE = 44f, PAD_MOBILE = 1f, GAP_MOBILE = 6f;

    /** 悬停多久弹出预览、离开多久收起。 */
    private static final float HOVER_DELAY = 0.5f, HIDE_DELAY = 0.2f;
    /** 库里暂时找不到绑定的蓝图时，隔多久再找一次。 */
    private static final float RESOLVE_DELAY = 1f;
    /** 别名连续输入时，停手多久落盘（每敲一个字写盘太浪费）。 */
    private static final float SAVE_DELAY = 0.4f;

    private final BarButton action, edit;

    private Schematic bound;
    private String alias;
    /** 编辑开关，只存内存。 */
    private boolean editing;

    /** 存档里记下的文件名：库还没加载完时先留着，act() 里重试。 */
    private String pendingFile;

    private BarSchematicPicker picker;
    private BarSchematicInfoWindow info;

    /** 悬停预览的浮层（非编辑态才有）、计时。 */
    private BarSchematicInfoPopup preview;
    private float hoverTime, hideTime;

    private float resolveTime, saveTime;
    /** 别名改过、还没写盘。 */
    private boolean dirty;

    public BarSlot(){
        defaults().pad(BarTheme.d(PAD, PAD_MOBILE));

        action = new BarButton("未绑定").ellipsis(true);
        action.onClick(this::onAction);

        edit = new BarButton("编辑").borderless(true).stateful(true).onClick(this::onEditToggle);

        add(action).height(BarTheme.rowH(ROW_H, ROW_H_MOBILE)).minWidth(BarTheme.u(ACTION_MIN)).growX();
        add(edit).size(BarTheme.d(EDIT_W, EDIT_W_MOBILE), BarTheme.rowH(ROW_H, ROW_H_MOBILE));

        restore();
        refresh();
    }

    // ------------------------------------------------------------------ 持久化

    /** 启动时从 config.json 恢复绑定与别名。 */
    private void restore(){
        alias = emptyToNull(BarConfig.slotAlias);
        pendingFile = emptyToNull(BarConfig.slotFile);
        bound = pendingFile == null ? null : lookup(pendingFile);
        if(bound != null) pendingFile = null;
    }

    /**
     * 按文件名找回蓝图。
     *
     * mod 蓝图与创意工坊蓝图也可能同名，所以优先认 saves/schematics 里的那张。
     * 名字不能叫 find：Group.find(String) 已经存在，同参数静态方法会冲突。
     */
    private static Schematic lookup(String name){
        if(Vars.schematics == null) return null;

        Schematic fallback = null;
        for(Schematic s : Vars.schematics.all()){
            if(s.file == null || !s.file.name().equals(name)) continue;
            if(Vars.schematicDirectory != null && s.file.parent().equals(Vars.schematicDirectory)) return s;
            if(fallback == null) fallback = s;
        }
        return fallback;
    }

    /** 绑定/别名一变就写盘：和设置共用 BarConfig → config.json。 */
    private void persist(){
        // 没有 file 的蓝图（例如原版自带的 loadout）没法按名找回，只能放弃落盘、保留会话内绑定
        BarConfig.slotFile = bound != null && bound.file != null ? bound.file.name() : null;
        BarConfig.slotAlias = emptyToNull(alias);
        BarConfig.save();
    }

    private static String emptyToNull(String s){
        return s == null || s.isEmpty() ? null : s;
    }

    // ------------------------------------------------------------------ 状态

    public Schematic bound(){
        return bound;
    }

    public String alias(){
        return alias;
    }

    /** 别名由信息窗实时写入；null / 空串表示“用蓝图原名”。落盘做了 0.4 秒防抖。 */
    public void alias(String value){
        alias = value;
        dirty = true;
        saveTime = SAVE_DELAY;
        refresh();
    }

    /** 槽位按钮上显示的文字：别名优先，否则蓝图原名，未绑定就是三个字。 */
    public String displayName(){
        if(bound == null) return "未绑定";
        if(alias != null && !alias.isEmpty()) return alias;
        return bound.name();
    }

    public void setBound(Schematic s){
        if(s == null) closeInfo();
        hidePreview();
        bound = s;
        alias = null;
        pendingFile = null;
        dirty = false;
        refresh();
        persist();
    }

    public void unbind(){
        setBound(null);
    }

    /** 选择窗里用来高亮“当前这张”。 */
    @Override
    public Schematic currentSchematic(){
        return bound;
    }

    /** 蓝图可能被原版界面删掉，每次使用前重新确认它在库里。 */
    private boolean isBound(){
        return bound != null && Vars.schematics != null && Vars.schematics.all().contains(bound);
    }

    /** 选择窗里点了一张蓝图。 */
    @Override
    public void picked(Schematic s){
        setBound(s);
        closePicker();
    }

    // ------------------------------------------------------------------ 交互

    private void onAction(){
        // 点下去要么用蓝图、要么开窗口，浮在上面的预览先收掉
        hidePreview();

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

    @Override
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
            // 关窗时把没写完的别名立刻落盘，不用等防抖
            if(dirty){
                dirty = false;
                persist();
            }
        }
    }

    // ------------------------------------------------------------------ 悬停预览

    /**
     * 只有“点按钮会直接用掉蓝图”的状态才需要预览：编辑态点按钮开的是信息窗，再叠一层反而碍事。
     * 指针停在槽位按钮上 0.5 秒弹出，离开 0.2 秒收起；手机没有悬停，用编辑开关看信息。
     */
    @Override
    public void act(float delta){
        super.act(delta);

        if(dirty && (saveTime -= delta) <= 0f){
            dirty = false;
            persist();
        }

        // 启动时蓝图库可能还没加载完；也可能用户之后才导入这张蓝图
        if(bound == null && pendingFile != null && (resolveTime -= delta) <= 0f){
            resolveTime = RESOLVE_DELAY;
            bound = lookup(pendingFile);
            if(bound != null){
                pendingFile = null;
                refresh();
            }
        }

        boolean allowed = bound != null && isBound() && !editing && picker == null && info == null;
        if(!allowed){
            hoverTime = 0f;
            hidePreview();
            return;
        }

        if(action.isOver() || (preview != null && preview.hovered())){
            hideTime = 0f;
            hoverTime += delta;
            if(preview == null && hoverTime >= HOVER_DELAY) showPreview();
        }else{
            hoverTime = 0f;
            hideTime += delta;
            if(preview != null && hideTime >= HIDE_DELAY) hidePreview();
        }
    }

    private void showPreview(){
        if(bound == null || Vars.ui == null || Vars.ui.hudGroup == null) return;

        preview = new BarSchematicInfoPopup(bound);
        Vars.ui.hudGroup.addChild(preview);

        // 槽位现在是面板里的子元素，x/y 是面板内坐标：必须换算到 stage，否则浮层会贴错地方
        Vec2 p = action.localToStageCoordinates(new Vec2(0f, 0f));
        float w = preview.getWidth(), h = preview.getHeight();

        // 优先放按钮右侧，右边放不下翻到左侧；竖直方向与按钮底边对齐后夹进屏幕
        float gap = BarTheme.d(GAP, GAP_MOBILE);
        float px = p.x + action.getWidth() + gap;
        if(px + w > Core.graphics.getWidth()) px = Math.max(0f, p.x - w - gap);
        float py = Mathf.clamp(p.y + action.getHeight() - h, 0f, Math.max(0f, Core.graphics.getHeight() - h));

        preview.setPosition(Mathf.round(px), Mathf.round(py));
        preview.toFront();
    }

    private void hidePreview(){
        if(preview != null){
            preview.hide();
            preview = null;
        }
    }

    // ------------------------------------------------------------------ 布局

    private void refresh(){
        action.text(displayName());
        // 未绑定且不在编辑态时按钮没有意义，直接禁用（禁用后 BarButton 不再发 ChangeEvent）
        action.setDisabled(bound == null && !editing);
        // 槽位宽度由面板的列宽决定，文字变了只需要让界面重新算一次 pref
        invalidateHierarchy();
    }

    @Override
    public boolean remove(){
        hidePreview();
        closePicker();
        closeInfo();
        return super.remove();
    }
}
