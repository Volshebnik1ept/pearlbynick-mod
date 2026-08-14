package dev.pearlowner.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Достаёт приватное поле owner у Projectile.
 *
 * ВАЖНО: с ~1.21.5 (задолго до 26.x) Mojang заменил старое поле
 * "ownerUUID: UUID" на "owner: EntityReference<Entity>" — обёртку над
 * Either<UUID, Entity>, которая умеет отдавать сырой UUID даже когда
 * сущность-владелец не загружена в мире, через EntityReference#getUUID().
 * Публичный Projectile#getOwner() по-прежнему требует загруженную сущность,
 * поэтому нам всё ещё нужен доступ к сырому полю, но теперь через
 * EntityReference, а не напрямую к UUID.
 *
 * Если снова ругается "no candidates" — поле могло переименоваться ещё раз;
 * открой Projectile в IDE (Ctrl+клик, мэппинги теперь официальные Mojang и
 * встроены в сам jar) и найди поле типа EntityReference рядом с getOwner()/
 * setOwner().
 */
@Mixin(Projectile.class)
public interface ProjectileOwnerAccessor {

    @Accessor("owner")
    EntityReference<Entity> pearlowner$getOwnerRef();
}
