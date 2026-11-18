package test;

import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SchematicsDialog;

/**
 * 蓝图网格里的一张卡片：预览图 + 名字 + 尺寸/方块数 + 标签，右上角一个小 i 看详情。
 *
 * 每个格子都写死宽度（{@link #IW}）：SchematicImage 的 pref 是蓝图贴图尺寸（200+ 宽），Label 的
 * pref 是整行文字的宽度，都不写死的话卡片内部的一列会被撑到 200 多，而卡片本身只有 152 宽，
 * Table 只好按“居中对齐 + 溢出”摆——看起来就是卡片互相压着、名字跑到隔壁卡片去。
 *
 * 不用 Button 而是直接继承 Table：卡片里要放一个可点的 i 按钮，Button 自带的
 * ClickListener 会在点击子按钮时也被触发（事件冒泡），结果“看详情”顺带把蓝图绑上了。
 * 这里自己挂 ClickListener 并在 touchDown 里挡掉落在 i 上的按下。
 *
 * 悬停状态不用 enter/exit 事件：指针移到 i 按钮上时卡片会收到 exit，但用户其实还在卡片上。
 * 每帧用 {@link BarTheme#pointerOver} 算一次，遮挡和裁剪都算得对。
 */
public class BarSchematicCard extends Table{

    public static final float W = 152f, H = 180f;

    /** 卡片内边距。 */
    private static final float PAD = 5f;
    /** 内容区宽度：卡片宽减左右内边距。所有子格都按它定宽。 */
    private static final float IW = W - PAD * 2f;
    /** 预览图高度：180 - 2*5(边距) - 2(名字上方留白) - 16 - 13 - 12 = 127，取 126 留 1px 余量。 */
    private static final float PREVIEW_H = 126f;
    private static final float NAME_H = 16f, META_H = 13f, TAGS_H = 12f;

    private final Schematic schem;
    private final Label name;
    private final BarIconButton info;

    private boolean selected, over;
    private final ClickListener click;

    private Runnable onClick, onHover, onUnhover, onInfo;

    public BarSchematicCard(Schematic schem){
        this.schem = schem;

        setTransform(false);
        touchable = Touchable.enabled;
        margin(PAD);
        defaults().pad(0f);
        top();

        // ---------------------------------------------------------------- 预览 + 右上角 i
        SchematicsDialog.SchematicImage preview = new SchematicsDialog.SchematicImage(schem);

        info = new BarIconButton(Icon.info, 12f);
        info.onClick(() -> {
            if(onInfo != null) onInfo.run();
        });

        Table corner = new Table();
        corner.top().right();
        corner.add(info).size(18f).padTop(2f).padRight(2f);

        stack(preview, corner).width(IW).height(PREVIEW_H).row();

        // ---------------------------------------------------------------- 文字
        name = new Label(schem.name(), new Label.LabelStyle(Styles.defaultLabel));
        name.setAlignment(Align.center);
        name.setEllipsis(true);
        add(name).width(IW).height(NAME_H).padTop(2f).row();

        add(BarTheme.dimText(schem.width + " × " + schem.height + " · " + schem.tiles.size + " 方块").alpha(0.75f))
            .width(IW).height(META_H).row();

        add(BarTheme.dimText(tagsLine(schem)).alpha(0.6f))
            .width(IW).height(TAGS_H).ellipsis(true).row();

        click = new ClickListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(pointer != 0 || button != KeyCode.mouseLeft) return false;
                // 点在 i 上就完全交给 i，卡片不进入按下态（返回 false 后不会再收 touchUp）
                if(hitsInfo(event.targetActor)) return false;
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void clicked(InputEvent event, float x, float y){
                if(onClick != null) onClick.run();
            }
        };
        addListener(click);
    }

    private boolean hitsInfo(Element target){
        for(Element e = target; e != null && e != this; e = e.parent){
            if(e == info) return true;
        }
        return false;
    }

    /** 标签太多就只显示前两个，剩下的折成 “+N”。 */
    private static String tagsLine(Schematic s){
        if(s.labels == null || s.labels.isEmpty()) return "无标签";

        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < s.labels.size; i++){
            if(i >= 2){
                sb.append("  +").append(s.labels.size - 2);
                break;
            }
            if(i > 0) sb.append(" · ");
            sb.append(s.labels.get(i));
        }
        return sb.toString();
    }

    public Schematic schematic(){
        return schem;
    }

    public void setSelected(boolean selected){
        this.selected = selected;
    }

    public void onClick(Runnable r){
        onClick = r;
    }

    public void onHover(Runnable r){
        onHover = r;
    }

    public void onUnhover(Runnable r){
        onUnhover = r;
    }

    public void onInfo(Runnable r){
        onInfo = r;
    }

    @Override
    public void act(float delta){
        super.act(delta);

        boolean now = BarTheme.pointerOver(this);
        if(now != over){
            over = now;
            if(now){
                if(onHover != null) onHover.run();
            }else if(onUnhover != null){
                onUnhover.run();
            }
        }
    }

    @Override
    public void draw(){
        float a = parentAlpha;

        if(over) BarTheme.fill(x, y, width, height, (click.isPressed() ? 0.26f : 0.16f) * a);

        // 文字颜色要跟着选中/悬停变，但必须在画子元素之前定好
        if(selected){
            name.color.set(BarTheme.accent.r, BarTheme.accent.g, BarTheme.accent.b, a);
        }else{
            name.color.set(BarTheme.text.r, BarTheme.text.g, BarTheme.text.b, (over ? 1f : 0.9f) * a);
        }

        super.draw();

        if(selected){
            BarTheme.frameAccent(x, y, width, height, (over ? 1f : 0.85f) * a);
        }else{
            BarTheme.brackets(x, y, width, height, over ? 0.80f * a : 0.28f * a);
        }
    }
}
