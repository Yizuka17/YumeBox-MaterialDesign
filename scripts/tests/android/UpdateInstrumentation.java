package com.github.yizuka17.yumebox.md3.validation;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone, debug-signed device tests. No fixture switches or exported test activities ship in the app. */
public final class UpdateInstrumentation extends Instrumentation {
    private ClassLoader loader;
    private Context target;
    private int code = 200;
    private String response = "";
    private long responseDelay;
    private boolean networkFailure;
    private String requestedUrl;
    private int passed;
    private final StringBuilder report = new StringBuilder();

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        target = getTargetContext(); loader = target.getClassLoader();
        SharedPreferences prefs = target.getSharedPreferences("release_updates", Context.MODE_PRIVATE);
        Map<String, ?> original = new HashMap<>(prefs.getAll());
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_OK;
        try {
            long installedCode = target.getPackageManager().getPackageInfo(target.getPackageName(), 0).getLongVersionCode();
            Object manager = manager(target, "0.6.5", installedCode);
            invoke(manager, "setIncludePrereleases", false);
            response = release("v0.6.6", false, false, "YumeBox-arm64-v8a.apk", null);
            Object candidate = suspend(manager, "checkForUpdate", true);
            check(candidate != null && requestedUrl.endsWith("/releases/latest"), "stable release endpoint and newer version");
            Object release = invoke(candidate, "getRelease");
            check("v0.6.6".equals(invoke(release, "getTag")), "release metadata is decoded in the real app");
            invoke(manager, "dismiss", true);
            suspend(manager, "checkForUpdate", false);
            check(value(invoke(manager, "getCandidate")) == null, "ignored release stays quiet on automatic checks");
            check(suspend(manager, "checkForUpdate", true) != null, "manual check bypasses ignored release");
            response = release("v0.6.5", false, false, "YumeBoxMD3-release.apk", null);
            check(suspend(manager, "checkForUpdate", true) == null, "same version does not prompt");
            response = release("v0.6.4", false, false, "YumeBoxMD3-release.apk", null);
            check(suspend(manager, "checkForUpdate", true) == null, "older release never downgrades");
            response = release("v0.7.0-alpha", true, false, "YumeBox-arm64-v8a.apk", null);
            check(suspend(manager, "checkForUpdate", true) == null, "stable channel excludes prereleases");
            invoke(manager, "setIncludePrereleases", true);
            response = "[" + release("v0.8.0", false, true, "YumeBox-arm64-v8a.apk", null) + "," + release("v0.7.0-alpha", true, false, "YumeBox-arm64-v8a.apk", null) + "]";
            candidate = suspend(manager, "checkForUpdate", true);
            check(candidate != null && requestedUrl.contains("releases?per_page="), "preview channel excludes drafts and accepts prereleases");
            response = "[" + release("Pre-release", true, false, "YumeBox-87654321-arm64-v8a.apk", null) + "]";
            check(suspend(manager, "checkForUpdate", true) != null, "rolling prerelease uses publication time");
            response = "[" + release("Pre-release", true, false, "YumeBox-12345678-arm64-v8a.apk", null) + "]";
            check(suspend(manager, "checkForUpdate", true) == null, "rolling release skips its own build hash");
            response = "not json";
            expectFailure(() -> suspend(manager, "checkForUpdate", true), "malformed API data is a recoverable failure");
            code = 403;
            expectFailure(() -> suspend(manager, "checkForUpdate", true), "GitHub rate limit is a recoverable failure", "GitHub");
            code = 200; networkFailure = true;
            expectFailure(() -> suspend(manager, "checkForUpdate", true), "network outage is a recoverable failure", "simulated offline");
            networkFailure = false; invoke(manager, "setIncludePrereleases", false);
            response = release("v0.6.6", false, false, "YumeBox-x86_64.apk", null);
            candidate = suspend(manager, "checkForUpdate", true);
            check(candidate != null && invoke(candidate, "getAsset") == null, "unsupported ABI falls back to release page");

            response = release("v0.6.6", false, false, "YumeBox-arm64-v8a.apk", "sha256:" + String.join("", Collections.nCopies(64, "0")));
            final Object checksumCandidate = suspend(manager, "checkForUpdate", true);
            response = "invalid-apk";
            expectFailure(() -> suspend(manager, "downloadAndInstall", checksumCandidate), "download rejects checksum mismatch", "SHA-256");
            check(!new File(target.getCacheDir(), "updates/release-9000000001.part").exists(), "failed download removes partial file");

            response = release("v0.6.6", false, false, "YumeBox-arm64-v8a.apk", null);
            final Object cancelCandidate = suspend(manager, "checkForUpdate", true);
            response = "invalid-apk"; responseDelay = 800L;
            ExecutorService executor = Executors.newSingleThreadExecutor();
            AtomicReference<Throwable> cancelError = new AtomicReference<>();
            Future<?> download = executor.submit(() -> {
                try { suspend(manager, "downloadAndInstall", cancelCandidate); throw new AssertionError("cancel succeeded unexpectedly"); }
                catch (Throwable expected) { if (expected instanceof AssertionError) throw (AssertionError) expected; cancelError.set(expected); }
            });
            long deadline = System.currentTimeMillis() + 5000;
            while (!Boolean.TRUE.equals(invoke(value(invoke(manager, "getProgress")), "getDownloading")) && System.currentTimeMillis() < deadline) Thread.sleep(10);
            invoke(manager, "cancelDownload");
            download.get(10, TimeUnit.SECONDS); executor.shutdown(); responseDelay = 0;
            check(cancelError.get() != null && String.valueOf(cancelError.get().getMessage()).contains("取消"), "cancel reports cancellation rather than another download error");
            check(!Boolean.TRUE.equals(invoke(value(invoke(manager, "getProgress")), "getDownloading")), "cancel clears downloading state");
            check(!new File(target.getCacheDir(), "updates/release-9000000001.part").exists(), "cancel removes partial file");

            File installed = new File(target.getApplicationInfo().sourceDir);
            expectFailure(() -> validate(manager, installed), "APK versionCode prevents downgrades and reinstalls");
            Object lowerVersionManager = manager(target, "0.0.1", 1L);
            validate(lowerVersionManager, installed);
            check(true, "real APK passes package and signing certificate validation");
            File wrongPackage = new File(target.getPackageManager().getApplicationInfo("com.android.settings", 0).sourceDir);
            expectFailure(() -> validate(lowerVersionManager, wrongPackage), "APK with another package is rejected");
            File wrongSigner = new File(target.getCacheDir(), "validation-wrong-signer.apk");
            check(wrongSigner.isFile(), "wrong-signature fixture is available");
            expectFailure(() -> validate(lowerVersionManager, wrongSigner), "APK signed by another certificate is rejected", "签名");
            RecordingContext recording = new RecordingContext(target);
            Object installerManager = manager(recording, "0.0.1", 1L);
            Method install = installerManager.getClass().getDeclaredMethod("openInstaller", File.class); install.setAccessible(true);
            try { install.invoke(installerManager, wrongSigner); } catch (InvocationTargetException expected) { /* permission settings path */ }
            check(recording.intent != null, "installer or install-permission intent is dispatched without performing an install");
            verifyLegacyMetadataMigration();
            verifyLegacyStatisticsMigration();
            verifySettingsBackup();
            verifyGeoValidation();
            verifyLegacyLogVisibility();
            result.putString("stream", report + "PASS: " + passed + " updater device checks\n");
        } catch (Throwable error) {
            resultCode = Activity.RESULT_CANCELED;
            result.putString("stream", report + "FAIL: " + error + "\n" + android.util.Log.getStackTraceString(error));
        } finally {
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : original.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof Boolean) editor.putBoolean(key, (Boolean)value);
                else if (value instanceof Long) editor.putLong(key, (Long)value);
                else if (value instanceof Integer) editor.putInt(key, (Integer)value);
                else if (value instanceof Float) editor.putFloat(key, (Float)value);
                else if (value instanceof String) editor.putString(key, (String)value);
            }
            editor.commit();
        }
        finish(resultCode, result);
    }

    private void verifyLegacyLogVisibility() throws Throwable {
        File directory = new File(target.getCacheDir(), "validation-logs-" + UUID.randomUUID());
        File logs = new File(directory, "logs"); check(logs.mkdirs(), "isolated log fixture directory created");
        java.nio.file.Files.write(new File(logs, "20260101_000000.log").toPath(), "[12:00:00] [Info] legacy\n".getBytes("UTF-8"));
        java.nio.file.Files.write(new File(logs, "log_123.log").toPath(), "[12:00:01] [Info] current\n".getBytes("UTF-8"));
        java.nio.file.Files.write(new File(logs, "unrelated.txt").toPath(), new byte[0]);
        android.app.Application application = new android.app.Application() {
            @Override public File getFilesDir() { return directory; }
        };
        Object gateway = loader.loadClass("com.github.yumelira.yumebox.service.LogRecordServiceGateway").getConstructor().newInstance();
        Object store = loader.loadClass("com.github.yumelira.yumebox.data.store.LogStore").getConstructors()[0].newInstance(application, gateway);
        List<?> entries = (List<?>)invoke(store, "listLogFiles");
        check(entries.size() == 2 && entries.stream().anyMatch(item -> {
            try { return invoke(item, "getName").equals("20260101_000000.log"); } catch (Exception error) { throw new RuntimeException(error); }
        }), "existing timestamp-named log recordings remain visible after upgrade");
    }

    private void verifyGeoValidation() throws Throwable {
        Object validator = loader.loadClass("com.github.yumelira.yumebox.runtime.service.core.GeoFileValidator").getField("INSTANCE").get(null);
        Class<?> type = loader.loadClass("com.github.yumelira.yumebox.core.model.GeoFileType");
        String[][] files = {{"GeoIP", "geoip.metadb"}, {"GeoSite", "geosite.dat"}, {"ASN", "ASN.mmdb"}};
        for (String[] file : files) {
            File source = new File(target.getFilesDir(), "mihomo/" + file[1]);
            Object result = invoke(validator, "validate", target, source, type.getField(file[0]).get(null));
            check(Boolean.TRUE.equals(invoke(result, "getValid")), "native validator accepts bundled " + file[1]);
        }
        File invalid = new File(target.getCacheDir(), "validation-invalid.mmdb");
        java.nio.file.Files.write(invalid.toPath(), new byte[100000]);
        Object result = invoke(validator, "validate", target, invalid, type.getField("ASN").get(null));
        check(Boolean.FALSE.equals(invoke(result, "getValid")), "native Geo validator rejects corrupt database contents");
        check(invalid.delete(), "Geo validation fixture removed");
    }

    private void verifySettingsBackup() throws Throwable {
        File directory = new File(target.getCacheDir(), "validation-backup-" + UUID.randomUUID());
        check(directory.mkdirs(), "isolated backup fixture directory created");
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return directory; }
        };
        Object provider = loader.loadClass("com.github.yumelira.yumebox.data.store.MMKVProvider").getConstructor().newInstance();
        Object[] stores = new Object[5];
        String[] names = {"AppSettingsStore", "FeatureStore", "NetworkSettingsStore", "ProfileLinksStore", "ProxyDisplaySettingsStore"};
        List<Object> fixtures = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Object mmkv = invoke(provider, "getMMKV", "validation-backup-" + UUID.randomUUID()); fixtures.add(mmkv);
            stores[i] = loader.loadClass("com.github.yumelira.yumebox.data.store." + names[i]).getConstructors()[0].newInstance(mmkv);
        }
        Object wallpaper = loader.loadClass("com.github.yumelira.yumebox.data.controller.AcgWallpaperStorage").getConstructor(Context.class).newInstance(isolated);
        Object bindings = loader.loadClass("com.github.yumelira.yumebox.data.store.ProfileBindingStore").getConstructor(Context.class).newInstance(isolated);
        Object overrides = loader.loadClass("com.github.yumelira.yumebox.data.store.OverrideConfigStore").getConstructors()[0].newInstance(isolated, bindings);
        Object controller = loader.loadClass("com.github.yumelira.yumebox.data.controller.UserSettingsBackupController").getConstructors()[0]
            .newInstance(stores[0], stores[1], stores[2], stores[3], stores[4], wallpaper, overrides);
        Object mode = invoke(stores[2], "getRunMode");
        String legacy = "{\"format\":\"YumeBoxUserSettingsBackup\",\"version\":3,\"createdAt\":1,\"stores\":{\"network\":{\"proxyMode\":\"Tun\"}}}";
        suspend(controller, "importFromJson", legacy);
        check(invoke(mode, "getValue").toString().equals("VpnService"), "legacy backup VPN mode is not mistaken for root Tun");
        suspend(controller, "importFromJson", legacy.replace("\"Tun\"", "\"RootTun\""));
        check(invoke(mode, "getValue").toString().equals("Tun"), "legacy root Tun backup keeps its root mode");
        String backup = (String)suspend(controller, "exportToJson");
        org.json.JSONObject parsed = new org.json.JSONObject(backup);
        check(parsed.getInt("version") == 4 && parsed.getJSONObject("stores").getJSONObject("network").has("runMode"), "settings export uses unambiguous version 4 run modes");
        suspend(controller, "importFromJson", backup);
        check(invoke(mode, "getValue").toString().equals("Tun"), "version 4 settings backup round trip preserves run mode");
        check(((String)suspend(controller, "exportToJson")).contains("superIslandEnabled"), "settings backup restores the HyperOS preference");
        expectFailure(() -> suspend(controller, "importFromJson", "not json"), "malformed settings backup is rejected");
        expectFailure(() -> suspend(controller, "importFromJson", backup.replace("\"version\": 4", "\"version\": 999")), "future backup versions are rejected", "Unsupported backup version");
        for (Object fixture : fixtures) { invoke(fixture, "clearAll"); invoke(fixture, "close"); }
    }

    private void verifyLegacyStatisticsMigration() throws Throwable {
        File directory = new File(target.getCacheDir(), "validation-stats-" + UUID.randomUUID());
        check(directory.mkdirs(), "isolated statistics fixture directory created");
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) { return new File(directory, name); }
        };
        Object provider = loader.loadClass("com.github.yumelira.yumebox.data.store.MMKVProvider").getConstructor().newInstance();
        Object mmkv = invoke(provider, "getMMKV", "validation-stats-" + UUID.randomUUID());
        long now = System.currentTimeMillis();
        String json = "{\"" + now + "\":{\"dateMillis\":" + now + ",\"appUsages\":{\"fixture.app\":{\"appKey\":\"fixture.app\",\"appName\":\"Fixture\",\"totalUpload\":12,\"totalDownload\":34}}}}";
        invoke(mmkv, "encode", "daily_app_summaries_v2", json);
        Object dao = loader.loadClass("com.github.yumelira.yumebox.data.store.room.TrafficDatabaseFactoryKt")
            .getMethod("createTrafficStatisticsDao", Context.class).invoke(null, isolated);
        Class<?> function = loader.loadClass("kotlin.jvm.functions.Function0");
        Object clock = Proxy.newProxyInstance(loader, new Class<?>[]{function}, (p, m, a) -> now);
        Class<?> store = loader.loadClass("com.github.yumelira.yumebox.data.store.TrafficStatisticsStore");
        Constructor<?> constructor = Arrays.stream(store.getConstructors()).filter(c -> c.getParameterCount() == 3).findFirst().get();
        Object statistics = constructor.newInstance(mmkv, dao, clock);
        Object today = loader.loadClass("com.github.yumelira.yumebox.data.model.StatisticsTimeRange").getField("TODAY").get(null);
        List<?> usages = (List<?>) suspend(statistics, "getAppUsagesSorted", today);
        check(usages.size() == 1 && ((Long)invoke(usages.get(0), "getTotalUpload")) == 12L && ((Long)invoke(usages.get(0), "getTotalDownload")) == 34L,
            "legacy traffic totals import into Room without losing counters");
        check(json.equals(invoke(mmkv, "decodeString", "daily_app_summaries_v2")), "legacy statistics source is retained");
        Object repeated = constructor.newInstance(mmkv, dao, clock);
        usages = (List<?>) suspend(repeated, "getAppUsagesSorted", today);
        check(usages.size() == 1 && ((Long)invoke(usages.get(0), "getTotalDownload")) == 34L, "statistics migration does not double count on restart");
        invoke(mmkv, "clearAll");
        invoke(mmkv, "close");
    }

    private void verifyLegacyMetadataMigration() throws Throwable {
        File files = new File(target.getCacheDir(), "validation-metadata-" + UUID.randomUUID());
        File dir = new File(files, "overrides");
        File configs = new File(dir, "configs");
        check(configs.mkdirs(), "isolated metadata fixture directory created");
        File legacy = new File(dir, "metadata.json");
        String source = "{\"configs\":{\"cfg-legacy\":{\"id\":\"cfg-legacy\",\"name\":\"Legacy\",\"createdAt\":1,\"updatedAt\":2}},\"profileChains\":{\"profile\":{\"profileId\":\"profile\",\"enabled\":false,\"overrideIds\":[\"preset-default\",\"cfg-legacy\"]}}}";
        java.nio.file.Files.write(legacy.toPath(), source.getBytes("UTF-8"));
        File oldConfig = new File(configs, "cfg-legacy.json");
        String content = "{\"dns\":{\"enable\":true},\"unknown-field\":\"preserved\"}";
        java.nio.file.Files.write(oldConfig.toPath(), content.getBytes("UTF-8"));
        Object metadata = loader.loadClass("com.github.yumelira.yumebox.data.store.OverrideMetadataIO").getField("INSTANCE").get(null);
        File yaml = new File(dir, "metadata.yaml");
        invoke(metadata, "load", yaml);
        check(yaml.isFile() && new File(configs, "cfg-legacy.yaml").isFile(), "legacy overrides migrate to YAML storage");
        String converted = new String(java.nio.file.Files.readAllBytes(yaml.toPath()), "UTF-8");
        check(converted.contains("builtin-official-mrs") && converted.contains("enabled: false"), "preset bindings and disabled state survive migration");
        check(new String(java.nio.file.Files.readAllBytes(new File(configs, "cfg-legacy.yaml").toPath()), "UTF-8").equals(content), "unknown configuration fields survive migration");
        check(new String(java.nio.file.Files.readAllBytes(legacy.toPath()), "UTF-8").equals(source) && oldConfig.isFile(), "legacy source files remain unchanged");
        invoke(metadata, "load", yaml);
        check(new String(java.nio.file.Files.readAllBytes(yaml.toPath()), "UTF-8").equals(converted), "legacy migration is idempotent");
        Context isolated = new ContextWrapper(target) {
            @Override public File getFilesDir() { return files; }
            @Override public Context getApplicationContext() { return this; }
        };
        Object pipeline = loader.loadClass("com.github.yumelira.yumebox.runtime.service.session.CompiledConfigPipeline")
            .getConstructor(Context.class).newInstance(isolated);
        check(((List<?>)invoke(pipeline, "resolveOverrideSpecs", "profile")).isEmpty(), "disabled override chains stay disabled in the actual compile pipeline");
        java.nio.file.Files.write(yaml.toPath(), converted.replace("enabled: false", "enabled: true").getBytes("UTF-8"));
        check(((List<?>)invoke(pipeline, "resolveOverrideSpecs", "profile")).size() == 2, "enabled legacy chains resolve their preset and custom override files");
    }

    private String release(String tag, boolean prerelease, boolean draft, String asset, String digest) {
        return "{\"id\":9000000001,\"tag_name\":\""+tag+"\",\"body\":\"Device validation\",\"html_url\":\"https://github.com/Yizuka17/YumeBox-MaterialDesign/releases/tag/"+tag+"\",\"published_at\":\"2027-01-01T00:00:00Z\",\"prerelease\":"+prerelease+",\"draft\":"+draft+",\"assets\":[{\"name\":\""+asset+"\",\"state\":\"uploaded\",\"browser_download_url\":\"https://github.com/Yizuka17/YumeBox-MaterialDesign/releases/download/"+tag+"/"+asset+"\",\"size\":11"+(digest == null ? "" : ",\"digest\":\""+digest+"\"")+"}]}";
    }
    private Object manager(Context context, String version, long versionCode) throws Exception {
        Class<?> interceptor = loader.loadClass("okhttp3.Interceptor");
        Object mock = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
            if (!method.getName().equals("intercept")) return null;
            if (networkFailure) throw new IOException("simulated offline");
            Object chain = args[0]; Object request = invoke(chain, "request"); requestedUrl = String.valueOf(invoke(request, "url"));
            if (responseDelay > 0) Thread.sleep(responseDelay);
            Class<?> media = loader.loadClass("okhttp3.MediaType");
            Object type = media.getMethod("get", String.class).invoke(null, "application/json");
            Class<?> responseBody = loader.loadClass("okhttp3.ResponseBody");
            Object body = responseBody.getMethod("create", media, String.class).invoke(null, type, response);
            Object builder = loader.loadClass("okhttp3.Response$Builder").getConstructor().newInstance();
            invoke(builder, "request", request); invoke(builder, "protocol", loader.loadClass("okhttp3.Protocol").getField("HTTP_1_1").get(null));
            invoke(builder, "code", code); invoke(builder, "message", "validation"); invoke(builder, "body", body);
            return invoke(builder, "build");
        });
        Object clientBuilder = loader.loadClass("okhttp3.OkHttpClient$Builder").getConstructor().newInstance();
        invoke(clientBuilder, "addInterceptor", mock);
        Object client = invoke(clientBuilder, "build");
        return loader.loadClass("com.github.yumelira.yumebox.update.GitHubUpdateManager")
            .getConstructor(Context.class, String.class, long.class, long.class, String.class, loader.loadClass("okhttp3.OkHttpClient"))
            .newInstance(context, version, versionCode, 1700000000L, "12345678", client);
    }
    private Object suspend(Object receiver, String name, Object... arguments) throws Throwable {
        CountDownLatch latch = new CountDownLatch(1); AtomicReference<Object> result = new AtomicReference<>();
        Class<?> continuation = loader.loadClass("kotlin.coroutines.Continuation");
        Object completion = Proxy.newProxyInstance(loader, new Class<?>[]{continuation}, (proxy, method, args) -> {
            if (method.getName().equals("getContext")) return loader.loadClass("kotlin.coroutines.EmptyCoroutineContext").getField("INSTANCE").get(null);
            if (method.getName().equals("resumeWith")) { result.set(args[0]); latch.countDown(); }
            return null;
        });
        Method chosen = null;
        for (Method method : receiver.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == arguments.length + 1) { chosen = method; break; }
        if (chosen == null) throw new NoSuchMethodException(name);
        Object immediate;
        Object[] invocation = Arrays.copyOf(arguments, arguments.length + 1); invocation[arguments.length] = completion;
        try { immediate = chosen.invoke(receiver, invocation); } catch (InvocationTargetException e) { throw e.getCause(); }
        if (immediate != null && immediate.toString().equals("COROUTINE_SUSPENDED")) {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("Coroutine timeout: " + name);
            immediate = result.get();
        }
        try { loader.loadClass("kotlin.ResultKt").getMethod("throwOnFailure", Object.class).invoke(null, immediate); }
        catch (InvocationTargetException e) { throw e.getCause(); }
        return immediate;
    }
    private static Object invoke(Object receiver, String name, Object... args) throws Exception {
        for (Method method : receiver.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            try { method.setAccessible(true); return method.invoke(receiver, args); }
            catch (IllegalArgumentException wrongOverload) { }
            catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
        }
        throw new NoSuchMethodException(name);
    }
    private Object value(Object flow) throws Exception {
        return loader.loadClass("kotlinx.coroutines.flow.StateFlow").getMethod("getValue").invoke(flow);
    }
    private void validate(Object manager, File file) throws Throwable {
        Method method = manager.getClass().getDeclaredMethod("validateApk", File.class); method.setAccessible(true);
        try { method.invoke(manager, file); } catch (InvocationTargetException e) { throw e.getCause(); }
    }
    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++; report.append("PASS: ").append(name).append('\n');
        Bundle status = new Bundle(); status.putString("stream", "PASS: " + name + "\n"); sendStatus(0, status);
    }
    private interface ThrowingAction { void run() throws Throwable; }
    private void expectFailure(ThrowingAction action, String name) throws Throwable {
        try { action.run(); } catch (Throwable expected) { if (expected instanceof AssertionError) throw expected; check(true, name); return; }
        throw new AssertionError(name + " unexpectedly succeeded");
    }
    private void expectFailure(ThrowingAction action, String name, String message) throws Throwable {
        try { action.run(); }
        catch (Throwable expected) {
            if (expected instanceof AssertionError) throw expected;
            check(String.valueOf(expected.getMessage()).contains(message), name + " (expected reason)");
            return;
        }
        throw new AssertionError(name + " unexpectedly succeeded");
    }
    private static final class RecordingContext extends ContextWrapper {
        Intent intent;
        RecordingContext(Context base) { super(base); }
        @Override public Context getApplicationContext() { return this; }
        @Override public void startActivity(Intent value) { intent = value; }
    }
}
