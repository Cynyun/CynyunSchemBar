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

    public static final float WIDTH = 300f;
    private static final float ROW_H = 24f, LIST_MIN = 60f, LIST_MAX = 240f;

    private final BarSchematicPicker picker;
    private final BarConfig.WindowState state = new BarConfig.WindowState();
    private final Table list = new Table();
    private final Cell<BarScroll> scrollCell;

    private float anchorX, anchorY;

    public BarTagOverview(BarSchematicPicker picker){
        super(WIDTH, 200f);
        this.picker = picker;

        margin(8f);
        defaults().pad(2f);
        top();

        Table head = new Table();
        head.add(BarTheme.accentText("标 签")).growX().left();
        head.add(new BarCloseButton().onClick(this::close)).size(18f);
        add(head).growX().height(20f).row();

        add(new BarDivider()).growX().height(1f).padTop(2f).padBottom(3f).row();

        list.top();
        list.defaults().padBottom(2f);
        scrollCell = add(new BarScroll(list)).growX().height(LIST_MIN);
        scrollCell.row();

        Table btns = new Table();
        btns.defaults().growX().height(24f).pad(2f);
        btns.add(new BarButton("清除全部筛选").onClick(() -> {
            picker.clearTags();
            sync();
            picker.refreshFilters();
        }));
        btns.add(new BarButton("关闭").borderless(true).onClick(this::close));
        add(btns).growX().padTop(2f).row();

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
            list.add(BarTheme.dimText("还没有任何标签")).growX().left().pad(6f).row();
            list.add(BarTheme.dimText("在原版蓝图界面里给蓝图加标签")).growX().left().pad(0f, 6f, 6f, 6f).row();
        }else{
            for(String tag : tags){
                BarTagRow row = new BarTagRow(tag, picker.countOf(tag));
                row.changed(() -> {
                    picker.toggleTag(tag);
                    picker.refreshFilters();
                    sync();
                });
                list.add(row).growX().height(ROW_H).row();
            }
        }

        sync();

        // 高度跟着内容走：标签少时不占一大片空白
        float h = Mathf.clamp(tags.size() * (ROW_H + 2f), LIST_MIN, LIST_MAX);
        scrollCell.height(h);
        invalidate();
        setSize(WIDTH, getPrefHeight());
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
