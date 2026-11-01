package acidfactory;

import mindustry.mod.Mod;
import mindustry.world.Block;

public class AcidFactoryMod extends Mod {

    public static Block nuclearReactor;

    @Override
    public void loadContent() {
        nuclearReactor = new AcidNuclearReactor("nuclear-power-plant");
    }
}