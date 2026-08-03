package dev.quirky.block;

import com.mojang.serialization.MapCodec;
import dev.quirky.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CloudBlock extends Block {
	public static final MapCodec<CloudBlock> CODEC = simpleCodec(CloudBlock::new);
	private static final int LIFETIME_TICKS = 200;
	private static final Vec3 STUCK_SPEED = new Vec3(0.95, 0.6, 0.95);

	public CloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<CloudBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return true;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return Shapes.block();
	}

	@Override
	protected void entityInside(
		BlockState state,
		Level level,
		BlockPos pos,
		Entity entity,
		InsideBlockEffectApplier effectApplier,
		boolean isPrecise
	) {
		entity.makeStuckInBlock(state, STUCK_SPEED);
		if (entity.isOnFire()) {
			entity.clearFire();
			if (!level.isClientSide()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
		}
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack,
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		InteractionHand hand,
		BlockHitResult hitResult
	) {
		if (!itemStack.is(Items.GLASS_BOTTLE)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			player.playSound(SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);
			ItemStack bottledCloud = new ItemStack(ModItems.BOTTLED_CLOUD);
			if (!player.hasInfiniteMaterials()) {
				itemStack.shrink(1);
			}
			if (itemStack.isEmpty()) {
				player.setItemInHand(hand, bottledCloud);
			} else {
				player.getInventory().placeItemBackInInventory(bottledCloud);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, LIFETIME_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockState(pos).is(this)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			double x = pos.getX() + random.nextDouble();
			double z = pos.getZ() + random.nextDouble();
			level.addParticle(ParticleTypes.CLOUD, x, pos.getY() - 0.1, z, 0.0, -0.05, 0.0);
		}
	}
}
