package code;

import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.Core;
import arc.util.Align;
import arc.util.Time;
import mindustry.game.Schematic;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SchematicsDialog;

/**
 * 蓝图网格里的一张卡片：预览图 + 名字 + 尺寸/方块数 + 标签。详情浮层由 Picker 只管悬停触发。
 *
 * 每个格子都写死宽度（{@link #IW}）：SchematicImage 的 pref 是蓝图贴图尺寸（200+ 宽），Label 的
 * pref 是整行文字的宽度，都不写死的话卡片内部的一列会被撑到 200 多，而卡片本身只有 176 宽，
 * Table 只好按“居中对齐 + 溢出”摆——看起来就是卡片互相压着、名字跑到隔壁卡片去。
 *
 * 不用 Button 而是直接继承 Table：Button 自带 ClickListener，卡片里再挂别的监听时容易互相干扰，
 * 这里自己挂一个 ClickListener，行为完全可控。
 *
 * 悬停状态走 enter/exit 事件，不在 act() 里算：每张卡每帧调一次 {@code Core.scene.hit} 会把整棵
 * 场景树走一遍，四百多张卡就是每帧上百万次元素访问，帧数直接掉下去。事件是引擎自己每帧只做一次
 * hit 之后派发的，所以指针移到名字/标签这些子元素上时，卡片会先收到 exit、紧接着收到从子元素
 * 冒泡上来的 enter——exit 里判断 toActor 是不是自己的后代，就能保持“还在卡片上”。
 */
public class BarSchematicCard extends Table{

    public static final float W = 176f, H = 218f;

    /** 卡片内边距。 */
    private static final float PAD = 6f;
    /** 内容区宽度：卡片宽减左右内边距。所有子格都按它定宽。 */
    private static final float IW = W - PAD * 2f;
    /** 预览图高度：218 - 2*6(边距) - 2(名字上方留白) - 18 - 14 - 13 = 159。 */
    private static final float PREVIEW_H = 149f;
    private static final float NAME_H = 18f, META_H = 14f, TAGS_H = 13f;

    private final Schematic schem;
    private final Label name;

    private boolean selected, over;

    /** 触摸端没有“悬停”，长按 0.5 秒代替。 */
    private static final float LONG_PRESS = 0.5f;
    /** 长按之后这么久内的点击只当“看详情”，不绑定。 */
    private static final float LONG_GUARD = 0.8f;

    private boolean touchOver, longFired;
    private float touchTime;
    private float longAt = -100f;

    private final ClickListener click;

    private Runnable onClick, onHover, onUnhover, onLongPress;

    public BarSchematicCard(Schematic schem){
        this.schem = schem;

        setTransform(false);
        touchable = Touchable.enabled;
        margin(PAD);
        defaults().pad(0f);
        top();

        // ---------------------------------------------------------------- 预览
        SchematicsDialog.SchematicImage preview = new SchematicsDialog.SchematicImage(schem);
        add(preview).width(IW).height(PREVIEW_H).row();

        // ---------------------------------------------------------------- 文字
        name = new Label(schem.name(), new Label.LabelStyle(Styles.defaultLabel));
        name.setAlignment(Align.center);
        name.setEllipsis(true);
        add(name).width(IW).height(NAME_H).padTop(2f).row();

        add(BarTheme.dimText(schem.width + " × " + schem.height + " · " + schem.tiles.size).alpha(0.75f))
            .width(IW).height(META_H).row();

        add(BarTheme.dimText(tagsLine(schem)).alpha(0.6f))
            .width(IW).height(TAGS_H).ellipsis(true).row();

        click = new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                // 长按松手后还会补一个 click，这一下别当成“选中这张蓝图”
                if(Time.time - longAt < LONG_GUARD) return;
                if(onClick != null) onClick.run();
            }
        };
        addListener(click);

        // 悬停：只认鼠标（pointer == -1），手指按下产生的 enter 不该弹出详情
        addListener(new InputListener(){
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                if(pointer != -1){
                    touchOver = true;
                    return;
                }
                if(over) return;
                over = true;
                if(onHover != null) onHover.run();
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                if(pointer != -1){
                    if(!inside(toActor)) touchOver = false;
                    return;
                }
                if(!over) return;
                // 移到自己的子元素（名字、标签）上不算离开
                if(inside(toActor)) return;
                over = false;
                if(onUnhover != null) onUnhover.run();
            }
        });
    }

    /** target 是自己或自己的后代。 */
    private boolean inside(Element target){
        for(Element e = target; e != null; e = e.parent){
            if(e == this) return true;
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

    /** 触摸端长按。和 onHover 分开：长按要立刻出浮层，不用等悬停延时。 */
    public void onLongPress(Runnable r){
        onLongPress = r;
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(touchOver && Core.input.isTouched()){
            touchTime += delta;
            if(!longFired && touchTime >= LONG_PRESS){
                longFired = true;
                longAt = Time.time;
                if(onLongPress != null) onLongPress.run();
            }
        }else{
            touchTime = 0f;
            longFired = false;
        }
    }

    /** 指针是否停在卡片上（含子元素）。由 enter/exit 维护，每帧读它零成本。 */
    public boolean isOver(){
        return over;
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
