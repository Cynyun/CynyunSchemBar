package code;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Cell;
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

    /** 浮层/详情窗可用的描述长度；数值与设备差异见 {@link BarMetrics#infoDescChars()}。 */
    public static int popupDescChars(){
        return BarMetrics.infoDescChars();
    }

    private final Schematic schem;
    /** 本表可用宽度（外层内边距已经扣掉）。手机上所有行都按它定死，避免被文字顶宽。 */
    private final float contentWidth;

    /**
     * @param width        本表可用宽度（外层内边距已经扣掉）
     * @param maxDescChars 描述截断长度，&lt;= 0 表示不截断
     */
    public BarSchematicInfo(Schematic schem, float width, int maxDescChars){
        this.schem = schem;
        this.contentWidth = width;

        setWidth(width);
        top();
        defaults().pad(BarMetrics.infoCellPad());

        // ---------------------------------------------------------------- 预览
        // 图片永远装在内宽 × 固定高里（SchematicImage 自己 Scaling.fit），不会溢出背景
        Cell<?> preview = add(new SchematicsDialog.SchematicImage(schem))
            .height(BarMetrics.infoPreviewH()).padBottom(BarMetrics.infoPreviewPadBottom());
        if(BarTheme.mobile) preview.width(rowWidth()); else preview.growX();
        preview.row();

        labelRow(BarTheme.text(schem.name()), false).row();
        labelRow(BarTheme.dimText(schem.width + " × " + schem.height + "    方块 " + schem.tiles.size), false).row();

        // ---------------------------------------------------------------- 消耗
        ItemStack[] reqs = schem.requirements().toArray();
        // 原版 SchematicsDialog.java:808 就是这么算的：内部值按 tick 计，乘 60 换成每秒
        float cons = schem.powerConsumption() * 60f, prod = schem.powerProduction() * 60f;
        boolean hasPower = cons > 0.001f || prod > 0.001f;

        labelRow(BarTheme.accentText("· 消耗"), false).padTop(BarMetrics.infoRowPadTop()).row();
        if(reqs.length == 0 && !hasPower){
            labelRow(BarTheme.dimText("无"), false).row();
        }

        if(reqs.length > 0){
            Table cost = new Table();
            cost.defaults().pad(0f);
            // 手机浮层只有 180 宽，4 列每格只剩 40，图标+数字一定会叠；改 2 列
            int cols = BarMetrics.infoCostCols();
            float iconSize = BarMetrics.infoCostIcon();
            float gapAfterIcon = BarMetrics.infoCostIconGap();
            float cellW = (width - BarMetrics.infoCostInset()) / cols;
            int i = 0;
            for(ItemStack st : reqs){
                Table cell = new Table();
                cell.add(new Image(st.item.uiIcon)).size(iconSize).padRight(BarMetrics.infoCostIconPad());
                Label amount = new Label(String.valueOf(st.amount), Styles.defaultLabel);
                amount.setEllipsis(true);
                // 数量列给固定宽度，五位数的数量也压得住，不会把整行顶宽
                cell.add(amount).width(cellW - gapAfterIcon).left();
                cost.add(cell).width(cellW).padBottom(BarMetrics.infoCostCellPadBottom());
                if(++i % cols == 0) cost.row();
            }
            Cell<?> costCell = add(cost).left();
            cost.left();
            if(BarTheme.mobile) costCell.width(rowWidth()); else costCell.growX();
            costCell.row();
        }

        if(hasPower){
            Table power = new Table();
            power.defaults().pad(0f);
            if(prod > 0.001f){
                power.image(Icon.powerSmall).color(Pal.powerLight).size(BarMetrics.infoPowerIcon()).padRight(BarMetrics.infoCostIconPad());
                power.add(powerText("+" + Strings.autoFixed(prod, 2) + "/s", Pal.powerLight)).left();
            }
            if(cons > 0.001f){
                if(prod > 0.001f) power.add().width(BarMetrics.infoPowerGap());
                power.image(Icon.powerSmall).color(Pal.remove).size(BarMetrics.infoPowerIcon()).padRight(BarMetrics.infoCostIconPad());
                power.add(powerText("-" + Strings.autoFixed(cons, 2) + "/s", Pal.remove)).left();
            }
            Cell<?> powerCell = add(power).left().padTop(BarMetrics.infoPowerPadTop());
            power.left();
            if(BarTheme.mobile) powerCell.width(rowWidth()); else powerCell.growX();
            powerCell.row();
        }

        // ---------------------------------------------------------------- 标签
        if(schem.labels != null && !schem.labels.isEmpty()){
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < schem.labels.size; i++){
                if(i > 0) sb.append(" · ");
                sb.append(schem.labels.get(i));
            }
            labelRow(BarTheme.dimText(sb.toString()), false).padTop(BarMetrics.infoRowPadTop()).row();
        }

        // ---------------------------------------------------------------- 描述
        String desc = schem.description();
        if(desc != null && !desc.isEmpty()){
            if(maxDescChars > 0 && desc.length() > maxDescChars) desc = desc.substring(0, maxDescChars) + "…";
            labelRow(BarTheme.dimText(desc), true).padTop(BarMetrics.infoRowPadTop()).row();
        }

        // 手机专用那一套：文字缩回桌面字号 + 按固定宽度布局；最后再裁一刀兜底，
        // 万一还有哪行算宽了，也是被切在浮层背景里，而不是画到战场上。
        if(BarTheme.mobile){
            scaleLabels(this, mobileFontScale());
            setClip(true);
        }
    }

    /** 一行文字：手机定死宽度（ellipsis / wrap 才真正生效），桌面沿用原来的 growX。 */
    private Cell<Label> labelRow(Label label, boolean wrap){
        Cell<Label> cell = add(label).left();
        if(wrap) cell.wrap(); else cell.ellipsis(true);
        if(BarTheme.mobile) cell.width(rowWidth()); else cell.growX();
        return cell;
    }

    /** 手机上一行的可用宽度：整表宽减去 cell 左右各 1 的 pad。 */
    private float rowWidth(){
        return contentWidth - BarMetrics.infoRowInset();
    }

    /**
     * 手机专用缩放系数：把文字缩回“桌面字号”那一档，不再跟 Scl 一起放大。
     * 手机上 Scl 通常 1.6~2.2，1 / Scl 约 0.5~0.6；桌面 Scl = 1 时算出来就是 1，等于不生效。
     */
    private static float mobileFontScale(){
        return Mathf.clamp(1f / BarTheme.u(1f), 0.5f, 1f);
    }

    private static void scaleLabels(Element root, float scale){
        if(!(root instanceof Group)) return;
        for(Element child : ((Group)root).getChildren()){
            if(child instanceof Label) ((Label)child).setFontScale(scale);
            scaleLabels(child, scale);
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
