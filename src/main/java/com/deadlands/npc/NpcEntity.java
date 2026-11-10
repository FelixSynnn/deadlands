package com.deadlands.npc;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLSurvivalItems;
import com.deadlands.registry.DLVehicles;
import com.deadlands.registry.DLWeapons;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A survivor: a named person with a role, a faction, a look, a temperament, a home and a routine.
 *
 * <p><b>Routine.</b> By day they keep within sixteen blocks of home, wandering between tasks. At
 * night everyone but the fighters goes back and stays within three blocks of where they sleep;
 * guards, leaders and bandits keep walking the perimeter.
 *
 * <p><b>Fighting.</b> Fighters (guards, leaders, bandits) attack the infected, anyone who hurts
 * them or their neighbours, fighters of a faction they are at war with, and survivors their
 * faction holds hostile. Everyone else runs from the infected and from whoever hurt them.
 *
 * <p><b>Talking.</b> Using a survivor opens a conversation ({@link NpcDialogue}); those with a
 * trade offer it through Minecraft's merchant screen, priced in 9 mm rounds, with prices that
 * follow the survivor's standing with their faction (and, in a settlement, with how full its stores
 * are). Hurting or killing one costs reputation.
 *
 * <p><b>Work.</b> By day those who are not fighters go about their jobs where they can be seen
 * ({@link NpcWorkGoal}).
 *
 * <p><b>Belongings.</b> Each carries a few things of their own (food, water, perhaps bandages or
 * rounds) and the tool of their trade in hand. They eat and bandage themselves from it when hurt,
 * and drop what is left when they die.
 *
 * <p><b>People.</b> Many have someone: a partner, a sibling or a friend among their neighbours.
 * They talk about them, and they grieve when they die; whoever killed them makes an enemy.
 *
 * <p><b>Settlements.</b> A resident belongs to their town's {@link Settlements} record: their death
 * lowers its population, and those the far-away simulation lost while nobody was near are gone
 * when their chunk loads again. Fighters turn on trespassers ({@link Access}).
 *
 * <p><b>The recurring characters</b> stand by each other: they never target or hurt one another,
 * and when one is fighting, the others within {@value #ALLY_RANGE} blocks join in against the
 * same enemy (even those who are not fighters by trade). Otherwise they behave like anyone of
 * their role. Only one entity per character exists in a world ({@link NpcWorldData}).
 *
 * <p><b>Voices.</b> They murmur to themselves, greet, call out when they pick a fight, and cry out
 * when hurt; each survivor's voice has its own pitch.
 */
public class NpcEntity extends PathfinderMob implements Merchant {
	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FACTION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> DATA_CHARACTER = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);

	public static final int SKINS_PER_ROLE = 3;
	public static final int TEMPERAMENTS = 4;
	private static final int DAY_RADIUS = 16;
	private static final int NIGHT_RADIUS = 3;
	/** How far the recurring characters look out for each other. */
	private static final int ALLY_RANGE = 24;

	private @Nullable BlockPos home;
	private int temperament;
	private @Nullable MerchantOffers offers;
	private @Nullable Player tradingPlayer;
	private boolean wasNight;
	/** Game time after which a wanderer leaves if nobody is near (0 = stays). */
	private long leavesAt;
	/** The settlement they belong to (its town key), 0 for none. */
	private long townKey;
	private final SimpleContainer inventory = new SimpleContainer(6);
	private @Nullable UUID relative;
	private String relativeName = "";
	private int relation;
	private boolean grieving;
	/** Whether this survivor has been checked against the settlement's losses since loading. */
	private boolean lossChecked;
	/** Whether a recurring character has been checked against the world's record since loading. */
	private boolean copyChecked;
	/** Set while a recurring character takes up an ally's fight (lets those who are not fighters do so). */
	private boolean joiningAlly;
	/** The enemy taken over from an ally, cleared once it is dead or gone. */
	private @Nullable LivingEntity allyEnemy;
	/** Relationship ids, by {@link #relation()}. */
	public static final String[] RELATIONS = {"none", "partner", "sibling", "friend"};

	public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, 0);
		builder.define(DATA_FACTION, 0);
		builder.define(DATA_SKIN, 0);
		builder.define(DATA_CHARACTER, "");
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new OpenDoorGoal(this, true));
		// Fighters fight; the rest keep their distance from the infected.
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
			@Override
			public boolean canUse() {
				return (role().fighter() || character() != null) && super.canUse();
			}
		});
		// Those who are not fighters keep away from the infected, unless standing by an ally in a fight.
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, InfectedEntity.class, 10.0F, 1.0, 1.25, entity -> !role().fighter() && getTarget() == null));
		goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		goalSelector.addGoal(5, new NpcWorkGoal(this));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, InfectedEntity.class, 10, true, false, (target, level) -> role().fighter()));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(target, level) -> role().fighter() && target instanceof Player player && (isHostileTo(player) || Access.trespassing(player, townKey))));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, NpcEntity.class, 10, true, false,
			(target, level) -> role().fighter() && target instanceof NpcEntity other && faction().hostileTo(other.faction())));
	}

	/** The recurring characters are allies: vanilla targeting never picks an ally. */
	@Override
	protected boolean considersEntityAsAlly(net.minecraft.world.entity.Entity entity) {
		if (character() != null && entity instanceof NpcEntity other && other.character() != null) {
			return true;
		}
		return super.considersEntityAsAlly(entity);
	}

	// ======================================================================================== identity

	public NpcRole role() {
		return NpcRole.byIndex(entityData.get(DATA_ROLE));
	}

	public Faction faction() {
		return Faction.byIndex(entityData.get(DATA_FACTION));
	}

	/** Which of the role's looks this survivor has. */
	public int skin() {
		return Math.floorMod(entityData.get(DATA_SKIN), SKINS_PER_ROLE);
	}

	public @Nullable NpcCharacter character() {
		String id = entityData.get(DATA_CHARACTER);
		return id.isEmpty() ? null : NpcCharacter.byId(id);
	}

	/** 0 guarded, 1 warm, 2 blunt, 3 weary: which set of lines they speak. */
	public int temperament() {
		return temperament;
	}

	public @Nullable BlockPos home() {
		return home;
	}

	/** Make this survivor who they are. Called once, when they are created. */
	public void assign(NpcRole role, Faction faction, @Nullable NpcCharacter character, @Nullable BlockPos home) {
		entityData.set(DATA_ROLE, role.ordinal());
		entityData.set(DATA_FACTION, faction.ordinal());
		entityData.set(DATA_SKIN, random.nextInt(SKINS_PER_ROLE));
		entityData.set(DATA_CHARACTER, character == null ? "" : character.id());
		this.temperament = random.nextInt(TEMPERAMENTS);
		this.home = home;
		setCustomName(Component.literal(character == null ? NpcNames.pick(random) : character.displayName()));
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(role.health());
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(role.damage());
		setHealth(getMaxHealth());
		this.lossChecked = true;
		stock();
		applyRoutine(true);
	}

	/** A wanderer who moves on when nobody has been near for a while. */
	public void setLeavesAt(long gameTime) {
		this.leavesAt = gameTime;
	}

	public void setFaction(Faction faction) {
		entityData.set(DATA_FACTION, faction.ordinal());
	}

	public long townKey() {
		return townKey;
	}

	public void setTown(long key) {
		this.townKey = key;
	}

	/** What they carry. */
	public SimpleContainer inventory() {
		return inventory;
	}

	/** 0 nobody, 1 partner, 2 sibling, 3 friend (see {@link #RELATIONS}). */
	public int relation() {
		return relation;
	}

	public @Nullable UUID relative() {
		return relative;
	}

	public String relativeName() {
		return relativeName;
	}

	/** Their partner, sibling or friend has died. */
	public boolean grieving() {
		return grieving;
	}

	/** Make two survivors each other's partner, sibling or friend. Siblings share a surname. */
	public static void link(NpcEntity a, NpcEntity b, int kind) {
		if (kind == 2 && a.character() == null && b.character() == null) {
			String[] first = b.getName().getString().split(" ", 2);
			String[] family = a.getName().getString().split(" ", 2);
			if (first.length == 2 && family.length == 2) {
				b.setCustomName(Component.literal(first[0] + " " + family[1]));
			}
		}
		a.relative = b.getUUID();
		a.relativeName = b.getName().getString();
		a.relation = kind;
		b.relative = a.getUUID();
		b.relativeName = a.getName().getString();
		b.relation = kind;
	}

	/** Say something: one of the {@code npc.*} voice sounds, at this survivor's pitch. */
	public void voice(SoundEvent sound) {
		playSound(sound, getSoundVolume(), getVoicePitch());
	}

	/** Their own things and the tool of their trade. */
	private void stock() {
		inventory.clearContent();
		inventory.addItem(new ItemStack(random.nextBoolean() ? DLSurvivalItems.CANNED_BEANS : DLSurvivalItems.JERKY, 1 + random.nextInt(2)));
		inventory.addItem(new ItemStack(DLSurvivalItems.WATER_BOTTLE));
		if (random.nextFloat() < 0.5F) {
			inventory.addItem(new ItemStack(DLSurvivalItems.BANDAGE, 1 + random.nextInt(2)));
		}
		if (random.nextFloat() < 0.4F) {
			inventory.addItem(new ItemStack(DLWeapons.PISTOL_AMMO, 2 + random.nextInt(6)));
		}
		Item tool = switch (role()) {
			case GUARD -> random.nextBoolean() ? DLWeapons.CROWBAR : DLWeapons.BASEBALL_BAT;
			case LEADER -> DLWeapons.MACHETE;
			case BANDIT -> random.nextBoolean() ? DLWeapons.MACHETE : DLWeapons.BASEBALL_BAT;
			case MECHANIC -> DLVehicles.WRENCH;
			case DOCTOR -> DLSurvivalItems.BANDAGE;
			case FARMER -> DLSurvivalItems.WATER_BOTTLE;
			default -> null;
		};
		if (tool != null) {
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
			setDropChance(EquipmentSlot.MAINHAND, 0.5F);
		}
	}

	/** Bandage up or eat something from their own things; 0 if there was nothing. */
	private float treatFromPack() {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(DLSurvivalItems.BANDAGE)) {
				stack.shrink(1);
				return 6.0F;
			}
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
				stack.shrink(1);
				return 3.0F;
			}
		}
		return 0.0F;
	}

	public boolean isHostileTo(Player player) {
		return !player.isCreative() && !player.isSpectator() && Reputation.standing(player, faction()) == Reputation.Standing.HOSTILE;
	}

	// ======================================================================================== routine

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!copyChecked) {
			copyChecked = true;
			// Each character exists once: any copy other than the recorded one is removed.
			NpcCharacter character = character();
			if (character != null && !NpcWorldData.get(level.getServer()).adopt(character, getUUID())) {
				discard();
				return;
			}
		}
		if (character() != null && tickCount % 10 == 0) {
			standByAllies(level);
		}
		if (!lossChecked) {
			lossChecked = true;
			// Lost while nobody was near (the far-away simulation): gone now their chunk is back.
			if (townKey != 0L && Settlements.get(level.getServer()).claimLoss(townKey, role() == NpcRole.LEADER || character() != null)) {
				discard();
				return;
			}
		}
		if (tickCount % 40 == 0) {
			applyRoutine(false);
			if (getHealth() < getMaxHealth() && getTarget() == null && tickCount % 200 == 0) {
				float treated = getHealth() < getMaxHealth() * 0.6F ? treatFromPack() : 0.0F;
				heal(treated > 0.0F ? treated : 1.0F); // they patch themselves up
			}
			if (leavesAt > 0L && level.getGameTime() > leavesAt && level.getNearestPlayer(this, 48.0) == null) {
				discard();
			}
		}
	}

	/** A recurring character takes up the fight of another within range, against the same enemy. */
	private void standByAllies(ServerLevel level) {
		if (allyEnemy != null && (getTarget() != allyEnemy || !allyEnemy.isAlive() || allyEnemy.isRemoved()
			|| distanceToSqr(allyEnemy) > (ALLY_RANGE + 8.0) * (ALLY_RANGE + 8.0))) {
			if (getTarget() == allyEnemy) {
				setTarget(null);
			}
			allyEnemy = null;
		}
		if (getTarget() != null) {
			return;
		}
		for (NpcEntity ally : level.getEntitiesOfClass(NpcEntity.class, getBoundingBox().inflate(ALLY_RANGE),
			other -> other != this && other.isAlive() && other.character() != null && other.getTarget() != null)) {
			LivingEntity enemy = ally.getTarget();
			if (enemy != null && enemy.isAlive() && !isAlliedTo(enemy)
				&& !(enemy instanceof Player player && (player.isCreative() || player.isSpectator()))) {
				joiningAlly = true;
				setTarget(enemy);
				joiningAlly = false;
				allyEnemy = getTarget();
				return;
			}
		}
	}

	private void applyRoutine(boolean force) {
		boolean night = level().isDarkOutside();
		if (!force && night == wasNight) {
			return;
		}
		wasNight = night;
		if (home == null) {
			clearHome();
			return;
		}
		setHomeTo(home, night && !role().fighter() ? NIGHT_RADIUS : DAY_RADIUS);
	}

	// ======================================================================================== talk, trade

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer survivor) {
			if (isHostileTo(player) || getTarget() == player || Access.trespassing(player, townKey)) {
				survivor.displayClientMessage(Component.translatable("npc.deadlands.refuses", getName()), true);
			} else {
				getNavigation().stop();
				NpcDialogue.open(survivor, this);
				voice(DLSounds.NPC_GREET.value());
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Open the trading screen for a survivor, with prices that follow their standing. */
	public void trade(ServerPlayer player) {
		if (!role().trades() || tradingPlayer != null) {
			return;
		}
		float price = Reputation.standing(player, faction()).price()
			* (townKey == 0L ? 1.0F : Settlements.get(player.level().getServer()).priceFactor(townKey));
		for (MerchantOffer offer : getOffers()) {
			offer.resetSpecialPriceDiff();
			int base = offer.getBaseCostA().getCount();
			offer.setSpecialPriceDiff(Math.max(1, Math.round(base * price)) - base);
		}
		setTradingPlayer(player);
		openTradingScreen(player, Component.translatable("npc.deadlands.trading", getName()), 0);
	}

	@Override
	public void setTradingPlayer(@Nullable Player player) {
		this.tradingPlayer = player;
	}

	@Override
	public @Nullable Player getTradingPlayer() {
		return tradingPlayer;
	}

	@Override
	public MerchantOffers getOffers() {
		if (offers == null) {
			offers = NpcTrades.offers(role(), random);
		}
		return offers;
	}

	@Override
	public void overrideOffers(MerchantOffers replacement) {
		this.offers = replacement;
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();
		if (tradingPlayer instanceof ServerPlayer player) {
			Reputation.change(player, faction(), Reputation.TRADE);
		}
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.UI_BUTTON_CLICK.value();
	}

	@Override
	public boolean isClientSide() {
		return level().isClientSide();
	}

	@Override
	public boolean stillValid(Player player) {
		return tradingPlayer == player && isAlive() && player.distanceToSqr(this) < 64.0;
	}

	// ======================================================================================== harm

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (character() != null && source.getEntity() instanceof NpcEntity other && other.character() != null) {
			return false; // allies never hurt one another
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof ServerPlayer player) {
			Reputation.change(player, faction(), isDeadOrDying() ? Reputation.KILLED_RESIDENT : Reputation.HURT_RESIDENT);
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		// The four recurring characters cannot be lost: only /kill and the void can touch them.
		if (character() != null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !dead;
		super.die(source);
		if (!first || !(level() instanceof ServerLevel server)) {
			return;
		}
		if (townKey != 0L) {
			Settlements.get(server.getServer()).residentDied(townKey);
		}
		NpcCharacter character = character();
		if (character != null) {
			NpcWorldData.get(server.getServer()).markLost(character, getUUID());
		}
		for (NpcEntity other : server.getEntitiesOfClass(NpcEntity.class, getBoundingBox().inflate(48.0),
			npc -> npc != this && npc.isAlive() && getUUID().equals(npc.relative))) {
			other.grieving = true;
			if (source.getEntity() instanceof ServerPlayer killer) {
				Reputation.change(killer, other.faction(), Reputation.GRIEF);
				if (other.role().fighter()) {
					other.setTarget(killer);
				}
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.removeItemNoUpdate(slot);
			if (!stack.isEmpty()) {
				spawnAtLocation(level, stack);
			}
		}
	}

	// ======================================================================================== voice

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return level().isDarkOutside() && !role().fighter() ? null : DLSounds.NPC_IDLE.value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 480;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return DLSounds.NPC_HURT.value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return DLSounds.NPC_DEATH.value();
	}

	@Override
	public float getVoicePitch() {
		// Everyone sounds like themselves: a pitch fixed by who they are, with a little variation.
		return (0.82F + Math.floorMod(getUUID().hashCode(), 8) * 0.05F) * (0.95F + random.nextFloat() * 0.1F);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// Nobody but a fighter picks a fight (a recurring character may take up an ally's), and nobody fights an ally.
		LivingEntity next = target == null || isAlliedTo(target) || !(role().fighter() || joiningAlly) ? null : target;
		if (next != null && next != getTarget() && !level().isClientSide()) {
			voice(DLSounds.NPC_ALERT.value());
		}
		super.setTarget(next);
	}

	// ======================================================================================== saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Role", entityData.get(DATA_ROLE));
		output.putInt("Faction", entityData.get(DATA_FACTION));
		output.putInt("Skin", entityData.get(DATA_SKIN));
		output.putString("Character", entityData.get(DATA_CHARACTER));
		output.putInt("Temperament", temperament);
		output.putLong("LeavesAt", leavesAt);
		output.storeNullable("Home", BlockPos.CODEC, home);
		output.putLong("Town", townKey);
		output.storeNullable("Relative", UUIDUtil.CODEC, relative);
		output.putString("RelativeName", relativeName);
		output.putInt("Relation", relation);
		output.putBoolean("Grieving", grieving);
		ContainerHelper.saveAllItems(output.child("Pack"), inventory.getItems());
		if (offers != null) {
			output.store("Offers", MerchantOffers.CODEC, offers);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(DATA_ROLE, input.getIntOr("Role", 0));
		entityData.set(DATA_FACTION, input.getIntOr("Faction", 0));
		entityData.set(DATA_SKIN, input.getIntOr("Skin", 0));
		entityData.set(DATA_CHARACTER, input.getStringOr("Character", ""));
		temperament = input.getIntOr("Temperament", 0);
		leavesAt = input.getLongOr("LeavesAt", 0L);
		home = input.read("Home", BlockPos.CODEC).orElse(null);
		townKey = input.getLongOr("Town", 0L);
		relative = input.read("Relative", UUIDUtil.CODEC).orElse(null);
		relativeName = input.getStringOr("RelativeName", "");
		relation = input.getIntOr("Relation", 0);
		grieving = input.getBooleanOr("Grieving", false);
		input.child("Pack").ifPresent(pack -> ContainerHelper.loadAllItems(pack, inventory.getItems()));
		offers = input.read("Offers", MerchantOffers.CODEC).orElse(null);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(role().health());
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(role().damage());
		applyRoutine(true);
	}
}
