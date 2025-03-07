package com.enderio.base.client;

import com.enderio.EnderIOBase;
import com.enderio.base.client.decorator.GlassIconDecorator;
import com.enderio.base.client.paint.model.PaintedBlockGeometry;
import com.enderio.base.client.particle.RangeParticle;
import com.enderio.base.client.renderer.block.EnderSkullRenderer;
import com.enderio.base.client.renderer.glider.ActiveGliderRenderLayer;
import com.enderio.base.client.travel.TravelTargetRendering;
import com.enderio.base.common.block.skull.EnderSkullBlock;
import com.enderio.base.common.init.EIOBlockEntities;
import com.enderio.base.common.init.EIOBlocks;
import com.enderio.base.common.init.EIOItems;
import com.enderio.base.common.init.EIOParticles;
import com.enderio.core.client.item.FluidBarDecorator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = EnderIOBase.MODULE_MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@Mod(value = EnderIOBase.MODULE_MOD_ID, dist = Dist.CLIENT)
public class EnderIOBaseClient {

    private static final Map<Item, ModelResourceLocation> HANG_GLIDER_MODEL_LOCATION = new HashMap<>();
    public static final Map<Item, BakedModel> GLIDER_MODELS = new HashMap<>();

    public EnderIOBaseClient(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        TravelTargetRendering.init();
    }

    @SubscribeEvent
    public static void additionalModels(ModelEvent.RegisterAdditional event) {
        Set<ResourceLocation> gliderModels = Minecraft
            .getInstance()
            .getResourceManager()
            .listResources("models/enderio_glider", rl -> rl.getPath().endsWith(".json"))
            .keySet();

        for (ResourceLocation gliderModelPath : gliderModels) {
            Optional<Item> gliderItem = findGliderForModelRL(gliderModelPath);
            if (gliderItem.isPresent()) {
                ResourceLocation modelLookupLocation = ResourceLocation.fromNamespaceAndPath(gliderModelPath.getNamespace(),
                    gliderModelPath.getPath().substring("models/".length(), gliderModelPath.getPath().length() - 5));

                ModelResourceLocation modelLocation = ModelResourceLocation.standalone(modelLookupLocation);
                event.register(modelLocation);
                HANG_GLIDER_MODEL_LOCATION.put(gliderItem.get(), modelLocation);
            }
        }
    }

    @SubscribeEvent
    public static void itemDecorators(RegisterItemDecorationsEvent event) {
        // Register tools
        event.register(EIOItems.LEVITATION_STAFF.get(), FluidBarDecorator.INSTANCE);
//        event.register(EIOItems.DARK_STEEL_AXE.get(), EnergyBarDecorator.INSTANCE);
//        event.register(EIOItems.DARK_STEEL_PICKAXE.get(), EnergyBarDecorator.INSTANCE);

        // Register all glass blocks
        EIOBlocks.GLASS_BLOCKS.values().forEach(blocks -> blocks.getAllBlocks().forEach(block -> event.register(block.get(), GlassIconDecorator.INSTANCE)));
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new ActiveGliderRenderLayer(playerRenderer));
            }
        }
    }

    @SubscribeEvent
    public static void bakingCompleted(ModelEvent.BakingCompleted event) {
        GLIDER_MODELS.clear();
        HANG_GLIDER_MODEL_LOCATION.forEach((item, modelRL) -> {
            BakedModel bakedModel = event.getModels().get(modelRL);
            if (bakedModel != null) {
                GLIDER_MODELS.put(item, bakedModel);
            }
        });
        HANG_GLIDER_MODEL_LOCATION.clear();
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(EIOParticles.RANGE_PARTICLE.get(), RangeParticle.Provider::new);
    }

    private static Optional<Item> findGliderForModelRL(ResourceLocation rl) {
        String namespace = rl.getNamespace();
        String path = rl.getPath().substring("models/enderio_glider/".length(), rl.getPath().length() - 5);
        return Optional.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path)));
    }

    @SubscribeEvent
    public static void modelInit(ModelEvent.RegisterGeometryLoaders event) {
        event.register(EnderIOBase.loc("painted_block"), new PaintedBlockGeometry.Loader());
    }

    @SubscribeEvent
    public static void modelRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EIOBlockEntities.ENDER_SKULL.get(), EnderSkullRenderer::new);
    }

    @SubscribeEvent
    public static void registerModelLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EnderSkullRenderer.ENDER_SKULL, EnderSkullRenderer.EnderSkullModel::createMobHeadLayer);
    }

    @SubscribeEvent
    public static void registerEnderSkulls(EntityRenderersEvent.CreateSkullModels event) {
        event.registerSkullModel(EnderSkullBlock.EIOSkulls.ENDERMAN, new EnderSkullRenderer.EnderSkullModel(event.getEntityModelSet().bakeLayer(EnderSkullRenderer.ENDER_SKULL)));
        SkullBlockRenderer.SKIN_BY_TYPE.put(EnderSkullBlock.EIOSkulls.ENDERMAN, ResourceLocation.withDefaultNamespace("textures/entity/enderman/enderman.png"));
    }
}
