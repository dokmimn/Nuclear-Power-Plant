package acidfactory;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.audio.Sound;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Time;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.type.StatusEffect;
import mindustry.ui.Bar;
import mindustry.graphics.Pal;
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.type.Category;

public class AcidNuclearReactor extends ConsumeGenerator {
    public Liquid wasteLiquid;
    public StatusEffect radiationEffect;

    public TextureRegion coreRegion;
    public TextureRegion heatRegion;
    public TextureRegion coolRegion;
    public TextureRegion powerRegion;
    public TextureRegion fuelRegion;
    public TextureRegion wasteRegion;

    public static final Sound explosionSound = new Sound();

    public static final Effect globalNeonYellowDome = new Effect(300f, e -> {
        float progress = e.fin();
        Color neonYellow = Color.yellow.cpy().add(0f, 0.5f, 0f, 0f);
        Draw.color(neonYellow, Color.orange, progress);
        float currentRadius = 6000f * progress;
        Lines.stroke(24f * (1f - progress));
        Lines.poly(e.x, e.y, 60, currentRadius);
        Draw.alpha(0.3f * (1f - progress));
        Fill.poly(e.x, e.y, 60, currentRadius);
    });

    public static final Effect globalOrangeShockwave = new Effect(240f, e -> {
        float progress = e.fin();
        Draw.color(Color.orange, Color.red, progress);
        float currentRadius = 4500f * progress;
        Lines.stroke(16f * (1f - progress));
        Lines.poly(e.x, e.y, 50, currentRadius);
    });

    public static final Effect globalLightYellowRing = new Effect(180f, e -> {
        float progress = e.fin();
        Color lightYellow = Color.yellow.cpy().add(0.2f, 0.2f, 0f, 0f);
        Draw.color(lightYellow, Color.yellow, progress);
        float currentRadius = 3500f * progress;
        Lines.stroke(12f * (1f - progress));
        Lines.poly(e.x, e.y, 50, currentRadius);
    });

    public static final Effect sparklingNuclearFlash = new Effect(140f, e -> {
        float progress = e.fin();
        Draw.color(Color.yellow, Color.orange, progress);
        float radius = 700f * progress;
        Lines.stroke(14f * (1f - progress));
        Lines.poly(e.x, e.y, 45, radius);
        Draw.alpha(0.4f * (1f - progress));
        Fill.poly(e.x, e.y, 45, radius);
    });

    public AcidNuclearReactor(String name) {
        super(name);

        this.size = 9;
        this.category = Category.power;
        this.itemCapacity = 20;
        this.liquidCapacity = 150f;
        this.hasPower = true;
        this.hasLiquids = true;
        this.hasItems = true;
        this.solid = true;
        this.update = true;

        this.powerProduction = 10000f / 60f;
        this.itemDuration = 600f;

        this.localizedName = "Nuclear Power Plant";
        this.description = "An extremely powerful and volatile 9x9 nuclear facility. Consumes Graphite and Thorium to generate a staggering 10,000 power/sec. REQUIRES continuous Cryofluid cooling and graphite moderation.";

        consumeLiquid(Liquids.cryofluid, 0.4f);
        
        consumeItem(Items.graphite, 4);
        consumeItem(Items.thorium, 10);
    }

    @Override
    public void load() {
        super.load();

        coreRegion  = Core.atlas.find(name + "-core");
        heatRegion  = Core.atlas.find(name + "-heat");
        coolRegion  = Core.atlas.find(name + "-cool");
        powerRegion = Core.atlas.find(name + "-power");
        fuelRegion  = Core.atlas.find(name + "-fuel");
        wasteRegion = Core.atlas.find(name + "-waste");

        if (coreRegion == null || !coreRegion.found()) {
            Log.warn("[AcidNuclearReactor] Core sprite not found: @-core.png", name);
            coreRegion = Core.atlas.find("clear");
        }
        if (heatRegion == null || !heatRegion.found())  heatRegion  = Core.atlas.find("clear");
        if (coolRegion == null || !coolRegion.found())  coolRegion  = Core.atlas.find("clear");
        if (powerRegion == null || !powerRegion.found()) powerRegion = Core.atlas.find("clear");
        if (fuelRegion == null || !fuelRegion.found())  fuelRegion  = Core.atlas.find("clear");
        if (wasteRegion == null || !wasteRegion.found()) wasteRegion = Core.atlas.find("clear");

        explosionSound.load(Vars.tree.get("sounds/nuclear-explosion.ogg"));
    }

    @Override
    public void init() {
        wasteLiquid = Vars.content.liquid("waste-liquid");
        if (wasteLiquid == null) wasteLiquid = Vars.content.liquid("acid-factory-waste-liquid");

        if (wasteLiquid != null) {
            this.outputLiquid = new LiquidStack(wasteLiquid, 25f / 60f);
        }

        radiationEffect = Vars.content.statusEffect("radiation");
        if (radiationEffect == null) radiationEffect = Vars.content.statusEffect("acid-factory-radiation");

        requirements(Category.power, new ItemStack[]{
            new ItemStack(Items.copper, 1200),
            new ItemStack(Items.lead, 800),
            new ItemStack(Items.silicon, 300),
            new ItemStack(Items.plastanium, 400),
            new ItemStack(Items.metaglass, 100),
            new ItemStack(Items.graphite, 300)
        });
        
        this.buildTime = 900f;

        super.init();
        
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("heat", (AcidNuclearReactorBuild entity) -> new Bar("Thermal runaway", Pal.lightOrange, () -> entity.customHeat));

        addBar("fission", (AcidNuclearReactorBuild entity) -> new Bar(
            "Nuclear fission",
            Color.valueOf("00FF00"),
            () -> entity.items.has(Items.thorium) ? 1f : 0f
        ));

        addBar("moderator", (AcidNuclearReactorBuild entity) -> new Bar(
            "Neutron moderation",
            Color.valueOf("CCCCCC"),
            () -> entity.items.has(Items.graphite) ? 1f : 0f
        ));

        addBar("power_display", (AcidNuclearReactorBuild entity) -> new Bar(
            "Nominal power",
            Color.valueOf("FF0000"),
            () -> entity.productionEfficiency
        ));

        addBar("cooling_display", (AcidNuclearReactorBuild entity) -> new Bar(
            "Cooling system",
            new Color(66f / 255f, 135f / 255f, 245f / 255f, 1f),
            () -> (entity.liquids != null && entity.liquids.get(Liquids.cryofluid) > 0.05f) ? 1f : 0f
        ));
    }

    public class AcidNuclearReactorBuild extends ConsumeGeneratorBuild {
        public boolean meltdownTriggered = false;
        public float customHeat = 0f;

        @Override
        public void updateTile() {
            boolean hasThorium = items.has(Items.thorium);
            boolean hasGraphite = items.has(Items.graphite);
            boolean hasCoolant = liquids != null && liquids.get(Liquids.cryofluid) > 0.05f;
            boolean wasteFull = wasteLiquid != null && liquids != null && liquids.get(wasteLiquid) >= (block.liquidCapacity - 5f);

            boolean isOverheating = false;

            if (hasThorium) {
                if (!hasCoolant || !hasGraphite) {
                    isOverheating = true;
                }
            }

            if (isOverheating) {
                customHeat += (1f / 300f) * delta();
            } else if (hasThorium) {
                customHeat = Mathf.clamp(customHeat - (0.005f * delta()));
            } else {
                customHeat = Mathf.clamp(customHeat - (0.001f * delta()));
            }

            if (customHeat >= 1f && !meltdownTriggered) {
                triggerMeltdown();
                return;
            }

            super.updateTile();

            if (wasteFull) {
                productionEfficiency = 0f;
                if (wasteLiquid != null) {
                    liquids.remove(wasteLiquid, (25f / 60f) * delta());
                }
            }

            if (wasteLiquid != null && liquids.get(wasteLiquid) > 0.01f) {
                dumpLiquid(wasteLiquid);
            }
        }

        @Override
        public void draw() {
            super.draw();

            float size = block.size * 8f;

            boolean working    = productionEfficiency > 0f;
            boolean hasThorium = items.has(Items.thorium);
            boolean hasGraphite = items.has(Items.graphite);
            boolean hasCoolant = liquids != null && liquids.get(Liquids.cryofluid) > 0.05f;
            boolean wasteFull  = wasteLiquid != null && liquids != null
                                 && liquids.get(wasteLiquid) >= (block.liquidCapacity - 5f);

            if (hasCoolant && coolRegion != Core.atlas.find("clear")) {
                Draw.color();
                Draw.rect(coolRegion, x, y, size, size);
                Draw.reset();
            }

            if (hasThorium && fuelRegion != Core.atlas.find("clear")) {
                Draw.color();
                Draw.rect(fuelRegion, x, y, size, size);
                Draw.reset();
            }

            if (working && powerRegion != Core.atlas.find("clear")) {
                Draw.color();
                Draw.rect(powerRegion, x, y, size, size);
                Draw.reset();
            }

            if (working && coreRegion != Core.atlas.find("clear")) {
                Draw.blend(Blending.additive);
                Draw.color();
                Draw.rect(coreRegion, x, y, size, size);
                Draw.blend(Blending.normal);
                Draw.reset();
            }

            if (customHeat > 0.5f && heatRegion != Core.atlas.find("clear")) {
                float heatT = Mathf.clamp((customHeat - 0.5f) / 0.5f);
                Draw.color(1f, 1f, 1f, heatT);
                Draw.rect(heatRegion, x, y, size, size);
                Draw.reset();
            }

            if (wasteFull && wasteRegion != Core.atlas.find("clear")) {
                Draw.color();
                Draw.rect(wasteRegion, x, y, size, size);
                Draw.reset();
            }
        }

        public void triggerMeltdown() {
            if (meltdownTriggered) return;
            meltdownTriggered = true;

            explosionSound.at(x, y);

            final float bx = x;
            final float by = y;

            globalNeonYellowDome.at(bx, by);
            globalOrangeShockwave.at(bx, by);
            globalLightYellowRing.at(bx, by);

            Effect.shake(200f, 300f, bx, by);
            Fx.shockwave.at(bx, by);

            for (int j = 0; j < 35; j++) {
                float delay = j * 3.5f;
                Time.run(delay, new Runnable() {
                    @Override
                    public void run() {
                        sparklingNuclearFlash.at(bx + Mathf.range(300f), by + Mathf.range(300f));
                    }
                });
            }

            for (int i = 0; i < 10; i++) {
                final float step = i;
                float delay = step * 25f;
                Time.run(delay, new Runnable() {
                    @Override
                    public void run() {
                        float blastRadius = (step == 0) ? 800f : 800f + (step * 450f);
                        Damage.damage(bx, by, blastRadius, 150000f);
                        if (radiationEffect != null) {
                            Damage.status(team, bx, by, blastRadius, radiationEffect, 2400f, true, true);
                        }
                    }
                });
            }

            if (tile != null && tile.build == this) {
                tile.setAir();
            }

            Time.run(300f, new Runnable() {
                @Override
                public void run() {
                    killPlayerCores();
                }
            });
        }

        public void killPlayerCores() {
            // Get the team that owns this reactor
            Team ownerTeam = this.team; 
            
            if (ownerTeam != null && ownerTeam.active()) {
                CoreBlock.CoreBuild core = ownerTeam.core();
                if (core != null) {
                    core.damage(1_000_000f);
                    
                    // If the owner is the local player, trigger Game Over screen
                    if (team == Vars.player.team()) {
                        Vars.state.gameOver = true;
                    }
                }
            }
        }
    }
}