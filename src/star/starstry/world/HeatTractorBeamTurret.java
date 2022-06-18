package star.starstry.world;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.type.Liquid;
import mindustry.type.StatusEffect;
import mindustry.ui.Bar;
import mindustry.world.blocks.defense.turrets.TractorBeamTurret;
import mindustry.world.consumers.ConsumeLiquidBase;
import mindustry.world.consumers.ConsumeType;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class HeatTractorBeamTurret extends TractorBeamTurret {

    public final int timerTarget = timers++;
    public float retargetTime = 5f;
    public float shootCone = 6f;
    public float shootLength = 5f;
    public float laserWidth = 0.6f;
    public float force = 0.3f;
    public float scaledForce = 0f;

    public boolean targetAir, targetGround = true;
    public Color laserColor = Color.white;
    public StatusEffect status = StatusEffects.none;
    public float statusDuration = 300;
    public Sound shootSound = Sounds.tractorbeam;
    public float shootSoundVolume = 0f;

    public float damage = 2000;
    public TextureRegion laser, laserStart, laserEnd, baseRegion;
    public float flashThreshold = 0.46f;

    public HeatTractorBeamTurret(String name) {
        super(name);
        coolantMultiplier = 1f;

        rotateSpeed = 10;
        hasPower = true;

    }

    @Override
    public void load() {
        laser = Core.atlas.find(name + "-laser");
        laserEnd = Core.atlas.find(name + "-laserStart");
        laserStart = Core.atlas.find( name + "-laserStart");
        baseRegion = Core.atlas.find("block-" + size + "size");
    }


    @Override
    public void setBars() {
        super.setBars();
        bars.add("heat", (HeatTractorBeamBuild entity) -> new Bar("bar.heat", Pal.lightOrange, () -> entity.heating));
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{baseRegion, region};
    }

    @Override
    public void setStats() {
        super.setStats();

        stats.add(Stat.targetsAir, targetAir);
        stats.add(Stat.targetsGround, targetGround);

        stats.add(Stat.damage, damage * 60f, StatUnit.perSecond);
    }

    @Override
    public void init() {
        super.init();

        clipSize = Math.max(clipSize, (range + tilesize) * 2);
    }

    public class HeatTractorBeamBuild extends BaseTurretBuild {
        public @Nullable
        Unit target;

        public float lastX, lastY, strength;
        public boolean any;
        public float coolant = 1f;
        public float flash;
        public float heating;
        public float heat = heating / 100;

        @Override
        public void updateTile() {

            if(timer(timerTarget, retargetTime)){
                target = Units.closestEnemy(team, x, y, range, u -> u.checkTarget(targetAir, targetGround));
            }

            if(target != null && acceptCoolant) {
                float maxUsed = consumes.<ConsumeLiquidBase>get(ConsumeType.liquid).amount;

                Liquid liquid = liquids.current();

                float used = Math.min(Math.min(liquids.get(liquid), maxUsed * Time.delta), Math.max(0, (1f / coolantMultiplier) / liquid.heatCapacity));

                liquids.remove(liquid, used);

                if(Mathf.chance(0.06 * used)){
                    coolEffect.at(x + Mathf.range(size * tilesize / 2f), y + Mathf.range(size * tilesize / 2f));
                }

                coolant = 1f + (used * liquid.heatCapacity * coolantMultiplier);
            }

            any = false;

            if(target != null && target.within(this, range + target.hitSize/2f) && target.team() != team && target.checkTarget(targetAir, targetGround) && efficiency() > 0.02f) {
                heating += Time.delta;

                if(Mathf.pow(heat, 2) >= 150) {
                    heat = 100;
                }

                if(!headless) {
                    control.sound.loop(shootSound, this, shootSoundVolume);
                }

                float dest = angleTo(target);
                rotation = Angles.moveToward(rotation, dest, rotateSpeed * edelta());
                lastX = target.x;
                lastY = target.y;
                strength = Mathf.lerpDelta(strength, 1f, 0.1f);

                //shoot when possible
                if(Angles.within(rotation, dest, shootCone)) {
                    if(damage > 0) {
                        target.damageContinuous(damage * efficiency());
                    }

                    if(status != StatusEffects.none) {
                        target.apply(status, statusDuration);
                    }

                    any = true;
                    target.impulseNet(Tmp.v1.set(this).sub(target).limit((force + (1f - target.dst(this) / range) * scaledForce) * edelta() * timeScale));
                }
            }
            else {
                strength = Mathf.lerpDelta(strength, 0, 0.1f);
                heating = 0;
            }
        }

        @Override
        public float efficiency(){
            return super.efficiency() * coolant * heat;
        }

        @Override
        public void draw() {
            Draw.rect(baseRegion, x, y);
            Drawf.shadow(region, x - (size / 2f), y - (size / 2f), rotation - 90);
            Draw.rect(region, x, y, rotation - 90);

            //draw laser if applicable
            if(any) {
                Draw.z(Layer.bullet);
                float ang = angleTo(lastX, lastY);

                Draw.mixcol(laserColor, Mathf.absin(4f, 0.6f));

                Drawf.laser(team, laser, laserStart, laserEnd,
                        x + Angles.trnsx(ang, shootLength), y + Angles.trnsy(ang, shootLength),
                        lastX, lastY, strength * efficiency() * laserWidth);

                Draw.mixcol();
            }

            if(heat > flashThreshold){
                flash += (1f + ((heat - flashThreshold) / (1f - flashThreshold)) * 5.4f) * Time.delta;
                Draw.color(Color.red, Color.yellow, Mathf.absin(flash, 9f, 1f));
                Draw.alpha(0.3f);
            }
            Draw.reset();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(heat);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            heat = read.f();
        }

        @Override
        public double sense(LAccess sensor) {
            if(sensor == LAccess.heat) return heat;
            return super.sense(sensor);
        }
    }
}
