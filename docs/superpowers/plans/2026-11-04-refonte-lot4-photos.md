# Refonte lot 4 — Photos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One photo per Aquarium, taken with the camera or chosen from the gallery, cropped square, shown on the home, in the Aquarium lists and in the « Mes bacs » widget. It follows the account everywhere: synced through Supabase Storage, included in the portable export, and deleted with the account.

**Architecture:**
- **Bytes live in files, not in Room.**
  - A photo is a JPEG file named by a photo id: `<filesDir>/aquarium-photos/<photoId>.jpg`, behind `AquariumPhotoStore` (`:shared`).
  - The Aquarium row only carries `photoId`, so every existing flow that reads Aquariums (sync, `observeChanges`, widgets) sees a photo change as an ordinary Aquarium change.
  - A new photo always gets a new id. So a cached file can never be stale.
- **Photo sync runs beside entity sync, in `SyncCoordinator`.**
  - A local change is marked in a small table, `aquarium_photo_syncs`.
  - The coordinator first uploads (or deletes) the remote object at `aquarium-photos/<accountId>/<aquariumId>.jpg`. It then enqueues an Aquarium upsert, so `photo_id` only reaches the server once the object exists.
  - Before committing a pulled snapshot, it downloads the photos it does not have yet.
- **The image pipeline lives in `:ui` commonMain**, with a single `expect` for JPEG encoding:
  1. FileKit picks (or, on Android, shoots) the image.
  2. `FileKit.compressImage` normalizes it: EXIF rotation applied on Android, longest side 2 400 px.
  3. A crop screen chooses the square.
  4. The square is drawn into a ≤ 1 200 px `ImageBitmap` and encoded as JPEG.

**Tech Stack:**
- Kotlin 2.4.20, Compose Multiplatform 1.12.2, Room 3.
- FileKit `io.github.vinceglb:filekit-dialogs-compose:0.17.0`.
- Supabase Storage REST.
- Glance 1.2.0, already present.

**Spec:** `docs/superpowers/specs/2026-10-30-refonte-aquagenda-design.md`:
- §4.6 Photos;
- §4.2 (« en haut à droite, une vignette avec la photo du bac … qui l'ouvre en plein écran », « la liste des bacs avec leurs photos »);
- §3 (« La synchronisation et l'export existants doivent couvrir … la photo », « la suppression de compte efface aussi … les photos dans le stockage », « Fichiers et photos : bibliothèque FileKit »);
- §4.7 (the photo is free);
- §6 (export format left to the builder);
- §7 (« La photo qui “suit le compte” fonctionne donc en gratuit »).

Mockup: `docs/design/20-refonte-carnet/03-accueil-scene.html`:
- `.cam`: a round, translucent badge at the scene's top right;
- `.thumb`: 44 px squares with 12 px corners in lists.

## Global Constraints

- **One photo per Aquarium.** There is no gallery and no history. Replacing a photo deletes the old one, locally and remotely.
- **Size.** The stored photo is square, at most 1 200 × 1 200 px, JPEG.
  - It is encoded at quality 80. If the result is over 300 000 bytes, it is re-encoded at 70, then at 60.
  - A smaller source is never upscaled.
- **Free.** Photos need no Pro.
  - The account and sync are open to free accounts (§7), so a free account's photo syncs too.
  - The « Mes bacs » widget stays Pro, as in lot 3.
- **Offline first.** Taking, changing and removing a photo all work without network. Sync catches up later.
  - A photo that fails to download never fails the sync: the place shows the no-photo look until a later sync brings it.
- **Privacy.**
  - The bucket is private.
  - An account can only read and write objects under its own `<accountId>/` folder.
  - Account deletion removes the folder.
  - Removing the local copy (`removeAccountAndLocalCopy`) deletes the local photo files.
  - No new telemetry event or value.
- **French copy:** French uses « vous ». Use the terms of `docs/localization-glossary.md` (Aquarium).
- **Localization.**
  - Every new key exists in the 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`) under `ui/src/commonMain/composeResources/`. The widget has no new text.
  - `LocalizationContractTest` stays green.
- **Accessibility.**
  - Touch targets are ≥ 48 dp. The vignette is drawn at 44 dp inside a 48 dp target.
  - The home vignette has a TalkBack label. List thumbnails are decorative, because the row already reads the name.
  - The crop screen can be confirmed without any gesture: the default crop is the centred square.
- **Reduced motion.** The no-photo mini-scene is a still frame (`TankScene(…, animate = false)`).
- **minSdk goes from 23 to 24**, in `androidApp` and `ui`. FileKit 0.17.0 requires 24.
- **CI must stay green:**
  - `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest :androidApp:testReleaseUnitTest :androidApp:lintDebug :androidApp:lintRelease :androidApp:assembleRelease`;
  - `python3 tools/ci/backend.py prepare && python3 tools/ci/backend.py verify` (pgTAP and HTTP integration).

## Decisions taken in this plan (Claude's calls, to check)

- **minSdk 24.**
  - FileKit 0.17.0 declares minSdk 24. Android 6 devices (API 23) stop receiving updates; they keep the installed version.
  - The alternative would be an older FileKit or a hand-written picker. Both go against the « FileKit » choice.
- **How a photo is added.**
  - Without a photo, the vignette is a still mini-scene with the mockup's camera badge. Tapping it opens a sheet: « Prendre une photo » (Android only) / « Choisir dans la galerie ».
  - With a photo, tapping the vignette opens it full screen, with « Changer la photo » and « Retirer la photo ». Removing asks for confirmation.
- **No CAMERA permission.**
  - FileKit's camera launcher uses the system camera app (`ACTION_IMAGE_CAPTURE`), which needs no permission as long as the manifest does not declare `android.permission.CAMERA`.
  - Lint guards this: no `CAMERA` permission in the merged manifest.
- **Export format.** Each `PortableAquarium` gains `photoJpegBase64: String? = null`, and `formatVersion` stays `1`.
  - Older files have no key and import without a photo.
  - The file grows by about 270 KB per photo.
- **The remote object path is fixed per Aquarium** (`<accountId>/<aquariumId>.jpg`), and a new upload overwrites it.
  - Two devices replacing the same photo at once settle on the last upload. A device that pulls the older `photo_id` may cache the newer bytes under the old id until the next pull brings the new id.
  - This beats versioned paths, which leave orphan objects behind.
- **A local photo change waiting for upload wins over the server.**
  - While `aquarium_photo_syncs` has a row for an Aquarium, applying a remote copy or snapshot keeps the local `photoId`.
  - This is what keeps a photo taken before sign-in. The initial account copy does not carry it, and the next snapshot would otherwise erase it.
- **`updateAquarium` never changes the photo.** It keeps the stored `photoId`, whatever the caller passes. Only `setAquariumPhoto` changes it, so an editor holding a stale Aquarium cannot revert a photo.
- **The photo id is generated by the repository's `generateId`** (a UUID in the app). The server column is `uuid`.

## Review Focus

1. **A portrait photo from the camera comes out sideways.** The source carries an EXIF rotation, and a naive decode ignores it.
   - Expected: the crop screen and the stored photo are upright.
   - Test: an Android instrumented test with a JPEG tagged orientation 6 (Task 6).
2. **A photo added before signing in disappears at sign-in.**
   - Expected: the photo is kept, then uploaded.
   - Test: `applyRemoteCopy` and `applyRemoteSnapshot` keep a pending local `photoId` (Task 2).
3. **A transparent PNG or a tiny image is picked.**
   - Expected: transparent areas become white, not black. A 300 px image stays 300 px and is not upscaled.
   - Test: in Task 6.
4. **A network failure halfway through a photo download.**
   - Expected: the sync still succeeds, the Aquarium shows the no-photo look, and the next sync retries.
   - Test: in Task 2.
5. **A hand-edited export with a broken or huge photo.**
   - Expected: the import is rejected with a clear problem, and nothing is replaced.
   - Test: in Task 5.

---

### Task 1: Photo storage and the Aquarium's `photoId` (Room v16)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/AquariumPhotos.kt`
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/Aquarium.kt`, `AquaLogDatabase.kt` (entity, new table, `MIGRATION_15_16`, version 16), `AquariumRepository.kt`
- Create: `shared/schemas/com.maximerollin.aqualog.shared.AquaLogDatabase/16.json` (exported by the build)
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/AquariumPhotoRepositoryTest.kt`, `HistoricalMigrationQualificationTest.kt`

**Interfaces:**
- Produces:
  - `Aquarium.photoId: String? = null` (last constructor parameter).
  - `interface AquariumPhotoStore` with these members:
    - `suspend fun write(photoId: String, jpeg: ByteArray)`
    - `suspend fun read(photoId: String): ByteArray?`
    - `suspend fun delete(photoId: String)`
    - `suspend fun retainOnly(photoIds: Set<String>)`
    - `suspend fun clear()`
  - `class DirectoryAquariumPhotoStore(directory: java.io.File) : AquariumPhotoStore`:
    - files are named `<photoId>.jpg`;
    - `write` goes through a temp file and a rename;
    - all IO runs on `Dispatchers.IO`.
  - New `AquariumRepository` members, with defaults so fakes compile:
    - `suspend fun setAquariumPhoto(aquariumId: String, jpeg: ByteArray?): Aquarium = error("unsupported")`
    - `suspend fun readAquariumPhoto(photoId: String): ByteArray? = null`
  - `RoomAquariumRepository(…, photoStore: AquariumPhotoStore? = null)`, as the last constructor parameter.
  - Room entity `AquariumPhotoSyncEntity(@PrimaryKey aquariumId: String)`, table `aquarium_photo_syncs`.

- [ ] **Step 1: Write the failing tests** in `AquariumPhotoRepositoryTest`. Use a temp-dir `DirectoryAquariumPhotoStore` and a JVM Room database, as `AquariumRepositoryContractTest` does.
  - `setting a photo stores the file and the id`:
    - `setAquariumPhoto(id, bytes)` returns an Aquarium whose `photoId != null`;
    - `readAquariumPhoto(photoId)` returns `bytes`;
    - `loadSetup(id).aquarium.photoId == photoId`;
    - one `AQUARIUM` upsert sync operation is pending;
    - a row exists in `aquarium_photo_syncs`.
  - `replacing a photo deletes the old file`: after a second `setAquariumPhoto`, `readAquariumPhoto(old) == null` and the id is new.
  - `removing a photo`:
    - `setAquariumPhoto(id, null)` sets `photoId = null` and deletes the file;
    - the `aquarium_photo_syncs` row remains, because the remote object must be deleted.
  - `updateAquarium keeps the stored photo`:
    - after a photo is set, `updateAquarium(staleAquariumWithPhotoIdNull.copy(name = "Nano"))` returns `photoId` equal to the stored id;
    - the name is « Nano ».
  - `removing the local copy deletes photo files`: after `removeAccountAndLocalCopy()`, `readAquariumPhoto(photoId) == null`.
  - `retainOnly keeps the listed files`: write `a` and `b`, `retainOnly(setOf("a"))`, then `read("b") == null`.
- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*AquariumPhotoRepositoryTest*'`. Expected: compilation fails, `setAquariumPhoto` is not defined.
- [ ] **Step 3: Implement.**
  - Entity column `photoId: String? = null`.
  - Migration: `ALTER TABLE aquariums ADD COLUMN photoId TEXT` and `CREATE TABLE IF NOT EXISTS aquarium_photo_syncs (aquariumId TEXT NOT NULL, PRIMARY KEY(aquariumId))`.
  - Map `photoId` in `toDomain` and `toEntity`.
  - `setAquariumPhoto`, in this order:
    1. generate an id and write the file;
    2. in one DAO transaction: update the row, upsert the `aquarium_photo_syncs` row, insert the sync operation;
    3. delete the old file;
    4. call `onSyncNeeded()`.
  - If step 2 throws, delete the new file. Either way, a `null` photo store makes `setAquariumPhoto` throw `IllegalStateException`.
- [ ] **Step 4: Add the migration case** to `HistoricalMigrationQualificationTest`, following its existing 14→15 pattern:
  - after the migration, a v15 Aquarium row reads `photoId == null`;
  - `aquarium_photo_syncs` exists and is empty.
- [ ] **Step 5: Run** `./gradlew :shared:jvmTest`. Expected: PASS, and `16.json` is generated. Commit it.
- [ ] **Step 6: Commit** `feat(photo): an Aquarium has one photo, stored as a file`.

### Task 2: Photo sync in `SyncCoordinator`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/Sync.kt`, `AquariumRepository.kt`, `AquaLogDatabase.kt` (the `SyncDao` remote-apply functions)
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/SyncCoordinatorTest.kt`, `SyncRecoveryTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `data class PendingPhotoSync(val aquariumId: String, val photoId: String?)`.
  - `SyncLocalDataSource` gains these members, with no-op defaults:
    - `suspend fun pendingPhotoSyncs(): List<PendingPhotoSync> = emptyList()`
    - `suspend fun markPhotoSynced(aquariumId: String, photoId: String?) {}`
    - `suspend fun aquariumPhotoIds(): Set<String> = emptySet()`
  - `SyncBackend` gains these members, with defaults:
    - `suspend fun uploadAquariumPhoto(session: SyncSession, aquariumId: String, jpeg: ByteArray) {}`
    - `suspend fun deleteAquariumPhoto(session: SyncSession, aquariumId: String) {}`
    - `suspend fun downloadAquariumPhoto(session: SyncSession, aquariumId: String): ByteArray? = null`
  - `SyncCoordinator(…, photoStore: AquariumPhotoStore? = null)`, as the last parameter.

- [ ] **Step 1: Write the failing tests** in `SyncCoordinatorTest`, with the existing fakes extended.
  - `pending photo is uploaded before the batch is pushed`:
    - with a pending `PendingPhotoSync("aq", "p1")` and `p1` in the store, the backend records `upload(aq, bytes)` before any `push`;
    - `markPhotoSynced("aq", "p1")` is called.
  - `removed photo deletes the remote object`: with a pending `PendingPhotoSync("aq", null)`, the backend records `delete(aq)`.
  - `missing photos are downloaded before the snapshot is committed`:
    - the snapshot has an Aquarium with `photoId = "p2"` and the store does not have it;
    - `download(aq)` is called, and `store.read("p2")` is non-null when `commitRemoteSnapshot` runs.
  - `a failed download does not fail the sync`:
    - `downloadAquariumPhoto` throws `SyncNetworkException`;
    - the result is `SyncRunResult.Synchronized`, and the snapshot is committed.
  - `photos no Aquarium references are removed after commit`: `store.retainOnly(local.aquariumPhotoIds())` is called after the commit.
  - `an upload failure retries like a push failure`: `uploadAquariumPhoto` throws `SyncNetworkException`, and the result is `Retry`.
- [ ] **Step 2: Write the failing Room tests** in `SyncRecoveryTest`, which already drives `RoomAquariumRepository` as `SyncLocalDataSource`.
  - `a pending local photo survives a remote copy`:
    - after `setAquariumPhoto`, `commitRemoteCopy` with the same Aquarium and `photoId = null` leaves the local `photoId` unchanged.
  - `a pending local photo survives a remote snapshot`: the same check, with `commitRemoteSnapshot` and a newer server version.
  - `markPhotoSynced clears the mark and enqueues an Aquarium upsert`:
    - the row is gone, and one new `AQUARIUM` upsert is pending;
    - when the `photoId` has changed since, the row stays.
  - `pendingPhotoSyncs lists the Aquarium with its current photoId`.
- [ ] **Step 3: Run** `./gradlew :shared:jvmTest --tests '*SyncCoordinatorTest*' --tests '*SyncRecoveryTest*'`. Expected: FAIL.
- [ ] **Step 4: Implement.**
  - **Coordinator**, inside the existing `try` of the loop's first iteration, before `push`: for each pending photo sync:
    - with a `photoId`, read the bytes and upload them;
    - if the file is missing, call `markPhotoSynced` without uploading;
    - with a `null` `photoId`, delete the remote object;
    - then call `markPhotoSynced`.
  - Between `pullVersioned` and `commitRemoteSnapshot`, download the snapshot Aquariums' missing photos:
    - catch `SyncNetworkException` and `SyncServerException` per photo, and skip that photo;
    - let `CloudAuthenticationExpiredException` and cancellation through.
  - After the commit, call `retainOnly`.
  - **DAO:** before `upsertAquariums` in `applyRemoteCopy` and `applyRemoteSnapshot`, replace each incoming `photoId` with the local one for every Aquarium id that has an `aquarium_photo_syncs` row.
- [ ] **Step 5: Run** `./gradlew :shared:jvmTest`. Expected: PASS.
- [ ] **Step 6: Commit** `feat(photo): photos sync beside the Aquarium`.

### Task 3: Backend — `photo_id`, the private bucket, deletion

**Files:**
- Create: `supabase/migrations/20261104100000_aquarium_photos.sql`, `supabase/tests/database/aquarium_photos.test.sql`
- Modify: `supabase/config.toml` (`[storage] enabled = true`), `supabase/tests/integration/entity_sync_test.ts` (or create `supabase/tests/integration/aquarium_photo_test.ts` and add it to `tools/ci/backend.py verify`), `supabase/functions/delete-account/README.md`

**Interfaces:**
- Produces:
  - `public.aquariums.photo_id uuid` (nullable);
  - bucket `aquarium-photos`;
  - object path `<accountId>/<aquariumId>.jpg`.

- [ ] **Step 1: Write the failing pgTAP test** `aquarium_photos.test.sql`, modelled on `analysis_interval.test.sql`:
  - `has_column('public','aquariums','photo_id')`;
  - an aquarium upsert carrying `"photo_id":"<uuid>"` stores it;
  - an upsert without the key keeps it;
  - an explicit `null` clears it;
  - the bucket exists with `public = false`;
  - an authenticated user can insert into `storage.objects` under `<own uid>/x.jpg` and cannot under `<other uid>/x.jpg` (`throws_ok`).
- [ ] **Step 2: Run** `python3 tools/ci/backend.py prepare && python3 tools/ci/backend.py verify`. Expected: the new test FAILS.
- [ ] **Step 3: Write the migration.**
  1. Add the column.
  2. Rename the six-argument `apply_sync_operation` to `apply_sync_operation_without_aquarium_photo`, and wrap it exactly as `20261015100000_lifetime_pro_multi_aquarium.sql` does. On an `aquarium` upsert, set `photo_id` from the payload when the key is present.
  3. Repeat the revokes and grants of that migration.
  4. `insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values ('aquarium-photos', 'aquarium-photos', false, 1048576, array['image/jpeg'])`.
  5. Four policies on `storage.objects` (select, insert, update, delete) for `authenticated`, with `bucket_id = 'aquarium-photos' and (storage.foldername(name))[1] = (select auth.uid())::text`.
- [ ] **Step 4: Write the HTTP integration case.** Two signed-up accounts, A and B:
  - A `POST /storage/v1/object/aquarium-photos/<A>/<aq>.jpg` with `x-upsert: true` and `Content-Type: image/jpeg` returns 200;
  - A `GET /storage/v1/object/authenticated/aquarium-photos/<A>/<aq>.jpg` returns the same bytes;
  - B's GET of the same path returns 400 or 404;
  - B's POST under `<A>/` is refused.
- [ ] **Step 5: Document deletion.**
  - In the delete-account README, state that production must set `AQUALOG_ACCOUNT_STORAGE_BUCKETS=aquarium-photos`.
  - Add the variable to the deployment checklist wherever the README lists the function's secrets.
- [ ] **Step 6: Run** the backend `prepare` and `verify` again. Expected: PASS.
- [ ] **Step 7: Commit** `feat(photo): private bucket and photo_id on the server`.

### Task 4: Android sync adapter and wiring

**Files:**
- Modify: `androidApp/src/main/kotlin/com/maximerollin/aqualog/SyncInfrastructure.kt`, `AquaLogApplication.kt`, `ui/src/jvmMain/kotlin/com/maximerollin/aqualog/ui/main.kt`
- Test: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/SupabaseTwoDeviceSyncTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: `SupabaseSyncBackend` implements the three photo calls.
  - Upload: `POST /storage/v1/object/aquarium-photos/<accountId>/<aquariumId>.jpg`, with headers `x-upsert: true` and `Content-Type: image/jpeg`.
  - Delete: `DELETE /storage/v1/object/aquarium-photos/<accountId>/<aquariumId>.jpg`. A 400 or 404 counts as done.
  - Download: `GET /storage/v1/object/authenticated/aquarium-photos/<accountId>/<aquariumId>.jpg`. A 400 or 404 returns `null`.
  - Errors map like `request()` does: 401 becomes `CloudAuthenticationExpiredException`, other statuses `SyncServerException`, and `IOException` becomes `SyncNetworkException`.
  - `request()` gains a byte-body and byte-response variant. Keep one HTTP helper and do not add a client library.

- [ ] **Step 1: Write the failing instrumented test** `productionAdapter_syncsAnAquariumPhotoToASecondDevice`, next to the existing two-device test and with the same setup. Each repository gets its own temp-dir `DirectoryAquariumPhotoStore`, and the coordinator gets the store.
  - First device: `setAquariumPhoto(aq, jpegFixture)`, then `runOnce()`.
  - Second device: `runOnce()`. Then `second.readAquariumPhoto(second.loadSetup(aq)!!.aquarium.photoId!!)` equals `jpegFixture`.
  - First device: `setAquariumPhoto(aq, null)` and `runOnce()`. Then the second device's `runOnce()` leaves `photoId == null`, and the remote GET returns `null`.
  - `jpegFixture` is any valid JPEG: encode a 16×16 `Bitmap` with `compress(JPEG, 80)`.
- [ ] **Step 2: Run** `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.SupabaseTwoDeviceSyncTest`, with the local backend from Task 3 running. Expected: FAIL.
- [ ] **Step 3: Implement the adapter.**
  - `Aquarium.toRemoteJson` puts `photo_id` as `putNullable`, so the explicit `null` clears it on the server.
  - The pull reads `value.nullableString("photo_id")`.
- [ ] **Step 4: Wire the store.**
  - `AquaLogApplication` builds one `DirectoryAquariumPhotoStore(File(filesDir, "aquarium-photos"))` and passes it to `RoomAquariumRepository` and to `SyncCoordinator`.
  - The desktop harness `main.kt` passes a store under its existing data directory.
- [ ] **Step 5: Run** the instrumented test again, then the full CI command. Expected: PASS.
- [ ] **Step 6: Commit** `feat(photo): Supabase Storage adapter`.

### Task 5: Photos in the portable export

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/PortableData.kt`, and the `PortableDataService` construction sites (grep `PortableDataService(`)
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/PortableDataServiceTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `PortableAquarium.photoJpegBase64: String? = null`;
  - `PortableDataService(database, photoStore: AquariumPhotoStore? = null)`;
  - `PortableImportProblem.INVALID_PHOTO("An Aquarium photo in the selected file is not a readable JPEG.")`.

- [ ] **Step 1: Write the failing tests.**
  - `export then import restores the photo`:
    - export a database whose Aquarium has a photo;
    - import it into a fresh database and store;
    - the Aquarium has a non-null `photoId`, and `readAquariumPhoto` returns the original bytes.
  - `a file without photoJpegBase64 imports without a photo`: use a v1 document from before this lot. A fixture string with no key is enough.
  - `a broken photo rejects the import and changes nothing`:
    - the cases are a non-base64 string, bytes not starting with `FF D8 FF`, and decoded bytes over 2 000 000;
    - each gives `Rejected(INVALID_DATA, INVALID_PHOTO)`;
    - the existing local Aquarium and its photo are untouched.
- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*PortableDataServiceTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - **Export:** read each photo with `kotlin.io.encoding.Base64.Default`.
  - **Import:**
    1. Validate in `validationProblem()`.
    2. Write each photo file under a newly generated id. Use `UUID.randomUUID()`, as the service has no id generator.
    3. Put those ids on the entities and `replaceAll`.
    4. On success, call `retainOnly` with the new ids. On failure or `ACCOUNT_LINKED`, delete the files just written.
    5. Imported photos get an `aquarium_photo_syncs` row each, inserted by `PortableDataDao.replaceAll` in the same transaction, so they upload after a later sign-in.
- [ ] **Step 4: Run** `./gradlew :shared:jvmTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(photo): the portable export carries the photos`.

### Task 6: Image pipeline and crop screen (`:ui`)

**Files:**
- Modify: `gradle/libs.versions.toml` (`filekit = "0.17.0"`, `filekit-dialogs-compose`), `ui/build.gradle.kts` (commonMain dependency; `minSdk = 24`), `androidApp/build.gradle.kts` (`minSdk = 24`)
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/photo/PhotoPipeline.kt`, `photo/PhotoCropScreen.kt`, `ui/src/androidMain/kotlin/com/maximerollin/aqualog/photo/JpegEncoder.android.kt`, `ui/src/jvmMain/kotlin/com/maximerollin/aqualog/photo/JpegEncoder.jvm.kt`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/photo/PhotoPipelineTest.kt`, `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/PhotoOrientationTest.kt`

**Interfaces:**
- Produces:
  - `data class SquareCrop(val left: Float, val top: Float, val side: Float)`: fractions of the source's width, height and shorter side.
  - `fun centredCrop(width: Int, height: Int): SquareCrop`.
  - `fun cropFor(imageWidth: Int, imageHeight: Int, viewport: Float, scale: Float, offset: Offset): SquareCrop`:
    - the square the viewport shows when the image is scaled to cover the viewport, times `scale` (1–4), and panned by `offset`;
    - clamped so the square stays inside the image.
  - `suspend fun normalizePhoto(source: ByteArray): ImageBitmap?`:
    - `FileKit.compressImage(bytes = source, imageFormat = ImageFormat.JPEG, quality = 90, maxWidth = 2400, maxHeight = 2400)`, then `decodeToImageBitmap()`;
    - `null` when undecodable.
  - `suspend fun encodeAquariumPhoto(image: ImageBitmap, crop: SquareCrop): ByteArray`:
    1. draw the crop onto a white `ImageBitmap` of side `min(1200, cropPixels)`;
    2. encode with `expect fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray`;
    3. if the result is over 300 000 bytes, re-encode at 70, then at 60.
  - `@Composable fun PhotoCropScreen(image: ImageBitmap, onConfirm: (SquareCrop) -> Unit, onCancel: () -> Unit)`:
    - a full-screen dialog with a square viewport;
    - `Modifier.transformable` pan and zoom;
    - buttons « Annuler » and « Utiliser cette photo », each ≥ 48 dp;
    - title « Recadrer la photo », marked as a heading.

- [ ] **Step 1: Write the failing JVM tests** in `PhotoPipelineTest`.
  - `centredCrop of a landscape image`: `centredCrop(400, 200) == SquareCrop(.25f, 0f, 1f)`.
  - `cropFor clamps the pan`:
    - a huge `offset` gives a square touching the image edge, and never `left + side·short/width > 1`;
    - at `scale = 2` the side is `.5f`.
  - `a large photo becomes 1200 px and under 300 KB`:
    - encode a 3000×2000 noisy JPEG fixture, made with `java.awt.image.BufferedImage` and random pixels, then pass it through `normalizePhoto` and `encodeAquariumPhoto`;
    - the result decodes to 1200×1200 and its size is ≤ 300 000 bytes.
  - `a small image is not upscaled`: a 300×300 source gives 300×300.
  - `transparent pixels become white`: a fully transparent 64×64 PNG gives a JPEG whose centre pixel is within 8 of `0xFFFFFF` on each channel.
- [ ] **Step 2: Write the failing instrumented test** `PhotoOrientationTest.normalizePhoto_appliesExifRotation`:
  1. compress a 40×20 `Bitmap` to JPEG;
  2. set `ExifInterface.TAG_ORIENTATION` to `ORIENTATION_ROTATE_90` with `androidx.exifinterface`, from the version FileKit already pulls in (check with `./gradlew :androidApp:dependencies`), as an `androidTestImplementation` only;
  3. `normalizePhoto` gives width 20 and height 40.
- [ ] **Step 3: Run** `./gradlew :ui:jvmTest --tests '*PhotoPipelineTest*'`. Expected: FAIL.
- [ ] **Step 4: Implement.**
  - **Android `encodeJpeg`:** `image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, stream)`.
  - **JVM `encodeJpeg`:** `org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)!!.bytes`.
  - Add the strings to the 6 catalogs:
    - `photo_crop_title` « Recadrer la photo »;
    - `photo_crop_confirm` « Utiliser cette photo »;
    - `photo_crop_cancel` « Annuler » (reuse the existing cancel key if one exists).
- [ ] **Step 5: Run** the JVM tests, the instrumented test, and the full CI command. Expected: PASS.
  - `lintRelease` must not report a `CAMERA` permission. Add `tools:node="remove"` for `android.permission.CAMERA` in `androidApp/src/main/AndroidManifest.xml`, so that no library brings it back.
- [ ] **Step 6: Commit** `feat(photo): pick, normalize, crop and encode a photo`.

### Task 7: Add, view, change and remove the photo from the home

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/photo/AquariumThumbnail.kt`, `photo/PhotoSheets.kt`, `photo/CameraLauncher.kt` (`expect`) with `ui/src/androidMain/…/photo/CameraLauncher.android.kt` and `ui/src/jvmMain/…/photo/CameraLauncher.jvm.kt`
- Modify: `HomeViewModel.kt`, `home/TankHome.kt` (`SceneHeader`), the 6 string catalogs
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/HomeViewModelPhotoTest.kt`, plus a Compose UI test in `ui/src/jvmTest` following the existing home UI tests

**Interfaces:**
- Consumes: Tasks 1 and 6.
- Produces:
  - `HomeViewModel` members:
    - `HomeUiState.photo: PhotoUiState = PhotoUiState.None`;
    - `fun openPhoto()`: tapping the vignette. It opens the viewer when the Aquarium has a photo, otherwise the source sheet.
    - `fun choosePhotoSource()`: « Changer la photo ».
    - `fun onPhotoPicked(bytes: ByteArray?)`;
    - `fun confirmCrop(crop: SquareCrop)`;
    - `fun cancelPhoto()`;
    - `fun askRemovePhoto()`;
    - `fun removePhoto()`;
    - `suspend fun aquariumPhoto(photoId: String): ImageBitmap?`: decoded once and cached in memory by id; the cache keeps only the ids of current Aquariums.
  - `sealed interface PhotoUiState { None; SourceSheet; Processing; Crop(image: ImageBitmap); Viewer(photoId: String); ConfirmRemove; Unreadable }`. Every state but `None` carries the target `aquariumId`, which is the Aquarium shown when `openPhoto` was called.
  - `@Composable fun AquariumThumbnail(aquarium: Aquarium, size: Dp, loadPhoto: suspend (String) -> ImageBitmap?, modifier: Modifier = Modifier)`:
    - the photo with 12 dp rounded corners;
    - otherwise `TankScene(aquarium, Modifier.size(size), animate = false)` with the same clip.
  - `@Composable expect fun rememberCameraLauncher(onResult: (ByteArray?) -> Unit): (() -> Unit)?`:
    - Android: `rememberCameraPickerLauncher`, then `readBytes()`;
    - JVM: `null`, so the sheet hides « Prendre une photo ».

- [ ] **Step 1: Write the failing VM tests** in `HomeViewModelPhotoTest`, with a fake repository that records `setAquariumPhoto`.
  - `openPhoto without a photo shows the source sheet`.
  - `openPhoto with a photo shows the viewer`.
  - `an unreadable picked file shows Unreadable and saves nothing`.
  - `a cancelled pick returns to None`: `onPhotoPicked(null)`.
  - `confirming the crop saves a JPEG for the Aquarium that was shown`:
    - the Aquarium is switched between `openPhoto` and `confirmCrop`;
    - the photo still goes to the first Aquarium;
    - the bytes start with `FF D8 FF`.
  - `removing asks first, then clears`: `askRemovePhoto()` gives `ConfirmRemove`, and `removePhoto()` calls `setAquariumPhoto(id, null)`.
- [ ] **Step 2: Run** `./gradlew :ui:jvmTest --tests '*HomeViewModelPhotoTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement the VM.**
  - Picked bytes go through `normalizePhoto` on `Dispatchers.Default`, and the state is `Processing` meanwhile.
  - `confirmCrop` calls `encodeAquariumPhoto` and then `setAquariumPhoto`.
  - A save failure shows the existing generic error.
- [ ] **Step 4: Implement the UI.**
  - **Vignette in `SceneHeader`:**
    - top-end, a 48 dp touch target holding a 44 dp `AquariumThumbnail` with a 1.5 dp white border;
    - without a photo, a 22 dp round translucent white badge sits at its bottom-end corner, holding the camera icon.
  - **Labels on the vignette's `onClickLabel` / `contentDescription`:**
    - `photo_open` « Voir la photo de %1$s »;
    - `photo_add` « Ajouter une photo de %1$s ».
  - **Source sheet:** `photo_take` « Prendre une photo » (only when the camera launcher is non-null) and `photo_choose` « Choisir dans la galerie »:
    - `rememberFilePickerLauncher(type = FileKitType.Image)` then `readBytes()`;
    - the result goes to `onPhotoPicked`.
  - **Viewer:**
    - a full-screen dialog;
    - the photo as `ContentScale.Fit`, with `contentDescription` `photo_of` « Photo de %1$s »;
    - `photo_change` « Changer la photo », `photo_remove` « Retirer la photo », and a close button with the existing close label.
  - **Removal confirmation:**
    - `photo_remove_title` « Retirer la photo ? »;
    - `photo_remove_text` « La photo sera supprimée de cet appareil et de votre compte. »;
    - confirm `photo_remove_confirm` « Retirer ».
  - **Unreadable file:** a snackbar with `photo_unreadable` « Cette image ne peut pas être lue. ».
  - Add every key to the 6 catalogs.
- [ ] **Step 5: Write a Compose UI test** on the JVM, in the style of the existing home UI tests, with a fake repository holding one photo:
  - the vignette with the label « Voir la photo de Salon » exists;
  - clicking it shows « Changer la photo » and « Retirer la photo ».
- [ ] **Step 6: Run** `./gradlew :ui:jvmTest`, then the full CI command. Expected: PASS.
- [ ] **Step 7: Commit** `feat(photo): add, view, change and remove the photo from the home`.

### Task 8: Thumbnails in the Aquarium lists and in « Mes bacs »

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/AquariumPickerSheet.kt`, `OnboardingUi.kt` (`MoreRow` gains `leading: (@Composable () -> Unit)? = null`; the More screen's Aquarium rows), `androidApp/src/main/kotlin/com/maximerollin/aqualog/widget/TanksWidget.kt`, `ui/src/commonMain/kotlin/com/maximerollin/aqualog/widget/WidgetText.kt` (`WidgetTankText.photoId: String?`)
- Test: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/widget/TanksWidgetTest.kt`, `ui/src/jvmTest/…/widget/WidgetTextTest.kt` (or the existing widget text test)

**Interfaces:**
- Consumes: `AquariumThumbnail`, `HomeViewModel.aquariumPhoto` (Task 7), and `AquariumRepository.readAquariumPhoto` (Task 1).
- Produces:
  - `AquariumPickerSheet(…, loadPhoto: suspend (String) -> ImageBitmap?)`;
  - `TanksContent(content: WidgetContent, photos: Map<String, Bitmap> = emptyMap())`.

- [ ] **Step 1: Write the failing tests.**
  - `widgetText carries the Aquarium photoId`, in the widget text test.
  - In `TanksWidgetTest`, with `runGlanceAppWidgetUnitTest` as in lot 3:
    - with a photo `Bitmap` for Aquarium A and none for B, row A has an `Image` node and row B has the existing `StatusDot` only;
    - the row's `contentDescription` is unchanged.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - **Picker sheet and More rows:** a 40 dp `AquariumThumbnail`, decorative (`clearAndSetSemantics {}`), before the name.
  - **« Mes bacs »:**
    - in `provideGlance`, read each listed Aquarium's photo through `readAquariumPhoto`;
    - decode it with `BitmapFactory` and `inSampleSize`, so the shorter side is about 96 px;
    - render a 36 dp `Image(ImageProvider(bitmap), contentDescription = null, contentScale = ContentScale.Crop)` with `cornerRadius(10.dp)`, before the dot.
  - A photo change already refreshes widgets through `observeChanges` (aquariums table).
- [ ] **Step 4: Run** the tests, then the full CI command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(photo): thumbnails in the Aquarium lists and in Mes bacs`.

---

## Manual pass (controller, after Task 8)

On the emulator, debug build, French, with the local backend running:
1. **No photo:** the vignette shows the mini-scene with its badge.
2. **Gallery:** « Choisir dans la galerie » with a portrait camera photo (push one with `adb push` to `/sdcard/Pictures` and run a media scan). The crop screen is upright. Confirm.
3. The vignette, the picker sheet, the More screen and « Mes bacs » (Pro faked via the cache, as in lot 3) all show the photo.
4. « Prendre une photo » opens the emulator camera, and the result reaches the crop screen.
5. **Two devices:** sign in on a second emulator user or a fresh install, and the photo arrives.
6. **Removal:** « Retirer la photo » brings the mini-scene back everywhere.
7. **Display:** German, dark mode, font scale 1.5. Nothing is clipped on the viewer or the sheets.
