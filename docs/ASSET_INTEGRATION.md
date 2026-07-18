# Asset Integration Guide

## 1. Using the exact logo artwork

Android **vector drawables cannot embed text**, so the app ships a branded shield **emblem**
(`res/drawable/ic_logo_emblem.xml`) plus the `ADAS BOUNTY RUN` wordmark composed from styled
views. To use the **original raster logo** instead:

1. Save the supplied image as:
   ```
   app/src/main/res/drawable-nodpi/logo.png
   ```
2. **Splash** — in `res/layout/activity_splash.xml`, point the emblem at the raster and hide
   the text views (the PNG already contains the wordmark and tagline):
   ```xml
   <ImageView android:id="@+id/logoEmblem"
       android:layout_width="360dp" android:layout_height="360dp"
       android:src="@drawable/logo" android:adjustViewBounds="true" />
   <!-- set android:visibility="gone" on wordAdas / wordBounty / wordRun / tagline -->
   ```
   `SplashActivity` animates whatever `logoEmblem` points at, so the loading animation reuses
   the same image automatically (spec requirement).
3. **Menu header** — do the same for the `ImageView` in `res/layout/activity_menu.xml`.
4. **Launcher icon** — generate an adaptive icon from the artwork with Android Studio
   *(New ▸ Image Asset ▸ Launcher Icons)*; it overwrites `mipmap-anydpi-v26/ic_launcher*.xml`
   and the density buckets. Keep a dark background matching `@color/ic_launcher_background`.

> Keep a copy of `ic_logo_emblem.xml` as a lightweight fallback for adaptive-icon foregrounds.

## 2. Adding 3D / sprite assets

The MVP renderer draws vehicles, people, animals and hazards as shaped primitives in
`engine/Renderer.kt`. To use bitmaps:

1. Add PNGs under `res/drawable-nodpi/` (e.g. `car_sedan.png`, `pedestrian.png`).
2. Decode once into `Bitmap`s in `Renderer` (cache them; never decode per frame).
3. Replace the relevant `draw*` method body with `canvas.drawBitmap(bitmap, srcRect, dstRect, paint)`
   using the perspective `scale` already computed for each entity.

Scale sprites by the projected `scale = GameGeometry.scaleAt(worldZ)` so they respect the
pseudo-3D perspective.

## 3. Audio (spec §25)

Voice alerts already play through Android **TextToSpeech** (`audio/VoiceManager`). To add
engine, siren, horn and ambience clips:

1. Drop `.ogg`/`.wav` files in `res/raw/` (e.g. `engine_loop.ogg`, `siren.ogg`).
2. Create an `AudioManager` wrapper around `SoundPool` (short SFX) and `MediaPlayer`
   (looping engine/ambience). Modulate engine pitch by `player.speedKmh`.
3. Trigger SFX from the same points that emit `AdasEvent`s and from `CollisionManager`.

## 4. Country-specific art

`CountryProfile.vehicleMix` and `commonHazards` already drive which road users spawn. When you
add country sprites (auto-rickshaws, tuk-tuks, police skins), key the bitmap lookup in
`Renderer`/`Spawner` on `world.setup.country.code` and the entity's `label`.

## 5. Where each asset is referenced

| Asset | File |
|-------|------|
| Splash / menu emblem | `res/layout/activity_splash.xml`, `res/layout/activity_menu.xml` |
| Launcher icon | `res/mipmap-anydpi-v26/ic_launcher*.xml`, `res/drawable/ic_launcher_foreground.xml` |
| Brand colours | `res/values/colors.xml` |
| HUD / button styling | `res/drawable/*.xml`, `engine/Renderer.kt` |
| Voice lines | `audio/VoiceManager.kt`, `res/values/strings.xml` (`va_*`) |
