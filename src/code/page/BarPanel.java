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

    // 以下全是"逻辑像素"，用到时一律过 BarTheme.u()（= Scl.scl）换算成屏幕像素。
    // 游戏的字体已经被 Scl 放大过，布局不跟着放大就会出现"字比格子大、按钮挤出窗口"。

    /** 默认宽度 = 3 列序列；用户拖过右下角之后用 BarConfig.mainWidth。 */
    private static final float COL_W = 72f, COL_GAP = 6f;
    /** 蓝图按钮高度：原来 24 太扁，降到手机上文字几乎贴着框。 */
    private static final float ENTRY_H = 28f;
    /** 栏目按钮高度（原来 24 太扁，手机上文字上下都快贴边了）。 */
    private static final float TAB_H = 30f;
    /** 列头高度。 */
    private static final float HEAD_H = 26f;
    /** 默认宽：3 列 + 列间距 + 滚动条 + 左右内容边距。 */
    private static final float DEFAULT_W = COL_W * 3f + COL_GAP * 2f + BarScroll.BAR_WIDTH + 16f;
    private static final float DEFAULT_H = 300f;
    /** 内容表的内边距；最小值要把它算进去。 */
    private static final float PAD = 8f;

    /** 初始落点：左上角留一点、顶部躲开 HUD。 */
    private static final float MARGIN = 12f, TOP_OFFSET = 48f;

    /** 面板名字：展开态在标题行，收起态是窄条上唯一的文字。 */
    private static final String TITLE = "S C H E M B A R";
    /** 收起态显示的名字：标题那串字距拉得宽，窄条上换成紧凑写法。 */
    private static final String SHORT_NAME = "SchemBar";

    /** 展开态内容与收起态窄条，同一时刻只挂一个到面板上。 */
    private final Table content, collapsed;

    /** 栏目 chips、序列列、序列区容器（空状态时换成一个居中的提示表）。 */
    private final Table tabs = new Table(), columns = new Table(), body = new Table(), tabRow = new Table();
    private final BarScroll tabScroll, columnScroll;
    private final BarButton editBtn;

    private boolean editing;

    /** 同一时间只允许一个浮层/弹窗：新的打开前先关旧的。 */
    private BarMenu menu;
    private BarSchematicInfoPopup preview;
    /** 浮层当前锚在哪个按钮上：指针还在它上面就保留，离开后走宽限期再收。 */
    private BarEntryButton previewOwner;
    /** 指针从按钮挪到浮层、或从一格挪到相邻一格时的宽限时间，避免闪一下。 */
    private float previewGrace;

    /** 选择窗与“这次要绑给谁”。pickEntry 非 null = 重新选择，null = 新增一条。 */
    private BarSchematicPicker picker;
    private BarSequence pickSequence;
    private BarEntry pickEntry;

    /** 收起前的位置与尺寸，展开时原样还原。 */
    private boolean shrunk;
    private float prevX, prevY, prevW, prevH;

    public BarPanel(){
        super(initialWidth(), initialHeight());

        // 面板本身不留边距，边距交给展开态的内容表，否则收起条也会被撑出空白
        margin(0f);
        defaults().pad(0f);

        // ------------------------------------------------------------ 展开态
        content = new Table();
        content.margin(BarTheme.u(PAD));
        content.defaults().pad(BarTheme.u(2f));
        content.top();

        // 标题行：编辑开关 + 设置 + 收起，后两个是一次性入口。
        // 标题给 ellipsis：窗口被拖到最小时文字自己截断，而不是把按钮挤出去。
        Table head = new Table();
        BarTheme.BarLabel title = BarTheme.accentText(TITLE);
        title.setEllipsis(true);
        head.add(title).growX().left().minWidth(0f);
        float btnH = BarTheme.u(HEAD_H) - BarTheme.u(4f);
        editBtn = new BarButton("编辑").borderless(true).stateful(true).onClick(this::toggleEditing);
        head.add(editBtn).size(BarTheme.u(46f), btnH);
        head.add(new BarButton("⚙").borderless(true).onClick(Start::openSettings)).size(BarTheme.u(24f), btnH);
        head.add(new BarButton("−").borderless(true).onClick(() -> setCollapsed(true))).size(BarTheme.u(24f), btnH);
        content.add(head).growX().height(BarTheme.u(HEAD_H)).row();

        content.add(new BarDivider()).growX().height(1f).padTop(BarTheme.u(2f)).padBottom(BarTheme.u(4f)).row();

        // 栏目行：一排单选按钮横向滚 + 右端固定的“新建栏目”（编辑态才出现，不随滚动跑掉）
        tabs.left();
        tabs.defaults().padRight(BarTheme.u(4f));
        tabScroll = new BarScroll(tabs, true);
        content.add(tabRow).growX().height(BarTheme.u(TAB_H + 8f)).row();

        content.add(new BarDivider()).growX().height(1f).padTop(BarTheme.u(4f)).padBottom(BarTheme.u(4f)).row();

        // 序列区：横向滚动，一列一个序列，列内自己竖直滚动
        columns.left().top();
        columns.defaults().padRight(BarTheme.u(COL_GAP));
        columnScroll = new BarScroll(columns, true);
        content.add(body).grow().row();

        // ------------------------------------------------------------ 收起态
        collapsed = new Table();
        collapsed.margin(BarTheme.u(4f), BarTheme.u(10f), BarTheme.u(4f), BarTheme.u(10f));
        collapsed.add(BarTheme.accentText(SHORT_NAME)).left();
        collapsed.add(new BarButton("+").borderless(true).onClick(() -> setCollapsed(false))).size(BarTheme.u(24f), btnH);

        add(content).grow();

        rebuild();

        // 最小值 = 标题行真实宽度（TITLE + 三个按钮），高度取它的一半
        float minW = head.getPrefWidth() + BarTheme.u(PAD * 2f + 4f);
        float minH = Math.max(BarTheme.u(60f), minW * 0.5f);
        enableResize(minW, minH);
        setSize(Math.max(width, minW), Math.max(height, minH));

        applyLayout();
    }

    @Override
    protected BarConfig.WindowState state(){
        return BarConfig.mainWindow;
    }

    @Override
    protected void defaultSpawn(){
        // 屏幕左上角，y 轴向上，顶部留出 HUD 状态栏高度（都要乘 Scl，否则手机上会被 HUD 盖住）
        setPosition(Mathf.round(BarTheme.u(MARGIN)),
            Mathf.round(Math.max(0f, Core.graphics.getHeight() - height - BarTheme.u(TOP_OFFSET))));
    }

    // ------------------------------------------------------------------ 尺寸

    private static float initialWidth(){
        float logical = BarConfig.mainWidth > 0f ? BarConfig.mainWidth : DEFAULT_W;
        float w = BarTheme.u(logical);
        float max = Math.max(BarTheme.u(120f), Core.graphics.getWidth() - BarTheme.u(16f));
        return Math.min(w, max);
    }

    private static float initialHeight(){
        float logical = BarConfig.mainHeight > 0f ? BarConfig.mainHeight : DEFAULT_H;
        float h = BarTheme.u(logical);
        // 手机上留出七成屏幕就够：面板太高会把准星和建造区挡住
        float max = Math.max(BarTheme.u(80f), Core.graphics.getHeight() * 0.72f);
        return Math.min(h, max);
    }

    /** 屏幕变小（窗口化、切分辨率）时把面板收进可见区：位置交给基类，尺寸这里先夹一次。 */
    @Override
    public void applyLayout(){
        float maxW = Math.max(BarTheme.u(80f), Core.graphics.getWidth() - BarTheme.u(8f));
        float maxH = Math.max(BarTheme.u(60f), Core.graphics.getHeight() - BarTheme.u(8f));
        if(width > maxW || height > maxH){
            float oldH = height;
            setSize(Math.min(width, maxW), Math.min(height, maxH));
            y = Math.max(0f, y + oldH - height);
        }
        super.applyLayout();
    }

    @Override
    protected void onResize(float w, float h){
        // 列宽固定，变宽只是多露出几列，不需要重建；位置/尺寸在松手时统一落盘
        invalidate();
    }

    @Override
    protected void onResizeEnd(){
        BarConfig.mainWidth = width / BarTheme.u(1f);
        BarConfig.mainHeight = height / BarTheme.u(1f);
        BarConfig.save();
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
        tabRow.clearChildren();

        for(BarCategory cat : BarConfig.categories){
            // 统一用无边框样式：栏目名是一组“页签”，加线框看着像一排按钮
            BarButton btn = new BarButton(cat.name).ellipsis(true).borderless(true);

            if(editing){
                // 编辑态点栏目是“开菜单”，不该把按钮的选中态翻掉（菜单关掉后没人会把它翻回来）
                btn.accent(cat == activeCategory());
                btn.onClick(() -> openCategoryMenu(cat));
            }else{
                btn.stateful(true);
                btn.setChecked(cat == activeCategory());
                btn.onClick(() -> selectCategory(cat));
            }
            // 宽度跟着文字走：短名字不再被固定宽撑出一大块空白
            tabs.add(btn).height(BarTheme.u(TAB_H)).minWidth(BarTheme.u(40f)).maxWidth(BarTheme.u(150f));
        }

        tabRow.add(tabScroll).growX().height(BarTheme.u(TAB_H + 4f));

        // “+” 只在编辑态出现：普通态多一个看不懂的加号只会误触
        if(editing){
            tabRow.add(new BarButton("+").borderless(true).onClick(this::addCategory))
                .size(BarTheme.u(32f), BarTheme.u(TAB_H)).padLeft(BarTheme.u(4f));
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
            columns.add(buildColumn(seq)).width(BarTheme.u(COL_W)).growY();
        }

        Table row = new Table();
        row.add(columnScroll).grow();
        if(editing){
            // 固定的“新建序列”：放在滚动区外面，横向滚到最右也一直看得见
            row.add(new BarButton("+").borderless(true).onClick(() -> addSequence(cat)))
                .width(BarTheme.u(30f)).height(BarTheme.u(HEAD_H)).top().padLeft(BarTheme.u(4f));
        }
        body.add(row).grow();
    }

    /** 空状态：一句话 + 一个正中的按钮。 */
    private Table emptyState(String hint, String button, Runnable action){
        Table t = new Table();
        BarTheme.BarLabel label = BarTheme.dimText(hint);
        label.setEllipsis(true);
        t.add(label).padBottom(BarTheme.u(8f)).minWidth(0f).row();
        t.add(new BarButton(button).accent(true).onClick(action))
            .size(BarTheme.u(140f), BarTheme.u(30f)).row();
        return t;
    }

    /**
     * 一列 = 列头（固定）+ 竖直滚动的蓝图按钮（编辑态末尾带 “+”）。
     *
     * “+” 刻意放进滚动内容里而不是贴在列底：新增一条蓝图它就往下挪一格，
     * 条目再多也永远跟在最后一张下面，不会被窗口底边裁掉（手机上就是这么丢的）。
     */
    private Table buildColumn(BarSequence seq){
        Table col = new Table();
        col.top();

        if(editing){
            // 序列头也是无边框样式，高度抬高到能舒服地点
            BarButton hb = new BarButton(seq.name).borderless(true).ellipsis(true);
            hb.onClick(() -> openSequenceMenu(seq));
            col.add(hb).growX().height(BarTheme.u(HEAD_H)).padBottom(BarTheme.u(3f)).row();
        }else{
            BarTheme.BarLabel title = BarTheme.dimText(seq.name).alpha(0.9f);
            title.setEllipsis(true);
            col.add(title).growX().left().padLeft(BarTheme.u(3f))
                .height(BarTheme.u(HEAD_H)).padBottom(BarTheme.u(3f)).row();
        }

        Table list = new Table();
        list.top();
        for(BarEntry entry : seq.entries){
            list.add(new BarEntryButton(this, seq, entry)).growX()
                .height(BarTheme.u(ENTRY_H)).padBottom(BarTheme.u(3f)).row();
        }
        if(editing){
            list.add(new BarButton("+").borderless(true).onClick(() -> openPicker(seq, null)))
                .growX().height(BarTheme.u(ENTRY_H))
                .padTop(seq.entries.isEmpty() ? 0f : BarTheme.u(3f)).row();
        }else if(seq.entries.isEmpty()){
            list.add(BarTheme.dimText("空")).growX().left().padLeft(BarTheme.u(3f))
                .height(BarTheme.u(20f)).row();
        }
        col.add(new BarScroll(list)).grow().minHeight(BarTheme.u(40f)).row();
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

    /** 指针正指着某个蓝图按钮：先记下来，浮层的去留由 act() 统一决定。 */
    void hovering(BarEntryButton button){
        previewOwner = button;
        previewGrace = 0f;
    }

    /** 按钮报“指针已经离开我”：只有浮层还锚在它身上时才解除锚定。 */
    void releasePreview(BarEntryButton button){
        if(previewOwner == button) previewOwner = null;
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

        previewOwner = button;
        previewGrace = 0f;

        if(preview != null && preview.schematic() == s){
            placePreview(button);
            return;
        }

        hidePreview();
        previewOwner = button;
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

        float gap = BarTheme.u(6f);
        float gx = p.x + anchor.getWidth() + gap;
        if(gx + w > Core.graphics.getWidth()) gx = p.x - w - gap;
        float gy = p.y + anchor.getHeight() - h;

        float maxX = Math.max(0f, Core.graphics.getWidth() - w);
        float maxY = Math.max(0f, Core.graphics.getHeight() - h);
        preview.setPosition(Mathf.round(Mathf.clamp(gx, 0f, maxX)), Mathf.round(Mathf.clamp(gy, 0f, maxY)));
    }

    void hidePreview(){
        previewOwner = null;
        previewGrace = 0f;
        if(preview != null){
            preview.remove();
            preview = null;
        }
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(preview != null){
            // 指针在按钮上、在浮层上、或者手指还按着 → 留着；都不在就等宽限期过了再收。
            // 宽限期是必需的：指针从按钮挪到浮层要横跨几像素，中间那一帧两边都不算“在里面”。
            boolean keep = preview.hovered()
                || (previewOwner != null && previewOwner.isOver())
                || Core.input.isTouched();
            if(keep){
                previewGrace = 0f;
            }else if((previewGrace += delta) >= 0.12f){
                hidePreview();
            }
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
            // 窄条尺寸由文字决定，不再受最小值约束，也不需要缩放手柄
            setGripVisible(false);

            float w = Math.max(BarTheme.u(80f), Mathf.round(collapsed.getPrefWidth()));
            float h = Math.max(BarTheme.u(20f), Mathf.round(collapsed.getPrefHeight()));
            setSize(w, h);
            placeClamped(prevX, prevY + prevH - h);
        }else{
            shrunk = false;
            swapChild(content);
            setGripVisible(true);

            setSize(prevW, prevH);
            placeClamped(prevX, prevY);
        }
    }

    /** 同一时刻只挂一个子表，换掉之后要让 Table 重新算布局。 */
    private void swapChild(Table target){
        clearChildren();
        add(target).grow();
        // clearChildren 连右下角手柄一起清掉了，换完子表要挂回来
        reattachGrip();
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