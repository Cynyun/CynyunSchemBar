package code;

import arc.Core;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;

import java.util.List;

/**
 * 标签浮层：列出所有标签和各自的蓝图数量，点一行就开关这项筛选。
 *
 * 多选且不自动关闭——筛标签常常要连着勾两个以上，点一下就关掉反而碍事。
 * “选中”状态只反映在按钮的 checked 上，但真正的数据在 Picker 的 selectedTags 里：
 * 每行的 checked 都由 Picker 的状态回写（{@link #sync()}），所以不会出现
 * “界面看着选中、筛选实际没生效”的错位。
 */
public class BarTagOverview extends BarWindow{

    private final BarSchematicPicker picker;
    private final BarConfig.WindowState state = new BarConfig.WindowState();
    private final Table list = new Table();
    private final Cell<BarScroll> scrollCell;

    private float anchorX, anchorY;

    public BarTagOverview(BarSchematicPicker picker){
        super(BarMetrics.tagOverviewW(), BarMetrics.tagOverviewH());
        this.picker = picker;

        margin(BarMetrics.sp8());
        defaults().pad(BarMetrics.sp2());
        top();

        Table head = new Table();
        head.add(BarTheme.accentText("标 签")).growX().left();
        head.add(new BarCloseButton().onClick(this::close)).size(BarMetrics.icon());
        add(head).growX().height(BarMetrics.settingsHeadH()).row();

        add(new BarDivider()).growX().height(BarMetrics.hairline()).padTop(BarMetrics.sp2()).padBottom(BarMetrics.sp3()).row();

        list.top();
        list.defaults().padBottom(BarMetrics.sp2());
        scrollCell = add(new BarScroll(list)).growX().height(BarMetrics.tagListMinH());
        scrollCell.row();

        Table btns = new Table();
        btns.defaults().growX().height(BarMetrics.dialogBtnH()).pad(BarMetrics.sp2());
        btns.add(new BarButton("清除全部筛选").onClick(() -> {
            picker.clearTags();
            sync();
            picker.refreshFilters();
        }));
        btns.add(new BarButton("关闭").borderless(true).onClick(this::close));
        add(btns).growX().padTop(BarMetrics.sp2()).row();

        rebuild();
    }

    @Override
    protected BarConfig.WindowState state(){
        return state;
    }

    @Override
    protected void defaultSpawn(){
        placeAt(anchorX, anchorY);
    }

    /** 贴着传进来的锚点（标签按钮的右下角）摆好：右边缘对齐，浮层向下展开。 */
    public void placeAt(float x, float y){
        anchorX = x;
        anchorY = y;

        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);

        state.present = true;
        setPosition(Mathf.round(Mathf.clamp(x - width, 0f, maxX)), Mathf.round(Mathf.clamp(y - height, 0f, maxY)));
    }

    public void close(){
        remove();
    }

    /** 重建行（标签集合或计数变了），并顺手把高度收成内容高。 */
    public void rebuild(){
        list.clearChildren();

        List<String> tags = picker.allTags();
        if(tags.isEmpty()){
            list.add(BarTheme.dimText("还没有任何标签")).growX().left().pad(BarMetrics.sp6()).row();
            list.add(BarTheme.dimText("在原版蓝图界面里给蓝图加标签")).growX().left().pad(0f, BarMetrics.sp6(), BarMetrics.sp6(), BarMetrics.sp6()).row();
        }else{
            for(String tag : tags){
                BarTagRow row = new BarTagRow(tag, picker.countOf(tag));
                row.changed(() -> {
                    picker.toggleTag(tag);
                    picker.refreshFilters();
                    sync();
                });
                list.add(row).growX().height(BarMetrics.menuItemH()).row();
            }
        }

        sync();

        // 高度跟着内容走：标签少时不占一大片空白
        float h = Mathf.clamp(tags.size() * (BarMetrics.menuItemH() + BarMetrics.sp2()),
            BarMetrics.tagListMinH(), BarMetrics.tagListMaxH());
        scrollCell.height(h);
        invalidate();
        setSize(BarMetrics.tagOverviewW(), getPrefHeight());
        if(state.present) placeAt(anchorX, anchorY);
    }

    /** 把 Picker 的选中集合回写到每一行（setChecked 不会触发 changed）。 */
    public void sync(){
        List<String> tags = picker.allTags();
        int i = 0;
        for(Element e : list.getChildren()){
            if(!(e instanceof BarTagRow)) continue;
            if(i >= tags.size()) break;
            BarTagRow row = (BarTagRow)e;
            boolean sel = picker.isTagSelected(tags.get(i));
            if(row.isChecked() != sel) row.setChecked(sel);
            i++;
        }
    }
}
