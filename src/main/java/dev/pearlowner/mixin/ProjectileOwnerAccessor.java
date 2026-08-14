package dev.pearlowner.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/**
 * Достаёт приватное поле ownerUUID у Projectile (официальное имя класса в
 * Mojang mappings, package net.minecraft.world.entity.projectile — раньше
 * в Yarn это был net.minecraft.entity.projectile.ProjectileEntity).
 *
 * У Projectile есть публичный getOwner(), но он возвращает Entity и требует,
 * чтобы владелец был реально загружен в мире — а нам нужен сырой UUID даже
 * если игрок вне зоны прогрузки/офлайн. Поэтому тянем поле напрямую.
 *
 * Если при сборке ругается, что поля "ownerUUID" не существует — открой в
 * IDE (Ctrl+клик) класс Projectile из скачанного minecraft-merged jar
 * (Loom положит его через официальные мэппинги) и посмотри актуальное имя.
 */
@Mixin(Projectile.class)
public interface ProjectileOwnerAccessor {

    @Accessor("ownerUUID")
    UUID pearlowner$getOwnerUuid();
}
