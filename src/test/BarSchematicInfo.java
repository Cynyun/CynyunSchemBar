package test;

import arc.graphics.Color;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SchematicsDialog;

/**
 * 蓝图信息正文：预览图 + 名字 + 尺寸 + 消耗 + 电量 + 标签 + 描述。
 *
 * 悬停浮层（{@link BarSchematicInfoPopup}）与详情窗（{@link BarSchematicInfoWindow}）共用这一份实现。
 * 以前两边各写一套，详情窗就漏了电量的 ×60（显示 56.9/s，原版是 3412/s），排版也慢慢跑偏。
 *
 * 宽度必须由构造参数给定：arc 的 Label 最小宽度等于文字宽度（Label.java:231-233），
 * 不按固定列宽切的话整张表会被顶宽、往右溢出，而不是换行。
 */
public class BarSchematicInfo extends Table{

    /** 预览图高度。浮层和详情窗共用，避免观感不一致。 */
    public static final float PREVIEW_H = 150f;
    /** 描述在浮层里截断到这么长；详情窗有滚动区，传 0 表示全文。 */
    public static final int POPUP_DESC_CHARS = 160;

    private final Schematic schem;

    /**
     * @param width        本表可用宽度（外层内边距已经扣掉）
     * @param maxDescChars 描述截断长度，&lt;= 0 表示不截断
     */
    public BarSchematicInfo(Schematic schem, float width, int maxDescChars){
        this.schem = schem;

        setWidth(width);
        top();
        defaults().pad(1f);

        // ---------------------------------------------------------------- 预览
        add(new SchematicsDialog.SchematicImage(schem)).growX().height(PREVIEW_H).padBottom(4f).row();

        add(BarTheme.text(schem.name())).growX().left().ellipsis(true).row();
        add(BarTheme.dimText(schem.width + " × " + schem.height + "    方块 " + schem.tiles.size))
            .growX().left().row();

        // ---------------------------------------------------------------- 消耗
        ItemStack[] reqs = schem.requirements().toArray();
        // 原版 SchematicsDialog.java:808 就是这么算的：内部值按 tick 计，乘 60 换成每秒
        float cons = schem.powerConsumption() * 60f, prod = schem.powerProduction() * 60f;
        boolean hasPower = cons > 0.001f || prod > 0.001f;

        if(reqs.length > 0 || hasPower){
            add(BarTheme.accentText("· 消耗")).growX().left().padTop(4f).row();
        }else{
            add(BarTheme.accentText("· 消耗")).growX().left().padTop(4f).row();
            add(BarTheme.dimText("无")).growX().left().row();
        }

        if(reqs.length > 0){
            Table cost = new Table();
            cost.defaults().pad(0f);
            float cellW = (width - 6f) / 4f;
            int i = 0;
            for(ItemStack st : reqs){
                Table cell = new Table();
                cell.add(new Image(st.item.uiIcon)).size(14f).padRight(3f);
                Label amount = new Label(String.valueOf(st.amount), Styles.defaultLabel);
                amount.setEllipsis(true);
                // 数量列给固定宽度，五位数的数量也压得住，不会把整行顶宽
                cell.add(amount).width(cellW - 19f).left();
                cost.add(cell).width(cellW).padBottom(2f);
                if(++i % 4 == 0) cost.row();
            }
            add(cost).growX().left().row();
        }

        if(hasPower){
            Table power = new Table();
            power.defaults().pad(0f);
            if(prod > 0.001f){
                power.image(Icon.powerSmall).color(Pal.powerLight).size(13f).padRight(3f);
                power.add(powerText("+" + Strings.autoFixed(prod, 2) + "/s", Pal.powerLight)).left();
            }
            if(cons > 0.001f){
                if(prod > 0.001f) power.add().width(14f);
                power.image(Icon.powerSmall).color(Pal.remove).size(13f).padRight(3f);
                power.add(powerText("-" + Strings.autoFixed(cons, 2) + "/s", Pal.remove)).left();
            }
            add(power).growX().left().padTop(1f).row();
        }

        // ---------------------------------------------------------------- 标签
        if(schem.labels != null && !schem.labels.isEmpty()){
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < schem.labels.size; i++){
                if(i > 0) sb.append(" · ");
                sb.append(schem.labels.get(i));
            }
            add(BarTheme.dimText(sb.toString())).growX().left().padTop(4f).ellipsis(true).row();
        }

        // ---------------------------------------------------------------- 描述
        String desc = schem.description();
        if(desc != null && !desc.isEmpty()){
            if(maxDescChars > 0 && desc.length() > maxDescChars) desc = desc.substring(0, maxDescChars) + "…";
            add(BarTheme.dimText(desc)).growX().left().padTop(4f).wrap().row();
        }
    }

    /** 电量文字要跟原版一样带颜色，所以这里不用 BarTheme 的会自换色的 Label。 */
    private static Label powerText(String text, Color color){
        Label label = new Label(text, Styles.defaultLabel);
        label.setColor(color);
        return label;
    }

    public Schematic schematic(){
        return schem;
    }
}
