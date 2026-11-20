import code.Start;
import mindustry.mod.Mod;

/**
 * 本 mod 的真正入口。mod.hjson 里的 main 指向这个默认包类，所以不要给它加 package。
 * 这里只做一件事：把启动逻辑交给 {@link Start}，以后换正式实现也只改 Start。
 */
public class Main extends Mod{

    public Main(){
        Start.init();
    }
}
