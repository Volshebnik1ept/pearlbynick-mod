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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public class PearlOwnerClient implements ClientModInitializer {

    /** Сколько тиков нужно непрерывно смотреть на жемчужину, чтобы вывести ник в чат (20 тик/с * 3с). */
    private static final int LOOK_TICKS_THRESHOLD = 60;
    /** Дальность луча взгляда для поиска жемчужины (в блоках). */
    private static final double LOOK_REACH = 64.0;
    /** Небольшое расширение хитбокса жемчужины, чтобы было проще "прицелиться". */
    private static final double LOOK_HIT_INFLATE = 0.35;

    private Entity lookTarget = null;
    private int lookTicks = 0;
    private boolean lookAnnounced = false;

    @Override
    public void onInitializeClient() {
        PlayerUuidCache.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getConnection() == null || client.player == null) return;
            if (client.player.tickCount % 20 != 0) return;
            for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
                var profile = entry.getProfile();
                // ПРОВЕРИТЬ: если ругается на id()/name() — открой GameProfile в IDE
                // (Ctrl+клик) и посмотри реальные имена методов в этой версии.
                PlayerUuidCache.update(profile.id(), profile.name());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::checkPearlGazeTick);

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

        // Позиция камеры — camera() на LevelRenderContext в 26.x убран.
        Vec3 camPos = context.levelState().cameraRenderState.pos;
        // ПРОВЕРИТЬ: поворот камеры (раньше camera.rotation() -> Quaternionf).
        // Открой CameraRenderState в IDE и найди поле с ориентацией/поворотом,
        // подставь его вместо строки poseStack.mulPose(...) ниже.
        // Quaternionf camRotation = context.levelState().cameraRenderState.orientation; // пример

        float tickDelta = context.deltaTracker().getGameTimeDeltaPartialTick(true);
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;

        // ПРОВЕРИТЬ: Font.drawInBatch в 26.2 убран. Текст теперь через
        // OrderedSubmitNodeCollector#submitText(...), доступный, вероятно, через
        // context.submitNodeCollector(). Открой этот класс в IDE и подставь
        // реальную сигнатуру вместо закомментированного вызова ниже.
        var submitNodeCollector = context.submitNodeCollector();
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
            // poseStack.mulPose(camRotation); // подставь реальный поворот камеры (см. выше)
            poseStack.scale(-0.025f, -0.025f, 0.025f);

            float bgOpacity = client.options.getBackgroundOpacity(0.25f);
            int background = (int) (bgOpacity * 255.0f) << 24;
            float halfWidth = -font.width(name) / 2f;

            // ЗАГЛУШКА — подставь реальный вызов submitText после проверки сигнатуры:
            // submitNodeCollector.submitText(poseStack, halfWidth, 0, name, false,
            //         Font.DisplayMode.SEE_THROUGH, 0xF000F0, 0xFFFFFF, background);

            poseStack.popPose();
        }
    }

    /**
     * Каждый тик проверяет, смотрит ли игрок на эндер-жемчуг, и если это
     * длится дольше {@link #LOOK_TICKS_THRESHOLD} тиков подряд — один раз
     * пишет ник владельца в чат. Взгляд "сбрасывается", если игрок отвёл
     * камеру или жемчужина исчезла (подобрана/протухла) — так что при новом
     * взгляде на ту же (или другую) жемчужину отсчёт и сообщение будут снова.
     */
    private void checkPearlGazeTick(Minecraft client) {
        if (client.level == null || client.player == null || client.getConnection() == null) return;

        Entity target = findLookedAtPearl(client);

        if (target == null || target != lookTarget || target.isRemoved()) {
            lookTarget = target != null && !target.isRemoved() ? target : null;
            lookTicks = 0;
            lookAnnounced = false;
            return;
        }

        lookTicks++;
        if (lookTicks >= LOOK_TICKS_THRESHOLD && !lookAnnounced) {
            lookAnnounced = true;
            announcePearlOwner(client, target);
        }
    }

    /** Ищет ближайшую эндер-жемчужину, пересекающую луч взгляда игрока. */
    private Entity findLookedAtPearl(Minecraft client) {
        var player = client.player;
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(LOOK_REACH));

        Entity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl)) continue;

            Optional<Vec3> hit = entity.getBoundingBox().inflate(LOOK_HIT_INFLATE).clip(eye, end);
            if (hit.isEmpty()) continue;

            double distSq = eye.distanceToSqr(hit.get());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = entity;
            }
        }
        return closest;
    }

    private void announcePearlOwner(Minecraft client, Entity pearl) {
        UUID ownerUuid = ((ProjectileOwnerAccessor) pearl).pearlowner$getOwnerUuid();
        if (ownerUuid == null) return;

        String name = PlayerUuidCache.getName(ownerUuid);
        if (name == null) {
            name = ownerUuid.toString().substring(0, 8) + "…";
        }

        client.player.sendSystemMessage(
                Component.literal("§dЖемчужина §7принадлежит §f" + name));
    }
}
