package com.huaxiaosheng.spapacks.anim.signal;

import com.huaxiaosheng.spapacks.util.MathUtil;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Random;

public final class SignalTracker {
    private static final float WALK_THRESHOLD = 0.018F;
    private static final float IDLE_SPEED_MAX = 0.05F;
    private static final float NORMAL_WALK_SPEED = 0.17F;
    private static final float RUN_SPEED = 0.24F;
    private static final int SPEED_WINDOW = 8;
    private static final int CLING_EXIT_GRACE_TICKS = 4;
    private static final double DESCENT_EPS = 0.05;
    private static final double REMOTE_JUMP_MIN_VELOCITY = 0.37;
    private static final int FALL_CHECK_TICKS = 10;
    private static final int FALLING_SCAN_BLOCKS = 6;
    private static final int FALLING_MIN_HEIGHT = 3;
    private static final double SLIME_FLAT_MAX_RISE = 2.0;
    private static final int BOUNCE_CONTACT_GRACE = 3;
    private static final int BOW_MIN_CHARGE = 3;
    private static final int TRIDENT_MIN_CHARGE = 10;
    private static final int SWING_TICKS = 6;
    private static final double TELEPORT_SQ = 64.0;
    private final float[] speedWindow = new float[8];
    private int speedIndex;
    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private boolean lastOnGround = true;
    private int airborneTicks;
    private int leftClingTicks = 99;
    private int sprintBuffer;
    private int recentlyRunning;
    private boolean sprintStopping;
    private boolean groundSprinting;
    private boolean hadJump;
    private int groundTicks;
    private boolean jumpFootLeft;
    private boolean slowJump;
    private boolean jumpedThisAir;
    private boolean steppedOffThisAir;
    private boolean descending;
    private int descentTicks;
    private boolean fallChecked;
    private boolean fallHigh;
    private double liftoffY;
    private double peakRise;
    private boolean slimeGround;
    private boolean slimeBounce;
    private int sinceBounce = 99;
    private int lastUseAction;
    private boolean lastUseOffhand;
    private int chargeTicks;
    private boolean lastChargedMain;
    private boolean lastChargedOff;
    private String lastMainItem = "";
    private String lastOffItem = "";
    private int lastMainSlot = -1;
    private boolean handsSeen;
    private boolean lastSwinging;
    private int lastSwingTicks;
    private boolean lastUsing;
    private int lastHurtTime;
    private float speed;
    private float forward;
    private float side;
    private float vertical;
    private float recentSpeed;
    private float turn;
    private boolean onGround;
    private boolean moving;
    private boolean backward;
    private boolean running;
    private boolean slowed;
    private boolean leftFootLead;
    private boolean clingGrace;
    private boolean bouncing;
    private boolean bounceContact;
    private boolean steppedOff;
    private float landSpeed;
    private float peakFall;
    private TickInput last = new TickInput();
    private boolean jumpStart;
    private boolean landed;
    private boolean swingStart;
    private boolean bcSwing;
    private int swingIndex;
    private int sinceSword = 1073741823;
    private static final int COMBO_TICKS = 40;
    private boolean useStart;
    private boolean useStop;
    private boolean released;
    private boolean releaseLeft;
    private int releaseKind;
    private boolean swapped;
    private boolean drawn;
    private boolean hurt;
    private int unread;
    private static final int STALE = 5;
    private double time = Double.NaN;
    private double distance;
    private boolean ticked;
    private double tickSeen;
    private static final int AFK_MIN = 60;
    private static final int AFK_MAX = 100;
    private static final int FIDGET_WINDOW = 1200;
    private final Random random;
    private int idleTicks;
    private int afkAt;
    private boolean afk;
    private String afkItem = "";
    private int windowTick;
    private final ArrayDeque<Integer> fidgetAt = new ArrayDeque<>();
    private boolean fidget;
    private boolean fidgetNext;
    private float fidgetPick;
    private final LanternSwing lantern = new LanternSwing();
    private boolean lanternRight;
    private boolean lanternLeft;

    public SignalTracker() {
        this(new Random());
    }

    public SignalTracker(Random random) {
        this.random = random;
        this.afkAt = 60 + random.nextInt(41);
    }

    public void tick(TickInput in, WorldProbe world) {
        if (++this.unread > 5) {
            this.clearEvents();
        }

        this.last = in;
        double dx = this.hasLast ? in.x - this.lastX : 0.0;
        double dz = this.hasLast ? in.z - this.lastZ : 0.0;
        double dy = this.hasLast ? in.y - this.lastY : 0.0;
        if (dx * dx + dy * dy + dz * dz > 64.0) {
            dx = 0.0;
            dy = 0.0;
            dz = 0.0;
        }

        double vy = in.local ? in.velocityY : dy;
        float moveLength = (float)Math.sqrt(dx * dx + dz * dz);
        this.airborneTicks = in.onGround ? 0 : this.airborneTicks + 1;
        this.onGround = in.onGround || this.airborneTicks < 2 && Math.abs(vy) <= 0.12;
        this.leftClingTicks = !in.inWater && !in.climbing ? this.leftClingTicks + 1 : 0;
        this.clingGrace = this.leftClingTicks <= 4;
        this.speedWindow[this.speedIndex] = moveLength;
        this.speedIndex = (this.speedIndex + 1) % 8;
        float peak = 0.0F;

        for (float v : this.speedWindow) {
            peak = Math.max(peak, v);
        }

        this.recentSpeed = peak * 20.0F;
        this.speed = moveLength * 20.0F;
        double yaw = Math.toRadians(in.bodyYaw);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        this.forward = (float)((dx * fx + dz * fz) * 20.0);
        this.side = (float)((dx * Math.cos(yaw) + dz * Math.sin(yaw)) * 20.0);
        this.vertical = (float)(vy * 20.0);
        float yawStep = this.hasLast ? MathUtil.wrapDegrees(in.bodyYaw - this.lastYaw) : 0.0F;
        this.turn = -yawStep * 20.0F;
        this.moving = in.local ? in.movementKeys : moveLength > 0.018F;
        boolean jumpKey = in.local ? in.jumpKey : !this.onGround && this.lastOnGround && vy >= 0.37;
        this.backward = this.moving && dx * fx + dz * fz < -0.3 * moveLength;
        this.leftFootLead = Math.sin(in.limbSwing * 0.6662F) >= 0.0;
        boolean slowBand = peak > 0.05F && peak < 0.17F;
        boolean terrainSlowed = slowBand && (!in.local || this.moving);
        this.slowed = in.using || terrainSlowed;
        this.sprintBuffer = in.sprinting && this.moving ? Math.min(this.sprintBuffer + 1, 8) : Math.max(this.sprintBuffer - 1, 0);
        this.running = peak >= 0.24F && this.sprintBuffer > 2;
        if (this.running) {
            this.recentlyRunning = 6;
        } else if (this.recentlyRunning > 0) {
            this.recentlyRunning--;
        }

        if (this.recentlyRunning > 0 && !this.moving && this.onGround) {
            this.sprintStopping = true;
            this.recentlyRunning = 0;
        } else if (this.moving || !this.onGround) {
            this.sprintStopping = false;
        }

        if (this.onGround) {
            this.groundSprinting = in.sprinting;
        }

        boolean leftGround = !this.onGround && this.lastOnGround;
        if (leftGround && vy > 0.0 && jumpKey) {
            this.jumpedThisAir = true;
            this.slowJump = !this.groundSprinting || this.slowed;
            this.jumpFootLeft = this.hadJump && this.groundTicks <= 4 ? !this.jumpFootLeft : !this.leftFootLead;
            this.hadJump = true;
            this.jumpStart = true;
        } else if (leftGround && vy <= 0.0) {
            this.steppedOffThisAir = true;
        }

        if (this.onGround || in.flying || in.gliding || in.inWater || in.climbing || in.vehicle != 0) {
            this.jumpedThisAir = false;
            this.slowJump = false;
            this.steppedOffThisAir = false;
        }

        if (this.onGround) {
            if (!this.lastOnGround) {
                this.landed = true;
                this.landSpeed = Math.max(this.peakFall, Math.max(0.0F, -this.vertical));
            }

            this.peakFall = 0.0F;
            this.slowJump = false;
            this.jumpedThisAir = false;
            this.groundTicks++;
            this.descending = false;
            this.descentTicks = 0;
        } else {
            this.groundTicks = 0;
            this.peakFall = Math.max(this.peakFall, -this.vertical);
            if (vy < -0.05) {
                this.descending = true;
            } else if (vy > 0.05) {
                this.descending = false;
            }

            this.descentTicks = this.descending ? this.descentTicks + 1 : 0;
        }

        if (leftGround) {
            this.liftoffY = in.y;
            this.peakRise = 0.0;
            boolean slime = !jumpKey && world.slimeBelow();
            this.slimeGround = slime;
            this.slimeBounce = slime && vy > Math.sqrt(0.32);
        }

        if (this.onGround || in.flying || in.inWater || in.climbing) {
            this.slimeGround = false;
            this.slimeBounce = false;
        }

        if (this.onGround) {
            this.fallChecked = false;
            this.fallHigh = false;
        } else {
            this.peakRise = Math.max(this.peakRise, in.y - this.liftoffY);
            if (!this.fallChecked && this.descending && this.descentTicks >= 10) {
                this.fallHigh = world.clearBelow(6) >= 3;
                this.fallChecked = true;
            }
        }

        if (this.slimeGround && !this.slimeBounce && this.peakRise > 2.0) {
            this.slimeBounce = true;
        }

        boolean slimeAir = !this.onGround && this.slimeGround;
        this.bouncing = slimeAir && this.slimeBounce;
        this.bounceContact = this.onGround && in.onSlime && this.sinceBounce <= 3;
        this.sinceBounce = this.bouncing ? 0 : this.sinceBounce + 1;
        if (slimeAir && !this.slimeBounce) {
            this.onGround = true;
        }

        this.steppedOff = !this.onGround && this.steppedOffThisAir && vy <= 0.0;
        this.hands(in);
        this.idle(in);
        this.lantern(in);
        this.ticked = true;
        this.hurt = this.hurt | in.hurtTime > this.lastHurtTime;
        this.lastHurtTime = in.hurtTime;
        this.lastX = in.x;
        this.lastY = in.y;
        this.lastZ = in.z;
        this.lastYaw = in.bodyYaw;
        this.lastOnGround = this.onGround;
        this.hasLast = true;
    }

    private void hands(TickInput in) {
        boolean started = in.swinging && (!this.lastSwinging || in.swingTicks < this.lastSwingTicks);
        this.swingStart |= started;
        boolean bc = in.bcAttacking || in.bcPlaying;
        this.bcSwing = in.swinging && (started ? bc : this.bcSwing || bc);
        this.lastSwinging = in.swinging;
        this.lastSwingTicks = in.swinging ? in.swingTicks : 0;
        this.sinceSword++;
        if (started && in.swingSword && !this.bcSwing) {
            this.swingIndex = this.sinceSword < 40 ? 1 - this.swingIndex : 0;
            this.sinceSword = 0;
        }

        this.useStart = this.useStart | (in.using && !this.lastUsing);
        this.useStop = this.useStop | (!in.using && this.lastUsing);
        if (!in.using && this.lastUsing) {
            boolean bow = this.lastUseAction == 4;
            boolean trident = this.lastUseAction == 5;
            if (bow && this.chargeTicks >= 3 || trident && this.chargeTicks >= 10) {
                this.released = true;
                this.releaseLeft = this.lastUseOffhand != in.mainArmLeft;
                this.releaseKind = this.lastUseAction;
            }
        }

        boolean firedMain = this.lastChargedMain && !in.crossbowChargedMain && Objects.equals(in.mainItem, this.lastMainItem);
        boolean firedOff = this.lastChargedOff && !in.crossbowChargedOff && Objects.equals(in.offItem, this.lastOffItem);
        if (firedMain || firedOff) {
            this.released = true;
            this.releaseLeft = firedOff != in.mainArmLeft;
            this.releaseKind = 6;
        }

        if (in.using) {
            this.chargeTicks = in.useAction == this.lastUseAction && in.useOffhand == this.lastUseOffhand ? this.chargeTicks + 1 : 1;
        } else {
            this.chargeTicks = 0;
        }

        boolean swap = Objects.equals(in.mainItem, this.lastOffItem)
            && Objects.equals(in.offItem, this.lastMainItem)
            && !Objects.equals(this.lastMainItem, this.lastOffItem)
            && !in.using;
        this.swapped |= swap;
        boolean otherItem = !Objects.equals(in.mainItem, this.lastMainItem) || in.mainSlot != this.lastMainSlot;
        this.drawn = this.drawn | (this.handsSeen && otherItem && !in.mainItem.isEmpty() && !swap);
        this.handsSeen = true;
        this.lastMainSlot = in.mainSlot;
        this.lastUseAction = in.using ? in.useAction : 0;
        this.lastUseOffhand = in.useOffhand;
        this.lastUsing = in.using;
        this.lastChargedMain = in.crossbowChargedMain;
        this.lastChargedOff = in.crossbowChargedOff;
        this.lastMainItem = in.mainItem;
        this.lastOffItem = in.offItem;
    }

    private void idle(TickInput in) {
        boolean still = this.onGround
            && !this.moving
            && !in.flying
            && !in.sneaking
            && !in.crawling
            && in.vehicle == 0
            && !in.inWater
            && !in.climbing
            && !in.gliding
            && !in.sleeping
            && !in.swinging
            && !in.using
            && !in.crossbowChargedMain
            && !in.crossbowChargedOff
            && !in.bcAttacking
            && !in.externalBody
            && !in.carrying;
        if (still && (!this.afk || Objects.equals(in.mainItem, this.afkItem))) {
            if (!this.afk) {
                if (++this.idleTicks >= this.afkAt) {
                    this.afk = true;
                    this.afkItem = in.mainItem;
                    this.planFidgets();
                }
            } else {
                if (this.fidgetNext) {
                    this.fidgetNext = false;
                    this.fidget = true;
                    this.fidgetPick = this.random.nextFloat();
                }

                this.windowTick++;
                if (!this.fidgetAt.isEmpty() && this.windowTick >= this.fidgetAt.peekFirst()) {
                    this.fidgetAt.pollFirst();
                    if (this.fidget) {
                        this.fidget = false;
                        this.fidgetNext = true;
                    } else {
                        this.fidget = true;
                        this.fidgetPick = this.random.nextFloat();
                    }
                }

                if (this.windowTick >= 1200) {
                    this.planFidgets();
                }
            }
        } else {
            this.idleTicks = 0;
            this.afk = false;
            this.fidget = false;
            this.fidgetNext = false;
            this.fidgetAt.clear();
            this.afkAt = 60 + this.random.nextInt(41);
        }
    }

    private void planFidgets() {
        this.windowTick = 0;
        this.fidgetAt.clear();
        int count = 2 + this.random.nextInt(2);
        int part = 1200 / count;
        int margin = Math.min(60, part / 4);

        for (int i = 0; i < count; i++) {
            int span = part - 2 * margin;
            this.fidgetAt.add(i * part + margin + (span > 0 ? this.random.nextInt(span) : 0));
        }
    }

    private void lantern(TickInput in) {
        boolean right = in.mainArmLeft ? in.lanternOff : in.lanternMain;
        boolean left = in.mainArmLeft ? in.lanternMain : in.lanternOff;
        if (!right && !left || in.bcAttacking || in.externalBody || right != this.lanternRight || left != this.lanternLeft) {
            this.lantern.reset();
        }

        this.lanternRight = right;
        this.lanternLeft = left;
        if (right || left) {
            this.lantern.tick(in.x, in.y, in.z, in.bodyYaw);
        }
    }

    public void frame(double time, float lookYaw, float lookPitch, Signals out) {
        double dt = Double.isNaN(this.time) ? 0.0 : Math.max(0.0, Math.min(0.1, time - this.time));
        this.time = time;
        this.distance = this.distance + this.speed * dt;
        TickInput in = this.last;
        if (this.ticked) {
            this.ticked = false;
            this.tickSeen = time;
        }

        float alpha = (float)Math.max(0.0, Math.min(1.0, (time - this.tickSeen) * 20.0));
        out.set(Signal.TIME, time);
        out.set(Signal.DT, dt);
        out.set(Signal.SPEED, this.speed);
        out.set(Signal.FORWARD, this.forward);
        out.set(Signal.SIDE, this.side);
        out.set(Signal.VERTICAL, this.vertical);
        out.set(Signal.RECENT_SPEED, this.recentSpeed);
        out.set(Signal.TURN, this.turn);
        out.set(Signal.DISTANCE, this.distance);
        out.set(Signal.LOOK_YAW, lookYaw);
        out.set(Signal.LOOK_PITCH, lookPitch);
        out.set(Signal.ON_GROUND, this.onGround);
        out.set(Signal.MOVING, this.moving);
        out.set(Signal.MOVING_BACKWARD, this.backward);
        out.set(Signal.SPRINTING, in.sprinting);
        out.set(Signal.RUNNING, this.running);
        out.set(Signal.SNEAKING, in.sneaking);
        out.set(Signal.CRAWLING, in.crawling);
        out.set(Signal.SLOWED, this.slowed);
        out.set(Signal.SPRINT_STOPPING, this.sprintStopping);
        out.set(Signal.SWIMMING, in.swimming);
        out.set(Signal.IN_WATER, in.inWater);
        out.set(Signal.SUBMERGED, in.submerged);
        out.set(Signal.CLIMBING, in.climbing);
        out.set(Signal.GLIDING, in.gliding);
        out.set(Signal.FLYING, in.flying);
        out.set(Signal.RIDING, in.vehicle != 0);
        out.set(Signal.VEHICLE, in.vehicle);
        out.set(Signal.BOAT_DRIVER, in.vehicle == 1 && in.boatDriver);
        out.set(Signal.PADDLING, in.boatDriver && (in.leftOar || in.rightOar));
        out.set(Signal.PADDLE_TURN, in.leftOar == in.rightOar ? 0.0F : (in.leftOar ? -1.0F : 1.0F));
        out.set(Signal.PADDLE_PHASE, in.paddlePhase);
        out.set(Signal.SLEEPING, in.sleeping);
        out.set(Signal.SLOW_FALLING, in.slowFalling);
        out.set(Signal.CLING_GRACE, this.clingGrace);
        out.set(Signal.BOUNCING, this.bouncing);
        out.set(Signal.BOUNCE_CONTACT, this.bounceContact);
        out.set(Signal.AIR_TIME, this.onGround ? 0.0 : this.airborneTicks / 20.0);
        out.set(Signal.FALL_DISTANCE, in.fallDistance);
        out.set(Signal.DESCENDING, this.descending);
        out.set(Signal.DESCENT_TIME, this.descentTicks / 20.0);
        out.set(Signal.JUMPED, this.jumpedThisAir);
        out.set(Signal.SLOW_JUMP, this.slowJump);
        out.set(Signal.JUMP_LEFT, this.jumpFootLeft);
        out.set(Signal.STEPPED_OFF, this.steppedOff);
        out.set(Signal.FALL_HIGH, this.fallHigh);
        out.set(Signal.LEFT_FOOT_LEAD, this.leftFootLead);
        out.set(Signal.JUMP_START, this.jumpStart);
        out.set(Signal.LANDED, this.landed);
        out.set(Signal.LAND_SPEED, this.landSpeed);
        out.set(Signal.SWING_START, this.swingStart);
        out.set(Signal.USE_START, this.useStart);
        out.set(Signal.USE_STOP, this.useStop);
        out.set(Signal.RELEASED, this.released);
        out.set(Signal.RELEASE_LEFT, this.releaseLeft);
        out.set(Signal.RELEASE_KIND, this.releaseKind);
        out.set(Signal.HANDS_SWAPPED, this.swapped);
        out.set(Signal.ITEM_DRAWN, this.drawn);
        out.set(Signal.HURT, this.hurt);
        out.set(Signal.SWINGING, in.swinging);
        out.set(Signal.SWING_PROGRESS, in.swinging ? Math.min(1.0F, in.swingTicks / 6.0F) : 0.0F);
        out.set(Signal.SWING_OFFHAND, in.swingOffhand);
        out.set(Signal.SWING_SWORD, in.swingSword);
        out.set(Signal.SWING_INDEX, this.swingIndex);
        out.set(Signal.HOLD_SWORD, in.holdSword);
        out.set(Signal.USING, in.using);
        out.set(Signal.USE_ACTION, in.using ? in.useAction : 0.0F);
        out.set(Signal.USE_TIME, in.using ? in.useTicks / 20.0 : 0.0);
        out.set(Signal.USE_OFFHAND, in.useOffhand);
        out.set(Signal.CROSSBOW_CHARGED, in.crossbowChargedMain || in.crossbowChargedOff);
        out.set(Signal.CROSSBOW_OFFHAND, !in.crossbowChargedMain && in.crossbowChargedOff);
        out.set(Signal.HOLD_MAIN_LEFT, in.mainArmLeft);
        out.set(Signal.LANTERN_RIGHT, this.lanternRight);
        out.set(Signal.LANTERN_LEFT, this.lanternLeft);
        out.set(Signal.LANTERN_PITCH, Math.toDegrees(this.lantern.pitchPrev + (this.lantern.pitchNow - this.lantern.pitchPrev) * alpha));
        out.set(Signal.LANTERN_ROLL, Math.toDegrees(this.lantern.rollPrev + (this.lantern.rollNow - this.lantern.rollPrev) * alpha));
        out.set(Signal.AFK, this.afk);
        out.set(Signal.FIDGET, this.afk && this.fidget);
        out.set(Signal.FIDGET_PICK, this.fidgetPick);
        out.set(Signal.BC_ATTACKING, in.bcAttacking);
        out.set(Signal.BC_ARMS, in.bcArms || in.bcAttacking || this.bcSwing);
        out.set(Signal.EXTERNAL_BODY, in.externalBody);
        out.set(Signal.CARRYING, in.carrying);
        this.clearEvents();
        this.unread = 0;
    }

    private void clearEvents() {
        this.jumpStart = this.landed = this.swingStart = this.useStart = this.useStop = false;
        this.released = this.swapped = this.drawn = this.hurt = false;
    }
}
