package dev.pearlowner;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.pearlowner.mixin.ProjectileOwnerAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.UUID;

public class PearlOwnerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PlayerUuidCache.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getConnection() == null || client.player == null) return;
            if (client.player.tickCount % 20 != 0) return;
            for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
                var profile = entry.getProfile();
                PlayerUuidCache.update(profile.getId(), profile.getName());
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("uuid")
                        .then(ClientCommands.argument("nick", StringArgumentType.string())
                                .executes(ctx -> {
                                    String nick = StringArgumentType.getString(ctx, "nick");
                                    UUID uuid = PlayerUuidCache.getUuid(nick);
                                    Minecraft client = Minecraft.getInstance();
                                    if (uuid == null) {
                                        client.player.sendSystemMessage(
                                                Component.literal("§cUUID для '" + nick + "' пока нет в кэше (игрок не был виден в таб-листе)."));
                                    } else {
                                        client.player.sendSystemMessage(
                                                Component.literal("§a" + nick + " §7-> §f" + uuid));
                                    }
                                    return 1;
                                }))
        ));

        LevelRenderEvents.AFTER_ENTITIES.register(this::renderOwnerLabels);
    }

    private void renderOwnerLabels(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Camera camera = context.camera();
        Vec3 camPos = camera.getPosition();
        float tickDelta = context.tickCounter().getGameTimeDeltaPartialTick(true);
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;

        MultiBufferSource.BufferSource bufferSource = context.bufferSource();
        Font font = client.font;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile projectile)) continue;

            UUID ownerUuid = ((ProjectileOwnerAccessor) projectile).pearlowner$getOwnerUuid();
            if (ownerUuid == null) continue;

            String name = PlayerUuidCache.getName(ownerUuid);
            if (name == null) {
                name = ownerUuid.toString().substring(0, 8) + "…";
            }

            double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
            double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
            double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());

            poseStack.pushPose();
            poseStack.translate(x - camPos.x, y - camPos.y + entity.getBbHeight() + 0.5, z - camPos.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-0.025f, -0.025f, 0.025f);

            Matrix4f matrix = poseStack.last().pose();
            float bgOpacity = client.options.getBackgroundOpacity(0.25f);
            int background = (int) (bgOpacity * 255.0f) << 24;
            float halfWidth = -font.width(name) / 2f;

            font.drawInBatch(name, halfWidth, 0, 0xFFFFFF, false, matrix, bufferSource,
                    Font.DisplayMode.SEE_THROUGH, background, 0xF000F0);

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }
}
