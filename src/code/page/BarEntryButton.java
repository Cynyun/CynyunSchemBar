package code;

import arc.Core;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.util.Time;
import mindustry.game.Schematic;

/**
 * 序列里的一格蓝图按钮。文字只用用户写的标题（未绑定 → “未绑定”，绑定没起名 → “未命名”）。
 *
 * 交互分两态，由面板的编辑开关决定：
 *   普通态  点击 = 用这张蓝图；悬停 0.3 秒 / 长按 0.5 秒 = 弹详情浮层，移开或松手收起
 *   编辑态  点击 = 弹编辑菜单（改标题 / 重新选择 / 上移 / 下移 / 移除）
 *
 * 详情浮层由面板统一管：这里只负责“什么时候该开”，位置和生命周期都在 BarPanel。
 * 悬停走 enter/exit 而不是每帧 hit：一列十几格、每格每帧走一遍场景树会明显掉帧。
 */
public class BarEntryButton extends BarButton{

    /** 鼠标停多久出详情。比原版卡片快一点，按钮小、扫过去更频繁。 */
    private static final float HOVER_DELAY = 0.30f;
    /** 手指按住多久算长按。 */
    private static final float LONG_PRESS = 0.5f;
    /** 长按之后这么久内的点击都当“看详情”而不是“用蓝图”。 */
    private static final float LONG_GUARD = 0.8f;

    private final BarPanel owner;
    private final BarSequence sequence;
    private final BarEntry entry;
    /** 解析结果，null = 未绑定或库里已经找不到。 */
    private final Schematic schematic;

    private boolean over, touchOver, hoverPending;
    private float hoverTime, pressTime;
    private float longAt = -100f;

    public BarEntryButton(BarPanel owner, BarSequence sequence, BarEntry entry){
        super(entry.displayTitle());
        this.owner = owner;
        this.sequence = sequence;
        this.entry = entry;
        this.schematic = BarLibrary.find(entry.file);
        entry.schematic = schematic;

        ellipsis(true);
        onClick(this::clicked);

        addListener(new InputListener(){
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                if(pointer == -1){
                    if(over) return;
                    over = true;
                    hoverTime = 0f;
                    hoverPending = schematic != null && !owner.isEditing();
                    owner.setHoverEntry(BarEntryButton.this);
                }else{
                    touchOver = true;
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                if(pointer != -1){
                    if(!inside(toActor)) touchOver = false;
                    return;
                }
                if(!over || inside(toActor)) return;
                over = false;
                hoverPending = false;
                owner.setHoverEntry(null);
            }
        });
    }

    /** target 是自己或自己的后代。移到子元素上不算离开。 */
    private boolean inside(Element target){
        for(Element e = target; e != null; e = e.parent){
            if(e == this) return true;
        }
        return false;
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(owner.isEditing()){
            hoverPending = false;
            return;
        }

        // 鼠标悬停：停够时间才弹，扫过去不弹
        if(over && hoverPending){
            hoverTime += delta;
            if(hoverTime >= HOVER_DELAY){
                hoverPending = false;
                owner.showPreview(this);
            }
        }

        // 触摸长按：不依赖按钮内部的 ClickListener（它默认会吃掉事件），
        // 只看“手指还在按着”和 enter 时记下的 touchOver。
        if(touchOver && Core.input.isTouched() && schematic != null){
            pressTime += delta;
            if(pressTime >= LONG_PRESS){
                pressTime = 0f;
                longAt = Time.time;
                owner.showPreview(this);
            }
        }else{
            pressTime = 0f;
        }
    }

    private void clicked(){
        // 长按松手后浏览器/引擎还会补一个 click，这一下只当“刚才在看详情”
        if(Time.time - longAt < LONG_GUARD) return;

        owner.entryClicked(sequence, entry, this);
    }

    public BarEntry entry(){
        return entry;
    }

    public BarSequence sequence(){
        return sequence;
    }

    public Schematic schematic(){
        return schematic;
    }
}
