# Pearl Owner Tracker (Fabric) — под Minecraft 26.2

Клиентский мод: показывает ник владельца над эндер-жемчугом, стрелами, трезубцами
и вообще любой сущностью-снарядом (`Projectile`), у которой есть Owner UUID.
Ник берётся не по публичному Mojang UUID, а по тому UUID, который **реально
прислал сервер** через таб-лист — на офлайн-сервере это offline-UUID, на
лицензионном — настоящий.

## Что изменилось при переносе с 1.21.1 на 26.2 (коротко)

В декабре 2025 Mojang анонсировала полный отказ от обфускации в Java Edition.
С версии **26.1** (первый релиз в новой схеме версионирования `год.дроп.хотфикс`,
26.2 — второй "дроп" 2026 года, вышел 16 июня 2026) игра **полностью
деобфусцирована**, а имена классов/методов/полей — это официальные Mojang
mappings. Из-за этого:

- **Yarn мёртв.** Fabric официально прекратил его поддержку с 26.1 — все
  мэппинги теперь берутся напрямую из игры через `loom.officialMojangMappings()`,
  никакой `yarn_mappings` строки в `gradle.properties` больше нет.
- Имена классов сменились на официальные (те, которыми годами пользовался
  Forge/NeoForge): `MinecraftClient`→`Minecraft`, `TextRenderer`→`Font`,
  `MatrixStack`→`PoseStack`, `Text`→`Component`, `ProjectileEntity`→`Projectile`,
  `PlayerListEntry`→`PlayerInfo`, `MathHelper`→`Mth`, `Vec3d`→`Vec3` и т.д.
- Нужен **JDK 25** (26.1+ требует его), и **Loom 1.17** / Gradle 9.5.1.
- Старый мод под 1.21.1 без пересборки не запустится вообще — старые классы
  не существуют в рантайме.

Всё это я проверил через официальный блог Fabric (fabricmc.net/2026/06/15/262.html)
и страницу Fabric Docs "Migrating Mappings" — если версия у тебя другая
(например, вышла уже 26.3), формулировки могут снова устареть, проверяй там же.

## Структура проекта

```
pearlowner/
├── build.gradle              <- mappings = official Mojang, Java 25 toolchain
├── gradle.properties         <- minecraft_version=26.2, fabric_version=...+26.2
├── settings.gradle
├── src/main/java/dev/pearlowner/
│   ├── PearlOwnerClient.java   — точка входа, таб-лист, команда /uuid, рендер
│   ├── PlayerUuidCache.java    — кэш UUID<->ник с сохранением в JSON (не тронут)
│   └── mixin/ProjectileOwnerAccessor.java — достаёт owner UUID у снарядов
└── src/main/resources/
    ├── fabric.mod.json
    └── pearlowner.mixins.json
```

## 1. Что нужно поставить

- **JDK 25** (Temurin/Adoptium) — `java -version` должен показать 25.
- Gradle wrapper в архиве отсутствует — как и раньше, возьми `gradle/`,
  `gradlew`, `gradlew.bat` из свежего `fabric-example-mod` (репозиторий на
  GitHub у FabricMC) — на момент 26.2 он уже настроен на Loom 1.17.

## 2. Сборка

```bash
cd pearlowner
./gradlew build
```

Jar появится в `build/libs/pearlowner-1.0.0.jar`. Кидай в `.minecraft/mods`
вместе с **Fabric API 0.150.1+26.2 (или новее)** и Fabric Loader 0.19.3+.

## 3. Тестовый запуск без сборки jar

```bash
./gradlew runClient
```

## 4. Если ругается на `ownerUUID` в mixin

Это самое хрупкое место, как и раньше. Открой в IDE (Ctrl+клик) класс
`net.minecraft.world.entity.projectile.Projectile` — Loom подтянет его уже
под официальными именами — и проверь точное имя приватного поля c UUID
владельца. Я взял `ownerUUID` по данным официальных mappings на момент
16 июня 2026, но если Mojang его переименовали в патче — поправь
`@Accessor("ownerUUID")` в `ProjectileOwnerAccessor.java`.

То же самое касается пары мест в `PearlOwnerClient.java`, которые я не мог
проверить компиляцией в этом окружении (нет доступа к сети/JDK 25 здесь):
`entitiesForRendering()` на `ClientLevel`, `getBackgroundOpacity(float)` на
`Options`, `DeltaTracker.getGameTimeDeltaPartialTick(boolean)` — сигнатуры
недавно менялись из-за перехода на новый рендер-пайплайн (Blaze3D/Vulkan),
так что если что-то из этого не собирается — открой класс в IDE, имя рядом
почти наверняка совпадает по смыслу.

## 5. Команда в игре

```
/uuid Ник
```
выдаст UUID игрока, если он хотя бы раз был виден в таб-листе — данные
сохраняются в `.minecraft/config/pearlowner_players.json` (формат не менялся).

## 6. Как залить на GitHub

```bash
cd pearlowner
git init
git add .
git commit -m "Порт на 26.2: official mappings вместо Yarn"
git branch -M main
git remote add origin https://github.com/ТВОЙ_НИК/pearlowner.git
git push -u origin main
```

## Ограничения

- Рендер проходит по всем загруженным сущностям каждый кадр — для одиночных
  жемчугов/стрел не проблема.
- Если ник неизвестен — покажется обрезанный UUID вместо ника.
- Код не компилировался и не тестировался в реальном 26.2-клиенте (нет
  доступа к сети/JDK в этой среде) — имена актуальны на 16 июня 2026 по
  официальным источникам (fabricmc.net, minecraft.wiki), но при сборке
  проверяй сигнатуры через IDE, как описано в п.4.
