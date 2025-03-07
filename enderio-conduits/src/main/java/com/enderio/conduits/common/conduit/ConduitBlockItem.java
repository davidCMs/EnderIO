package com.enderio.conduits.common.conduit;

import com.enderio.conduits.api.EnderIOConduitsRegistries;
import com.enderio.conduits.api.Conduit;
import com.enderio.base.common.init.EIOCreativeTabs;
import com.enderio.conduits.EnderIOConduits;
import com.enderio.conduits.common.conduit.block.ConduitBundleBlockEntity;
import com.enderio.conduits.common.init.ConduitBlocks;
import com.enderio.conduits.common.init.ConduitComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = EnderIOConduits.MODULE_MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ConduitBlockItem extends BlockItem {

    public ConduitBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack getStackFor(Holder<Conduit<?>> conduit, int count) {
        var stack = new ItemStack(ConduitBlocks.CONDUIT.asItem(), count);
        stack.set(ConduitComponents.CONDUIT, conduit);
        return stack;
    }

    @Override
    public Component getName(ItemStack pStack) {
        Holder<Conduit<?>> conduit = pStack.get(ConduitComponents.CONDUIT);
        if (conduit == null) {
            return super.getName(pStack);
        }

        return conduit.value().description();
    }

    // Do not use block description, as that is Conduit Bundle.
    // This will ensure missing conduits use the missing conduit string.
    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        @Nullable
        Player player = context.getPlayer();
        BlockPos blockpos = context.getClickedPos();
        ItemStack itemstack = context.getItemInHand();

        Holder<Conduit<?>> conduit = itemstack.get(ConduitComponents.CONDUIT);

        // Handle placing into an existing block
        if (conduit != null && level.getBlockEntity(blockpos) instanceof ConduitBundleBlockEntity blockEntity) {
            if (blockEntity.hasType(conduit)) {
                // Pass through to block
                return level.getBlockState(blockpos).useWithoutItem(level, player, context.getHitResult());
            }

            blockEntity.addType(conduit, player);
            if (level.isClientSide()) {
                blockEntity.updateClient();
            }

            BlockState blockState = level.getBlockState(blockpos);
            SoundType soundtype = blockState.getSoundType(level, blockpos, context.getPlayer());
            level.playSound(player, blockpos, this.getPlaceSound(blockState, level, blockpos, context.getPlayer()), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, blockpos, GameEvent.Context.of(player, blockState));

            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return super.place(context);
    }

    @Override
    public void appendHoverText(ItemStack pStack, TooltipContext pContext, List<Component> pTooltipComponents, TooltipFlag pTooltipFlag) {
        Holder<Conduit<?>> conduit = pStack.get(ConduitComponents.CONDUIT);
        if (conduit != null) {
            conduit.value().addToTooltip(pContext, pTooltipComponents::add, pTooltipFlag);
        }

        super.appendHoverText(pStack, pContext, pTooltipComponents, pTooltipFlag);
    }

    // High priority so conduits appear at the top of the conduits tab.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == EIOCreativeTabs.CONDUITS_TAB.get()) {
            var registry = event.getParameters().holders().lookupOrThrow(EnderIOConduitsRegistries.Keys.CONDUIT);
            var conduitTypes = registry.listElements().toList();

            var conduitClassTypes = conduitTypes.stream()
                .map(e -> e.value().getClass())
                .sorted(Comparator.comparing(Class::getName))
                .distinct()
                .toList();

            for (var conduitClass : conduitClassTypes) {
                var matchingConduitTypes = conduitTypes.stream()
                    .filter(e -> e.value().getClass() == conduitClass)
                    // GRIM...
                    .sorted((o1, o2) -> compareConduitTo(o1.value(), o2.value()))
                    .toList();

                for (var conduitType : matchingConduitTypes) {
                    event.accept(getStackFor(conduitType, 1), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                }
            }
        }
    }

    private static <T extends Conduit<T>> int compareConduitTo(Conduit<T> o1, Conduit<?> o2) {
        return o1.compareTo((T)o2);
    }
}
