import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * TEMPORARY diagnostic tool (deleted before release).
 *
 * Runs on the Loom-remapped Minecraft classpath and prints the exact 1.21.1 signatures of every
 * class this mod touches into api-dump.txt. Only uses java.lang.reflect, so it compiles even when
 * the mod sources do not.
 */
public final class ApiDump {
    private static final String[] CLASSES = {
        "net.minecraft.tags.BiomeTags",
        "net.minecraft.world.level.storage.LevelResource",
        "net.minecraft.world.item.component.ItemAttributeModifiers",
        "net.minecraft.core.component.DataComponentType",
        "net.minecraft.core.component.DataComponentMap",
        "net.minecraft.world.level.block.Blocks",
        "net.minecraft.world.level.block.RenderShape",
        "net.minecraft.world.level.block.HorizontalDirectionalBlock",
        "net.minecraft.world.level.block.state.properties.Property",
        "net.minecraft.world.level.block.state.properties.BooleanProperty",
        "net.minecraft.world.level.block.state.properties.EnumProperty",
        "net.minecraft.world.level.block.state.properties.DirectionProperty",
        "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase",
        "net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity",
        "net.minecraft.world.level.block.entity.ChestBlockEntity",
        "net.minecraft.world.level.Explosion",
        "net.minecraft.world.level.levelgen.Heightmap",
        "net.minecraft.world.entity.monster.Skeleton",
        "net.minecraft.world.entity.monster.Pillager",
        "net.minecraft.world.MenuProvider",
        "net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents",
        "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents",
        "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",
        "net.minecraft.world.entity.ai.brain.Brain",
        "net.minecraft.world.entity.ai.sensing.Sensor",
        "net.minecraft.world.entity.ai.memory.MemoryModuleType",
        "net.minecraft.world.entity.ai.behavior.Behavior",
        "net.minecraft.core.BlockPos",
        "net.minecraft.core.Direction",
        "net.minecraft.core.Holder",
        "net.minecraft.core.HolderLookup",
        "net.minecraft.core.HolderLookup$Provider",
        "net.minecraft.core.Registry",
        "net.minecraft.core.RegistryAccess",
        "net.minecraft.core.component.DataComponents",
        "net.minecraft.core.registries.BuiltInRegistries",
        "net.minecraft.core.registries.Registries",
        "net.minecraft.nbt.CompoundTag",
        "net.minecraft.nbt.ListTag",
        "net.minecraft.nbt.Tag",
        "net.minecraft.network.FriendlyByteBuf",
        "net.minecraft.network.RegistryFriendlyByteBuf",
        "net.minecraft.network.chat.Component",
        "net.minecraft.network.chat.MutableComponent",
        "net.minecraft.network.codec.ByteBufCodecs",
        "net.minecraft.network.codec.StreamCodec",
        "net.minecraft.network.protocol.common.custom.CustomPacketPayload",
        "net.minecraft.resources.ResourceKey",
        "net.minecraft.resources.ResourceLocation",
        "net.minecraft.server.MinecraftServer",
        "net.minecraft.server.level.ServerLevel",
        "net.minecraft.server.level.ServerPlayer",
        "net.minecraft.sounds.SoundEvent",
        "net.minecraft.sounds.SoundEvents",
        "net.minecraft.tags.BlockTags",
        "net.minecraft.tags.ItemTags",
        "net.minecraft.tags.TagKey",
        "net.minecraft.util.Mth",
        "net.minecraft.util.RandomSource",
        "net.minecraft.world.Container",
        "net.minecraft.world.ContainerHelper",
        "net.minecraft.world.InteractionHand",
        "net.minecraft.world.InteractionResult",
        "net.minecraft.world.InteractionResultHolder",
        "net.minecraft.world.ItemInteractionResult",
        "net.minecraft.world.InteractionResultHolder",
        "net.minecraft.world.SimpleContainer",
        "net.minecraft.world.damagesource.DamageSource",
        "net.minecraft.world.damagesource.DamageSources",
        "net.minecraft.world.effect.MobEffectInstance",
        "net.minecraft.world.effect.MobEffects",
        "net.minecraft.world.entity.Entity",
        "net.minecraft.world.entity.EquipmentSlot",
        "net.minecraft.world.entity.LivingEntity",
        "net.minecraft.world.entity.Mob",
        "net.minecraft.world.entity.MobCategory",
        "net.minecraft.world.entity.MobSpawnType",
        "net.minecraft.world.entity.PathfinderMob",
        "net.minecraft.world.entity.SpawnGroupData",
        "net.minecraft.world.entity.EntityType",
        "net.minecraft.world.entity.EntityType$Builder",
        "net.minecraft.world.entity.ai.attributes.Attribute",
        "net.minecraft.world.entity.ai.attributes.AttributeInstance",
        "net.minecraft.world.entity.ai.attributes.AttributeSupplier",
        "net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder",
        "net.minecraft.world.entity.ai.attributes.Attributes",
        "net.minecraft.world.entity.ai.goal.FloatGoal",
        "net.minecraft.world.entity.ai.goal.Goal",
        "net.minecraft.world.entity.ai.goal.GoalSelector",
        "net.minecraft.world.entity.ai.goal.LookAtPlayerGoal",
        "net.minecraft.world.entity.ai.goal.MeleeAttackGoal",
        "net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal",
        "net.minecraft.world.entity.ai.goal.OpenDoorGoal",
        "net.minecraft.world.entity.ai.goal.RandomLookAroundGoal",
        "net.minecraft.world.entity.ai.goal.RangedAttackGoal",
        "net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal",
        "net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal",
        "net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal",
        "net.minecraft.world.entity.ai.navigation.GroundPathNavigation",
        "net.minecraft.world.entity.ai.navigation.PathNavigation",
        "net.minecraft.world.entity.animal.Animal",
        "net.minecraft.world.entity.animal.Wolf",
        "net.minecraft.world.entity.item.ItemEntity",
        "net.minecraft.world.entity.monster.Monster",
        "net.minecraft.world.entity.monster.Zombie",
        "net.minecraft.world.entity.npc.AbstractVillager",
        "net.minecraft.world.entity.npc.Villager",
        "net.minecraft.world.entity.player.Inventory",
        "net.minecraft.world.entity.player.Player",
        "net.minecraft.world.entity.projectile.AbstractArrow",
        "net.minecraft.world.entity.projectile.Arrow",
        "net.minecraft.world.entity.projectile.Projectile",
        "net.minecraft.world.inventory.AbstractContainerMenu",
        "net.minecraft.world.inventory.MenuType",
        "net.minecraft.world.item.ArmorItem",
        "net.minecraft.world.item.ArmorMaterial",
        "net.minecraft.world.item.ArmorMaterials",
        "net.minecraft.world.item.ArrowItem",
        "net.minecraft.world.item.BowItem",
        "net.minecraft.world.item.CrossbowItem",
        "net.minecraft.world.item.DiggerItem",
        "net.minecraft.world.item.Item",
        "net.minecraft.world.item.Item$Properties",
        "net.minecraft.world.item.ItemStack",
        "net.minecraft.world.item.Items",
        "net.minecraft.world.item.ProjectileWeaponItem",
        "net.minecraft.world.item.Rarity",
        "net.minecraft.world.item.ShieldItem",
        "net.minecraft.world.item.SwordItem",
        "net.minecraft.world.item.Tier",
        "net.minecraft.world.item.TieredItem",
        "net.minecraft.world.item.Tiers",
        "net.minecraft.world.item.context.BlockPlaceContext",
        "net.minecraft.world.item.context.UseOnContext",
        "net.minecraft.world.item.crafting.CraftingInput",
        "net.minecraft.world.item.crafting.CraftingRecipe",
        "net.minecraft.world.item.crafting.Ingredient",
        "net.minecraft.world.item.crafting.Recipe",
        "net.minecraft.world.item.crafting.RecipeManager",
        "net.minecraft.world.item.crafting.RecipeSerializer",
        "net.minecraft.world.item.crafting.RecipeType",
        "net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer",
        "net.minecraft.world.level.BlockGetter",
        "net.minecraft.world.level.Level",
        "net.minecraft.world.level.LevelAccessor",
        "net.minecraft.world.level.LevelReader",
        "net.minecraft.world.level.block.BaseEntityBlock",
        "net.minecraft.world.level.block.Block",
        "net.minecraft.world.level.block.state.BlockBehaviour",
        "net.minecraft.world.level.block.state.BlockBehaviour$Properties",
        "net.minecraft.world.level.block.ChestBlock",
        "net.minecraft.world.level.block.EntityBlock",
        "net.minecraft.world.level.block.SoundType",
        "net.minecraft.world.level.block.entity.BlockEntity",
        "net.minecraft.world.level.block.entity.BlockEntityType",
        "net.minecraft.world.level.block.entity.BlockEntityType$Builder",
        "net.minecraft.world.level.block.state.BlockState",
        "net.minecraft.world.level.block.state.StateDefinition",
        "net.minecraft.world.level.block.state.properties.BlockStateProperties",
        "net.minecraft.world.level.chunk.ChunkAccess",
        "net.minecraft.world.level.chunk.LevelChunk",
        "net.minecraft.world.level.ChunkPos",
        "net.minecraft.world.level.biome.Biome",
        "net.minecraft.world.level.material.MapColor",
        "net.minecraft.world.level.material.PushReaction",
        "net.minecraft.world.level.saveddata.SavedData",
        "net.minecraft.world.level.saveddata.SavedData$Factory",
        "net.minecraft.world.phys.AABB",
        "net.minecraft.world.phys.BlockHitResult",
        "net.minecraft.world.phys.Vec3",
        "net.minecraft.world.phys.shapes.CollisionContext",
        "net.minecraft.world.phys.shapes.Shapes",
        "net.minecraft.world.phys.shapes.VoxelShape",
        "net.minecraft.commands.CommandSourceStack",
        "net.minecraft.commands.Commands",
        "net.minecraft.advancements.Advancement",
        "net.minecraft.advancements.AdvancementHolder",
        "net.minecraft.advancements.AdvancementProgress",
        "net.fabricmc.loader.api.FabricLoader",
        "net.fabricmc.loader.api.ModContainer",
        "net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback",
        "net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents",
        "net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents",
        "net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents",
        "net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents",
        "net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents",
        "net.fabricmc.fabric.api.event.player.AttackBlockCallback",
        "net.fabricmc.fabric.api.event.player.UseBlockCallback",
        "net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents",
        "net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry",
        "net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking",
        "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents",
        "net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry",
        "net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents",
        "net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags",
        "net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags",
        "net.minecraft.client.Minecraft",
        "net.minecraft.client.DeltaTracker",
        "net.minecraft.client.gui.GuiGraphics",
        "net.minecraft.client.gui.screens.Screen",
        "net.minecraft.client.gui.components.Button",
        "net.minecraft.client.gui.components.Button$Builder",
        "net.minecraft.client.gui.components.AbstractWidget",
        "net.minecraft.client.gui.components.events.GuiEventListener",
        "net.minecraft.client.KeyMapping",
        "net.minecraft.client.player.LocalPlayer",
        "net.minecraft.client.multiplayer.ClientLevel",
        "net.minecraft.client.renderer.entity.EntityRenderer",
        "net.minecraft.client.renderer.entity.EntityRendererProvider",
        "net.minecraft.client.renderer.entity.MobRenderer",
        "net.minecraft.client.renderer.entity.HumanoidMobRenderer",
        "net.minecraft.client.renderer.entity.EntityRenderers",
        "net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer",
        "net.minecraft.client.renderer.entity.layers.ItemInHandLayer",
        "net.minecraft.client.renderer.entity.layers.RenderLayer",
        "net.minecraft.client.model.HumanoidModel",
        "net.minecraft.client.model.geom.ModelLayers",
        "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback",
        "net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry",
        "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents",
        "net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking",
        "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper"
    };

    private static PrintWriter out;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("scan")) {
            scan();
            return;
        }
        out = new PrintWriter(new FileWriter("api-dump.txt"));
        for (String name : CLASSES) {
            dump(name);
        }
        out.flush();
        out.close();
        System.out.println("api-dump.txt written (" + CLASSES.length + " classes)");
    }

    /** Writes every net.minecraft / fabric-api class name found on the classpath into api-classes.txt. */
    private static void scan() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            java.io.File f = new java.io.File(entry);
            if (!f.isFile() || !f.getName().endsWith(".jar")) continue;
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(f)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (!n.endsWith(".class")) continue;
                    if (n.startsWith("net/minecraft/") || n.startsWith("net/fabricmc/fabric/api/")
                            || n.startsWith("net/fabricmc/api/") || n.startsWith("net/fabricmc/loader/api")) {
                        names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                    }
                }
            } catch (Throwable t) {
                // ignore unreadable jars
            }
        }
        try (PrintWriter w = new PrintWriter(new FileWriter("api-classes.txt"))) {
            for (String n : names) {
                w.println(n);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return;
        }
        System.out.println("api-classes.txt written (" + names.size() + " classes)");
    }

    private static void dump(String name) {
        Class<?> c;
        try {
            c = Class.forName(name, false, ApiDump.class.getClassLoader());
        } catch (Throwable t) {
            out.println("=== " + name + "  ::  MISSING (" + t.getClass().getSimpleName() + ")");
            return;
        }
        out.println();
        out.println("=== " + name + "  ::  " + mods(c.getModifiers()) + kind(c)
                + " extends " + typeName(c.getGenericSuperclass())
                + " implements " + typeNames(c.getGenericInterfaces()));

        List<String> lines = new ArrayList<>();
        try {
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (Modifier.isPrivate(k.getModifiers())) continue;
                try {
                    lines.add("  CTOR " + mods(k.getModifiers()) + c.getSimpleName() + "(" + params(k.getGenericParameterTypes()) + ")");
                } catch (Throwable t) {
                    lines.add("  CTOR <?> (" + t.getClass().getSimpleName() + ")");
                }
            }
        } catch (Throwable t) {
            lines.add("  # constructors unavailable: " + t.getClass().getSimpleName());
        }
        try {
            for (Field f : c.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isPrivate(m) || f.isSynthetic()) continue;
                lines.add("  FIELD " + mods(m) + typeName(f.getGenericType()) + " " + f.getName());
            }
        } catch (Throwable t) {
            lines.add("  # fields unavailable: " + t.getClass().getSimpleName());
        }
        try {
            for (Method mm : c.getDeclaredMethods()) {
                int m = mm.getModifiers();
                if (Modifier.isPrivate(m) || mm.isSynthetic() || mm.isBridge()) continue;
                try {
                    String thr = mm.getExceptionTypes().length == 0 ? "" : " throws " + typeNames(mm.getExceptionTypes());
                    lines.add("  METHOD " + mods(m) + typeName(mm.getGenericReturnType()) + " " + mm.getName()
                            + "(" + params(mm.getGenericParameterTypes()) + ")" + thr);
                } catch (Throwable t) {
                    lines.add("  METHOD <?> " + mm.getName() + " (" + t.getClass().getSimpleName() + ")");
                }
            }
        } catch (Throwable t) {
            lines.add("  # methods unavailable: " + t.getClass().getSimpleName());
        }
        lines.sort(null);
        for (String line : lines) {
            out.println(line);
        }
    }

    private static String kind(Class<?> c) {
        if (c.isInterface()) return "interface";
        if (c.isEnum()) return "enum";
        if (c.isRecord()) return "record";
        if (Modifier.isAbstract(c.getModifiers())) return "abstract class";
        return "class";
    }

    private static String mods(int m) {
        StringBuilder sb = new StringBuilder();
        if (Modifier.isPublic(m)) sb.append("public ");
        else if (Modifier.isProtected(m)) sb.append("protected ");
        if (Modifier.isStatic(m)) sb.append("static ");
        if (Modifier.isFinal(m)) sb.append("final ");
        if (Modifier.isAbstract(m)) sb.append("abstract ");
        if (Modifier.isNative(m)) sb.append("native ");
        return sb.toString();
    }

    private static String typeName(Type t) {
        if (t == null) return "<none>";
        // strip package prefixes but keep nested-class separators and generic arguments intact
        return t.getTypeName().replaceAll("(?:[a-z][a-zA-Z0-9_$]*\\.)+", "");
    }

    private static String typeNames(Type[] types) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(typeName(types[i]));
        }
        return sb.toString();
    }

    private static String params(Type[] types) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(typeName(types[i]));
        }
        return sb.toString();
    }
}
