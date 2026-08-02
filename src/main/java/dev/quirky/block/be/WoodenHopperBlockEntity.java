package dev.quirky.block.be;

import dev.quirky.ModBlockEntityTypes;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 木漏斗方块实体（对齐 Quark 经典行为）：
 * <ul>
 *   <li>传输速度是铁漏斗的 1/4：成功搬运一次后冷却 32 tick（原版 {@code MOVE_ITEM_SPEED} = 8）。</li>
 *   <li>红石锁定无效：搬运逻辑不检查 {@code HopperBlock.ENABLED}，被红石信号激活时不暂停传输。</li>
 * </ul>
 *
 * <p>26.2 的 {@link HopperBlockEntity} 把全部搬运逻辑放在 private static 方法里
 * （{@code pushItemsTick}/{@code tryMoveItems}/{@code ejectItems}/{@code setCooldown}），
 * 且构造函数硬编码原版 {@code BlockEntityTypes.HOPPER}（该类型只认 {@code Blocks.HOPPER} 一种方块，
 * NBT 往返也会按 {@code minecraft:hopper} 反序列化成原版实体），子类无法覆写冷却与锁定判断。
 * 因此本类不复用其类继承，而是独立实现搬运循环：只借用其 public static 的
 * {@code suckInItems}/{@code addItem}/{@code getContainerAt} 等原子操作，其余行为保持一致。
 */
public class WoodenHopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper {
	/** 搬运成功后的冷却 tick：原版铁漏斗为 8，木漏斗为其 4 倍（每 32 tick 移动 1 个物品）。 */
	public static final int MOVE_ITEM_SPEED = 32;
	public static final int HOPPER_CONTAINER_SIZE = 5;
	private static final int NO_COOLDOWN_TIME = -1;
	private static final Component DEFAULT_NAME = Component.translatable("container.hopper");

	private NonNullList<ItemStack> items = NonNullList.withSize(HOPPER_CONTAINER_SIZE, ItemStack.EMPTY);
	private int cooldownTime = NO_COOLDOWN_TIME;
	private Direction facing;

	public WoodenHopperBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		super(ModBlockEntityTypes.WOODEN_HOPPER, worldPosition, blockState);
		this.facing = blockState.getValue(HopperBlock.FACING);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		if (!this.tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, this.items);
		}
		this.cooldownTime = input.getIntOr("TransferCooldown", -1);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		if (!this.trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, this.items);
		}
		output.putInt("TransferCooldown", this.cooldownTime);
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public ItemStack removeItem(final int slot, final int count) {
		this.unpackLootTable(null);
		return ContainerHelper.removeItem(this.getItems(), slot, count);
	}

	@Override
	public void setItem(final int slot, final ItemStack itemStack) {
		this.unpackLootTable(null);
		this.getItems().set(slot, itemStack);
		itemStack.limitSize(this.getMaxStackSize(itemStack));
	}

	@Override
	public void setBlockState(final BlockState blockState) {
		super.setBlockState(blockState);
		this.facing = blockState.getValue(HopperBlock.FACING);
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	/**
	 * 方块 tick 入口（由 {@code WoodenHopperBlock.getTicker} 挂接）。
	 * 与 26.2 原版 {@code HopperBlockEntity.pushItemsTick} 等价，但：
	 * 成功搬运冷却 32 而非 8；不检查 {@code HopperBlock.ENABLED}（红石锁不住）。
	 */
	public static void pushItemsTick(final Level level, final BlockPos pos, final BlockState state, final WoodenHopperBlockEntity entity) {
		entity.cooldownTime--;
		if (entity.isOnCooldown()) {
			return;
		}
		entity.cooldownTime = 0;
		if (!QuirkyConfigHolder.get().woodenHopper) {
			return;
		}
		if (level.isClientSide()) {
			return;
		}
		boolean changed = false;
		if (!entity.isEmpty()) {
			changed = ejectItems(level, pos, entity);
		}
		if (!entity.inventoryFull()) {
			changed |= HopperBlockEntity.suckInItems(level, entity);
		}
		if (changed) {
			entity.setCooldown(MOVE_ITEM_SPEED);
			setChanged(level, pos, state);
		}
	}

	/** 把物品吐向 facing 方向的容器（等价原版 {@code HopperBlockEntity.ejectItems}）。 */
	private static boolean ejectItems(final Level level, final BlockPos blockPos, final WoodenHopperBlockEntity self) {
		Container container = HopperBlockEntity.getContainerAt(level, blockPos.relative(self.facing));
		if (container == null) {
			return false;
		}
		Direction direction = self.facing.getOpposite();
		if (isFullContainer(container, direction)) {
			return false;
		}
		for (int slot = 0; slot < self.getContainerSize(); slot++) {
			ItemStack itemStack = self.getItem(slot);
			if (!itemStack.isEmpty()) {
				int originalCount = itemStack.getCount();
				ItemStack result = HopperBlockEntity.addItem(self, container, self.removeItem(slot, 1), direction);
				if (result.isEmpty()) {
					container.setChanged();
					return true;
				}
				itemStack.setCount(originalCount);
				if (originalCount == 1) {
					self.setItem(slot, itemStack);
				}
			}
		}
		return false;
	}

	private static boolean isFullContainer(final Container container, final Direction direction) {
		if (container instanceof WorldlyContainer worldly) {
			for (int slot : worldly.getSlotsForFace(direction)) {
				ItemStack itemStack = container.getItem(slot);
				if (itemStack.getCount() < itemStack.getMaxStackSize()) {
					return false;
				}
			}
			return true;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack itemStack = container.getItem(slot);
			if (itemStack.getCount() < itemStack.getMaxStackSize()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 物品实体落入漏斗时尝试吸取（等价原版 {@code HopperBlockEntity.entityInside}，锁定无效）。
	 */
	public static void entityInside(
		final Level level, final BlockPos pos, final BlockState state, final Entity entity, final WoodenHopperBlockEntity hopper
	) {
		if (!QuirkyConfigHolder.get().woodenHopper) {
			return;
		}
		if (entity instanceof ItemEntity itemEntity
			&& !itemEntity.getItem().isEmpty()
			&& entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ()).intersects(hopper.getSuckAabb())) {
			if (!hopper.isOnCooldown() && HopperBlockEntity.addItem(hopper, itemEntity)) {
				hopper.setCooldown(MOVE_ITEM_SPEED);
				setChanged(level, pos, state);
			}
		}
	}

	private void setCooldown(final int time) {
		this.cooldownTime = time;
	}

	private boolean isOnCooldown() {
		return this.cooldownTime > 0;
	}

	private boolean inventoryFull() {
		for (ItemStack itemStack : this.items) {
			if (itemStack.isEmpty() || itemStack.getCount() != itemStack.getMaxStackSize()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public double getLevelX() {
		return this.worldPosition.getX() + 0.5;
	}

	@Override
	public double getLevelY() {
		return this.worldPosition.getY() + 0.5;
	}

	@Override
	public double getLevelZ() {
		return this.worldPosition.getZ() + 0.5;
	}

	@Override
	public boolean isGridAligned() {
		return true;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(final NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
		return new HopperMenu(containerId, inventory, this);
	}
}
