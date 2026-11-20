package code;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Schematic;

/**
 * 主悬浮窗：栏目行 → 序列列 → 蓝图按钮，编辑态在同一个窗口里切换。
 *
 * 数据全部来自 {@link BarConfig}：categories / activeCategory。界面只做三件事——
 * 读树、改树、save()，不另存一份内存副本，所以关掉再开、重启之后看到的都是同一份。
 *
 * 编辑态（标题行的“编辑”）管所有增删改：
 *   栏目  点栏目按钮 → 改名 / 左移 / 右移 / 删除；栏目行末尾 “+” = 新建栏目
 *   序列  点列头 → 改名 / 左移 / 右移 / 删除；最右一列 “+” = 新建序列
 *   按钮  点按钮 → 改标题 / 重新选择 / 上移 / 下移 / 移除；列底 “+” = 从蓝图库挑一张
 * 普通态：点蓝图按钮 = 放蓝图；悬停 0.3s / 长按 0.5s = 出详情浮层。
 *
 * 性能：只在切换栏目、编辑动作、增删时重建，act() 里没有 hit，也没有每帧 setText。
 */
public class BarPanel extends BarWindow implements BarPickerHost{

    /** 面板尺寸：四列 92px 的序列 + 滚动条刚好放得下，再多就横向滚动。 */
    public static final float WIDTH = 480f, HEIGHT = 340f, MARGIN = 24f, TOP_OFFSET = 24f;

    private static final float COL_W = 92f, COL_GAP = 6f;
    private static final float ENTRY_H = 22f;
    private static final float TAB_W = 88f, TAB_H = 24f;

    /** 面板名字：展开态在标题行，收起态是窄条上唯一的文字。 */
    private static final String TITLE = "C Y N Y U N   ·   S C H E M B A R";
    /** 收起态显示的名字：标题那串字距拉得宽，窄条上换成紧凑写法。 */
    private static final String SHORT_NAME = "Cynyun SchemBar";

    /** 展开态内容与收起态窄条，同一时刻只挂一个到面板上。 */
    private final Table content, collapsed;

    /** 栏目 chips、序列列、序列区容器（空状态时换成一个居中的提示表）。 */
    private final Table tabs = new Table(), columns = new Table(), body = new Table();
    private final BarScroll tabScroll, columnScroll;
    private final BarButton editBtn;

    private boolean editing;

    /** 同一时间只允许一个浮层/弹窗：新的打开前先关旧的。 */
    private BarMenu menu;
    private BarSchematicInfoPopup preview;
    private BarEntryButton hoverEntry;

    /** 选择窗与“这次要绑给谁”。pickEntry 非 null = 重新选择，null = 新增一条。 */
    private BarSchematicPicker picker;
    private BarSequence pickSequence;
    private BarEntry pickEntry;

    /** 收起前的位置与尺寸，展开时原样还原。 */
    private boolean shrunk;
    private float prevX, prevY, prevW, prevH;

    public BarPanel(){
        super(WIDTH, HEIGHT);

        // 面板本身不留边距，边距交给展开态的内容表，否则收起条也会被撑出空白
        margin(0f);
        defaults().pad(0f);

        // ------------------------------------------------------------ 展开态
        content = new Table();
        content.margin(8f);
        content.defaults().pad(2f);
        content.top();

        // 标题行：编辑开关 + 设置 + 收起，后两个是一次性入口
        Table head = new Table();
        head.add(BarTheme.accentText(TITLE)).growX().left();
        editBtn = new BarButton("编辑").borderless(true).stateful(true).onClick(this::toggleEditing);
        head.add(editBtn).size(40f, 18f);
        head.add(new BarButton("⚙").borderless(true).onClick(Start::openSettings)).size(22f, 18f);
        head.add(new BarButton("−").borderless(true).onClick(() -> setCollapsed(true))).size(22f, 18f);
        content.add(head).growX().height(20f).row();

        content.add(new BarDivider()).growX().height(1f).padTop(2f).padBottom(4f).row();

        // 栏目行：一排单选按钮，放不下就横向滚
        tabs.left();
        tabs.defaults().padRight(4f);
        tabScroll = new BarScroll(tabs, true);
        content.add(tabScroll).growX().height(32f).row();

        content.add(new BarDivider()).growX().height(1f).padTop(4f).padBottom(4f).row();

        // 序列区：横向滚动，一列一个序列，列内自己竖直滚动
        columns.left().top();
        columns.defaults().padRight(COL_GAP);
        columnScroll = new BarScroll(columns, true);
        content.add(body).grow().row();

        // ------------------------------------------------------------ 收起态
        collapsed = new Table();
        collapsed.margin(4f, 10f, 4f, 10f);
        collapsed.add(BarTheme.accentText(SHORT_NAME)).left();
        collapsed.add(new BarButton("+").borderless(true).onClick(() -> setCollapsed(false))).size(22f, 18f);

        add(content).grow();

        rebuild();
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

    // ------------------------------------------------------------------ 重建

    /**
     * 整块重建栏目行与序列区。结构小（几十个元素），只在用户动作后调用，
     * 比维护一堆增量更新（谁被删了、谁改名了）可靠得多。
     */
    private void rebuild(){
        hidePreview();
        closeMenu();

        editBtn.text(editing ? "完成" : "编辑");
        editBtn.setChecked(editing);

        rebuildTabs();
        rebuildBody();
    }

    private void rebuildTabs(){
        tabs.clearChildren();

        for(BarCategory cat : BarConfig.categories){
            BarButton btn = new BarButton(cat.name).ellipsis(true);

            if(editing){
                // 编辑态点栏目是“开菜单”，不该把按钮的选中态翻掉（菜单关掉后没人会把它翻回来）
                btn.accent(cat == activeCategory());
                btn.onClick(() -> openCategoryMenu(cat));
            }else{
                btn.stateful(true);
                btn.setChecked(cat == activeCategory());
                btn.onClick(() -> selectCategory(cat));
            }
            tabs.add(btn).size(TAB_W, TAB_H);
        }

        // “+” 只在编辑态出现：普通态多一个看不懂的加号只会误触
        if(editing){
            tabs.add(new BarButton("+").borderless(true).onClick(this::addCategory)).size(32f, TAB_H);
        }
    }

    private void rebuildBody(){
        body.clearChildren();

        BarCategory cat = activeCategory();
        if(cat == null){
            body.add(emptyState("还没有栏目", "+ 新建栏目", this::addCategory)).grow();
            return;
        }

        if(cat.sequences.isEmpty()){
            body.add(emptyState("这个栏目还没有序列", "+ 新建序列", () -> addSequence(cat))).grow();
            return;
        }

        columns.clearChildren();
        for(BarSequence seq : cat.sequences){
            columns.add(buildColumn(seq)).width(COL_W).growY();
        }
        if(editing){
            columns.add(buildAddColumn(cat)).width(COL_W).growY();
        }

        body.add(columnScroll).grow();
    }

    /** 空状态：一句话 + 一个正中的按钮。 */
    private Table emptyState(String hint, String button, Runnable action){
        Table t = new Table();
        t.add(BarTheme.dimText(hint)).padBottom(8f).row();
        t.add(new BarButton(button).accent(true).onClick(action)).size(128f, 26f).row();
        return t;
    }

    /** 一列 = 列头 + 竖直滚动的蓝图按钮 + （编辑态）列底 “+”。 */
    private Table buildColumn(BarSequence seq){
        Table col = new Table();
        col.top();

        Table head = new Table();
        if(editing){
            BarButton hb = new BarButton(seq.name).borderless(true).ellipsis(true);
            hb.onClick(() -> openSequenceMenu(seq));
            head.add(hb).growX().height(20f);
        }else{
            BarTheme.BarLabel title = BarTheme.dimText(seq.name).alpha(0.9f);
            title.setEllipsis(true);
            head.add(title).growX().left().padLeft(3f).height(20f);
        }
        col.add(head).growX().height(20f).padBottom(3f).row();

        Table list = new Table();
        list.top();
        for(BarEntry entry : seq.entries){
            list.add(new BarEntryButton(this, seq, entry)).growX().height(ENTRY_H).padBottom(3f).row();
        }
        if(seq.entries.isEmpty()){
            list.add(BarTheme.dimText(editing ? "点 + 添加" : "空")).growX().left().padLeft(3f).height(18f).row();
        }
        col.add(new BarScroll(list)).grow().minHeight(40f).row();

        if(editing){
            col.add(new BarButton("+").borderless(true).onClick(() -> openPicker(seq, null))).growX().height(20f).row();
        }
        return col;
    }

    /** 编辑态最右一列：“+” 新建序列。 */
    private Table buildAddColumn(BarCategory cat){
        Table col = new Table();
        col.top();
        col.add(new BarButton("+").borderless(true).onClick(() -> addSequence(cat))).growX().height(20f).row();
        col.add(BarTheme.dimText("新序列")).growX().padTop(4f).height(18f).row();
        return col;
    }

    // ------------------------------------------------------------------ 栏目/序列的编辑动作

    private BarCategory activeCategory(){
        if(BarConfig.categories.isEmpty()) return null;
        if(BarConfig.activeCategory != null){
            for(BarCategory c : BarConfig.categories){
                if(c.id.equals(BarConfig.activeCategory)) return c;
            }
        }
        return BarConfig.categories.first();
    }

    private void selectCategory(BarCategory cat){
        BarConfig.activeCategory = cat.id;
        persist();
        rebuild();
    }

    private void toggleEditing(){
        editing = editBtn.isChecked();
        if(editing){
            hidePreview();
            closeMenu();
        }
        rebuild();
    }

    private void openCategoryMenu(BarCategory cat){
        BarMenu m = newMenu(cat.name);
        // 编辑态下换栏目也得能换，否则要退出编辑再切回来
        m.item("切换到此栏目", () -> selectCategory(cat));
        m.item("改名", () -> promptText("重命名栏目", cat.name, "栏目名字", v -> {
            cat.name = fallback(v, cat.name);
            persist();
            rebuild();
        }));
        m.item("左移", () -> moveCategory(cat, -1));
        m.item("右移", () -> moveCategory(cat, 1));
        m.item("删除", () -> confirmDelete("删除栏目", "「" + cat.name + "」里的 " + cat.sequences.size + " 条序列会一起删掉。", () -> {
            BarConfig.categories.remove(cat, true);
            if(cat.id.equals(BarConfig.activeCategory)) BarConfig.activeCategory = null;
            persist();
            rebuild();
        }));
        showMenu();
    }

    private void openSequenceMenu(BarSequence seq){
        BarCategory cat = activeCategory();
        if(cat == null) return;

        BarMenu m = newMenu(seq.name);
        m.item("改名", () -> promptText("重命名序列", seq.name, "序列名字", v -> {
            seq.name = fallback(v, seq.name);
            persist();
            rebuild();
        }));
        m.item("左移", () -> moveSequence(cat, seq, -1));
        m.item("右移", () -> moveSequence(cat, seq, 1));
        m.item("删除", () -> confirmDelete("删除序列", "「" + seq.name + "」里的 " + seq.entries.size + " 个蓝图按钮会一起删掉。", () -> {
            cat.sequences.remove(seq, true);
            persist();
            rebuild();
        }));
        showMenu();
    }

    /** 蓝图按钮的编辑菜单。位置跟着光标，和点栏目/列头时一致。 */
    private void openEntryMenu(BarSequence seq, BarEntry entry){
        BarMenu m = newMenu(entry.displayTitle());
        m.item("改标题", () -> promptText("蓝图标题", entry.title == null ? "" : entry.title, "按钮显示的名字", v -> {
            entry.title = v.isEmpty() ? null : v;
            persist();
            rebuild();
        }));
        m.item("重新选择", () -> openPicker(seq, entry));
        m.item("上移", () -> moveEntry(seq, entry, -1));
        m.item("下移", () -> moveEntry(seq, entry, 1));
        m.item("移除", () -> {
            seq.entries.remove(entry, true);
            persist();
            rebuild();
        });
        showMenu();
    }

    private void addCategory(){
        promptText("新建栏目", "", "例如：S科技", v -> {
            BarCategory cat = new BarCategory(BarConfig.newId("c"), fallback(v, "新栏目"));
            BarConfig.categories.add(cat);
            BarConfig.activeCategory = cat.id;
            persist();
            rebuild();
        });
    }

    private void addSequence(BarCategory cat){
        promptText("新建序列", "", "例如：钻头", v -> {
            cat.sequences.add(new BarSequence(BarConfig.newId("s"), fallback(v, "新序列")));
            persist();
            rebuild();
        });
    }

    private void moveCategory(BarCategory cat, int dir){
        int from = BarConfig.categories.indexOf(cat), to = from + dir;
        if(from < 0 || to < 0 || to >= BarConfig.categories.size) return;

        BarConfig.categories.swap(from, to);
        persist();
        rebuild();
    }

    private void moveSequence(BarCategory cat, BarSequence seq, int dir){
        int from = cat.sequences.indexOf(seq), to = from + dir;
        if(from < 0 || to < 0 || to >= cat.sequences.size) return;

        cat.sequences.swap(from, to);
        persist();
        rebuild();
    }

    private void moveEntry(BarSequence seq, BarEntry entry, int dir){
        int from = seq.entries.indexOf(entry, true), to = from + dir;
        if(from < 0 || to < 0 || to >= seq.entries.size) return;

        seq.entries.swap(from, to);
        persist();
        rebuild();
    }

    // ------------------------------------------------------------------ 弹窗

    /** 新建菜单：先关掉旧的，行由调用方逐条加，最后 showMenu()。 */
    private BarMenu newMenu(String title){
        closeMenu();
        menu = new BarMenu(title);
        return menu;
    }

    private void showMenu(){
        if(menu == null) return;
        menu.showAt(Core.input.mouseX(), Core.input.mouseY());
    }

    private void closeMenu(){
        if(menu != null){
            menu.remove();
            menu = null;
        }
    }

    private void promptText(String title, String text, String message, arc.func.Cons<String> onOk){
        // 旧的输入框留着会让键盘焦点分叉，先关
        if(Core.scene != null) Core.scene.setKeyboardFocus(null);
        new BarPrompt(title, text, message, onOk);
    }

    private void confirmDelete(String title, String message, Runnable onOk){
        new BarConfirm(title, message, "删除", onOk);
    }

    /** 空输入回落到原值，避免出现没名字的栏目/序列。 */
    private static String fallback(String value, String def){
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? def : v;
    }

    // ------------------------------------------------------------------ 蓝图按钮交互

    boolean isEditing(){
        return editing;
    }

    void setHoverEntry(BarEntryButton button){
        hoverEntry = button;
    }

    /** 普通态点按钮：解析到蓝图就放，解析不到就说一声（配置保留，等蓝图回来）。 */
    void entryClicked(BarSequence seq, BarEntry entry, BarEntryButton button){
        if(editing){
            openEntryMenu(seq, entry);
            return;
        }

        Schematic s = button.schematic();
        if(s == null){
            if(Vars.ui != null){
                Vars.ui.showInfoFade(entry.file == null ? "这条蓝图按钮还没绑定" : "蓝图缺失：" + entry.file);
            }
            return;
        }
        useSchematic(s);
    }

    private void useSchematic(Schematic s){
        if(Vars.control == null || Vars.control.input == null) return;

        // 与原版蓝图对话框的条件保持一致：不是游戏里 / 规则禁用了都会提示同一句话
        if(!Vars.state.isGame() || !Vars.state.rules.schematicsAllowed){
            if(Vars.ui != null) Vars.ui.showInfoFade("@schematic.disabled");
            return;
        }
        Vars.control.input.useSchematic(s);
    }

    // ------------------------------------------------------------------ 详情浮层

    void showPreview(BarEntryButton button){
        Schematic s = button.schematic();
        if(s == null || editing) return;

        if(preview != null && preview.schematic() == s){
            placePreview(button);
            return;
        }

        hidePreview();
        preview = new BarSchematicInfoPopup(s);
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;
        Vars.ui.hudGroup.addChild(preview);
        placePreview(button);
        preview.toFront();
    }

    /** 浮层贴按钮右侧；右边放不下翻到左侧；上下夹回屏幕。 */
    private void placePreview(Element anchor){
        if(preview == null || anchor.parent == null) return;

        Vec2 p = anchor.localToStageCoordinates(new Vec2(0f, 0f));
        float w = preview.getWidth(), h = preview.getHeight();

        float gx = p.x + anchor.getWidth() + 6f;
        if(gx + w > Core.graphics.getWidth()) gx = p.x - w - 6f;
        float gy = p.y + anchor.getHeight() - h;

        float maxX = Math.max(0f, Core.graphics.getWidth() - w);
        float maxY = Math.max(0f, Core.graphics.getHeight() - h);
        preview.setPosition(Mathf.round(Mathf.clamp(gx, 0f, maxX)), Mathf.round(Mathf.clamp(gy, 0f, maxY)));
    }

    void hidePreview(){
        if(preview != null){
            preview.remove();
            preview = null;
        }
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(preview != null){
            // 指针在按钮上、在浮层上、或者手指还按着 → 留着；否则收掉
            boolean keep = preview.hovered()
                || (hoverEntry != null && hoverEntry.schematic() == preview.schematic())
                || Core.input.isTouched();
            if(!keep) hidePreview();
        }
    }

    // ------------------------------------------------------------------ 选择窗（BarPickerHost）

    private void openPicker(BarSequence seq, BarEntry entry){
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        closePicker();
        pickSequence = seq;
        pickEntry = entry;

        picker = new BarSchematicPicker(this);
        Vars.ui.hudGroup.addChild(picker);
        picker.applyLayout();
        picker.toFront();
    }

    @Override
    public Schematic currentSchematic(){
        return pickEntry == null ? null : BarLibrary.find(pickEntry.file);
    }

    @Override
    public void picked(Schematic s){
        if(pickSequence == null) return;

        BarEntry target = pickEntry;
        if(target == null){
            target = new BarEntry();
            pickSequence.entries.add(target);
        }
        target.file = BarLibrary.keyOf(s);
        target.schematic = s;

        pickSequence = null;
        pickEntry = null;

        persist();
        closePicker();
        rebuild();
    }

    @Override
    public void closePicker(){
        if(picker != null){
            picker.remove();
            picker = null;
        }
        pickSequence = null;
        pickEntry = null;
    }

    // ------------------------------------------------------------------ 收起 / 展开

    public boolean isCollapsed(){
        return shrunk;
    }

    /**
     * 收起时保持左上角不动（高度变小，y 要补上差值），展开时按收起前的坐标还原；
     * 两种情况都夹在屏幕内。尺寸不写进 config.json：下次启动仍是展开态。
     */
    public void setCollapsed(boolean collapse){
        if(collapse == shrunk) return;

        if(collapse){
            closeMenu();
            closePicker();
            hidePreview();

            prevX = x;
            prevY = y;
            prevW = width;
            prevH = height;

            shrunk = true;
            swapChild(collapsed);

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
        setPosition(Mathf.round(Mathf.clamp(nx, 0f, maxX)), Mathf.round(Mathf.clamp(ny, 0f, maxY)));
    }

    @Override
    public void resetToDefault(){
        // 恢复默认位置时先展开，否则会用收起态的小尺寸算落点
        setCollapsed(false);
        super.resetToDefault();
    }

    @Override
    public boolean remove(){
        closeMenu();
        closePicker();
        hidePreview();
        return super.remove();
    }

    /** 编辑动作之后的统一落盘：动作都是用户点击触发的，不用防抖。 */
    private void persist(){
        BarConfig.save();
    }
}
