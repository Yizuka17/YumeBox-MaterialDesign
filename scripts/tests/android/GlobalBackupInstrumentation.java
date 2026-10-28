package com.github.yizuka17.yumebox.md3.validation;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;

/** Exercises the actual app's global archive with disposable records and restores the original snapshot. */
public final class GlobalBackupInstrumentation extends Instrumentation {
    private Context target;
    private ClassLoader loader;
    private int passed;
    private final StringBuilder report = new StringBuilder();
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        target = getTargetContext(); loader = target.getClassLoader();
        Bundle result = new Bundle(); int resultCode = Activity.RESULT_OK;
        Object originalPlan = null, plan = null, controller = null, proxy = null, network = null;
        boolean running = false;
        try {
            // Instrumentation starts its worker before Application.onCreate has returned.
            waitForIdleSync();
            controller = singleton("com.github.yumelira.yumebox.backup.GlobalBackupController");
            proxy = singleton("com.github.yumelira.yumebox.runtime.client.ProxyFacade");
            network = singleton("com.github.yumelira.yumebox.data.store.NetworkSettingsStore");
            running = (Boolean)invoke(value(invoke(proxy, "getRuntimeSnapshot")), "getRunning");
            if (running) suspend(proxy, "stopProxy");
            File original = new File(target.getCacheDir(), "validation-global-original.zip");
            suspend(controller, "exportTo", Uri.fromFile(original));
            originalPlan = suspend(controller, "prepareImport", Uri.fromFile(original));
            check(original.length() > 0, "original data snapshot saved before mutation");

            Object store = singleton("com.github.yumelira.yumebox.data.store.OverrideConfigStore");
            Object bindings = singleton("com.github.yumelira.yumebox.data.store.ProfileBindingStore");
            Object stats = singleton("com.github.yumelira.yumebox.data.store.TrafficStatisticsStore");
            Object oldStats = suspend(stats, "exportBackup");
            Object profileStore = instance("com.github.yumelira.yumebox.runtime.service.profile.ProfileStore");
            List<Object> oldProfiles = new ArrayList<>((List<?>)invoke(profileStore, "loadImported"));
            List<Object> oldOrder = new ArrayList<>((List<?>)invoke(profileStore, "loadProfileOrder"));
            UUID uuid = UUID.randomUUID();
            Object type = loader.loadClass("com.github.yumelira.yumebox.runtime.api.Profile$Type").getField("File").get(null);
            Object imported = construct("com.github.yumelira.yumebox.runtime.service.profile.Imported", uuid, "全局配置 🧪", type, "", 60000L, 12L, 34L, 100L, 0L, 1L, "validation-only-age-secret");
            List<Object> profiles = new ArrayList<>(oldProfiles); profiles.add(imported);
            invoke(profileStore, "saveImported", profiles);
            List<Object> order = new ArrayList<>(oldOrder); order.add(uuid); invoke(profileStore, "saveProfileOrder", order);
            File profileDir = new File(target.getFilesDir(), "imported/" + uuid);
            write(new File(profileDir, "config.yaml"), "mixed-port: 7890\nproxies: []\nrules: [MATCH,DIRECT]\n");
            String providerText = "payload:\n  - DOMAIN,validation.example\n";
            write(new File(profileDir, "providers/rules/test.yaml"), providerText);
            String id = "cfg-global-" + UUID.randomUUID();
            Object yaml = loader.loadClass("com.github.yumelira.yumebox.data.model.OverrideContentType").getField("Yaml").get(null);
            String content = "dns:\n  enable: true\nunknown-field: 🧪 preserved\n";
            Object override = construct("com.github.yumelira.yumebox.data.model.OverrideConfig", id, "覆写 🧪", null, yaml, content, 1L, 2L);
            suspend(store, "save", override);
            Object binding = construct("com.github.yumelira.yumebox.data.model.ProfileBinding", uuid.toString(), Collections.singletonList(id), false);
            suspend(bindings, "setBinding", binding);
            Object health = invoke(singleton("com.github.yumelira.yumebox.data.store.AppSettingsStore"), "getHealthCheckConcurrency");
            Object originalHealth = invoke(health, "getValue");

            List<Object> apps = new ArrayList<>((List<?>)invoke(oldStats, "getApps"));
            apps.add(construct("com.github.yumelira.yumebox.data.store.room.AppTrafficDailyEntity", 1L, "validation-global", null, "Validation", 12L, 34L, 1L));
            Object expectedStats = construct("com.github.yumelira.yumebox.data.store.room.TrafficStatisticsBackup", apps, invoke(oldStats, "getRoutes"), invoke(oldStats, "getHours"));
            suspend(stats, "restoreBackup", expectedStats);
            expectedStats = suspend(stats, "exportBackup"); // DAO exports a stable key order.
            File backup = new File(target.getCacheDir(), "validation-global-roundtrip.zip");
            suspend(controller, "exportTo", Uri.fromFile(backup));
            plan = suspend(controller, "prepareImport", Uri.fromFile(backup));
            check(!((Boolean)invoke(plan, "getLegacySettingsOnly")), "ZIP identified as a complete global backup");
            check(((Integer)invoke(plan, "getProfileCount")) == profiles.size(), "archive profile count includes the local profile");
            Set<String> names = entries(backup);
            check(names.contains("profiles.json") && names.contains("statistics.json") && names.contains("files/overrides/metadata.yaml"), "archive contains profiles, statistics and override bindings");
            check(names.contains("files/imported/" + uuid + "/providers/rules/test.yaml"), "original provider files are included");

            invoke(profileStore, "saveImported", oldProfiles); invoke(profileStore, "saveProfileOrder", oldOrder);
            delete(profileDir); suspend(store, "delete", id); suspend(stats, "restoreBackup", oldStats);
            invoke(health, "set", originalHealth.equals(8) ? 16 : 8);
            suspend(controller, "restore", plan);
            List<?> restored = (List<?>)invoke(profileStore, "loadImported");
            Object restoredProfile = restored.stream().filter(item -> { try { return uuid.equals(invoke(item, "getUuid")); } catch (Exception e) { throw new RuntimeException(e); } }).findFirst().orElseThrow(() -> new AssertionError("profile missing"));
            check("validation-only-age-secret".equals(invoke(restoredProfile, "getAgeSecretKey")), "local profile identity and encryption key survive restoration");
            check(providerText.equals(read(new File(profileDir, "providers/rules/test.yaml"))), "deleted provider source restored byte for byte");
            check(content.equals(invoke(suspend(store, "getById", id), "getContent")), "deleted override restored without losing unknown fields or emoji");
            Object restoredBinding = suspend(bindings, "getBinding", uuid.toString());
            check(Boolean.FALSE.equals(invoke(restoredBinding, "getEnabled")) && ((List<?>)invoke(restoredBinding, "getOverrideIds")).contains(id), "profile binding identity and disabled state restored");
            check(originalHealth.equals(invoke(health, "getValue")), "settings restore updates the live preference flow");
            check(expectedStats.equals(suspend(stats, "exportBackup")), "traffic history restored transactionally without summing twice");
            suspend(controller, "restore", plan);
            check(((List<?>)invoke(profileStore, "loadImported")).size() == profiles.size(), "repeated restoration does not duplicate profiles");
            check(expectedStats.equals(suspend(stats, "exportBackup")), "repeated restoration does not duplicate traffic totals");

            File tampered = new File(target.getCacheDir(), "validation-global-tampered.zip");
            rewrite(backup, tampered, "settings.json", "{}");
            final Object actualController = controller;
            failure(() -> suspend(actualController, "prepareImport", Uri.fromFile(tampered)), "tampered payload rejected before data mutation");
            File invalid = new File(target.getCacheDir(), "validation-global-traversal.zip");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(invalid))) { zip.putNextEntry(new ZipEntry("files/imported/../escaped")); zip.write(1); zip.closeEntry(); }
            failure(() -> suspend(actualController, "prepareImport", Uri.fromFile(invalid)), "archive path traversal is rejected");
            check(!new File(target.getFilesDir(), "escaped").exists(), "invalid archive never writes outside its staging directory");
            check(((List<?>)invoke(profileStore, "loadImported")).size() == profiles.size(), "failed validation leaves live profiles untouched");
            File future = new File(target.getCacheDir(), "validation-global-future.zip");
            String manifest;
            try (ZipFile zip = new ZipFile(backup)) { manifest = new String(bytes(zip.getInputStream(zip.getEntry("manifest.json"))), StandardCharsets.UTF_8); }
            rewrite(backup, future, "manifest.json", manifest.replace("\"version\":1", "\"version\":999"));
            failure(() -> suspend(actualController, "prepareImport", Uri.fromFile(future)), "unsupported global backup version is rejected");
            result.putString("stream", report + "PASS: " + passed + " global backup device checks\n");
        } catch (Throwable e) {
            resultCode = Activity.RESULT_CANCELED;
            result.putString("stream", report + "FAIL: " + android.util.Log.getStackTraceString(e));
        } finally {
            try {
                if (originalPlan != null) { suspend(controller, "restore", originalPlan); invoke(controller, "discard", originalPlan); }
                if (plan != null) invoke(controller, "discard", plan);
                if (running && proxy != null) suspend(proxy, "startProxy", invoke(invoke(network, "getRunMode"), "getValue"));
            } catch (Throwable cleanup) { resultCode = Activity.RESULT_CANCELED; result.putString("cleanup", android.util.Log.getStackTraceString(cleanup)); }
        }
        finish(resultCode, result);
    }
    private Object instance(String name) throws Exception { return loader.loadClass(name).getField("INSTANCE").get(null); }
    private Object singleton(String name) throws Exception {
        Object koin = invoke(instance("org.koin.mp.KoinPlatform"), "getKoin");
        Object type = loader.loadClass("kotlin.jvm.internal.Reflection").getMethod("getOrCreateKotlinClass", Class.class).invoke(null, loader.loadClass(name));
        return invoke(koin, "get", type, null, null);
    }
    private Object construct(String name, Object... arguments) throws Exception {
        for (Constructor<?> constructor : loader.loadClass(name).getConstructors()) if (constructor.getParameterCount() == arguments.length) {
            try { return constructor.newInstance(arguments); } catch (IllegalArgumentException mismatch) { }
        }
        throw new NoSuchMethodException(name);
    }
    private Object suspend(Object receiver, String name, Object... arguments) throws Throwable {
        Class<?> continuation = loader.loadClass("kotlin.coroutines.Continuation");
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Object> result = new AtomicReference<>();
        Object completion = Proxy.newProxyInstance(loader, new Class<?>[]{continuation}, (proxy, method, args) -> {
            if (method.getName().equals("getContext")) return loader.loadClass("kotlin.coroutines.EmptyCoroutineContext").getField("INSTANCE").get(null);
            if (method.getName().equals("resumeWith")) { result.set(args[0]); done.countDown(); } return null;
        });
        Object[] args = Arrays.copyOf(arguments, arguments.length + 1); args[arguments.length] = completion;
        Object output = invoke(receiver, name, args);
        Object suspended = loader.loadClass("kotlin.coroutines.intrinsics.IntrinsicsKt").getMethod("getCOROUTINE_SUSPENDED").invoke(null);
        if (output == suspended) { if (!done.await(180, TimeUnit.SECONDS)) throw new AssertionError("operation timed out: " + name); output = result.get(); }
        try { loader.loadClass("kotlin.ResultKt").getMethod("throwOnFailure", Object.class).invoke(null, output); }
        catch (InvocationTargetException e) { throw e.getCause(); } return output;
    }
    private static Object invoke(Object receiver, String name, Object... args) throws Exception {
        for (Method method : receiver.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            try { method.setAccessible(true); return method.invoke(receiver, args); } catch (IllegalArgumentException mismatch) { }
            catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
        }
        throw new NoSuchMethodException(name);
    }
    private Object value(Object flow) throws Exception { return loader.loadClass("kotlinx.coroutines.flow.StateFlow").getMethod("getValue").invoke(flow); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); passed++; report.append("PASS: ").append(message).append('\n'); Bundle status = new Bundle(); status.putString("stream", "PASS: " + message + "\n"); sendStatus(0, status); }
    private interface Action { void run() throws Throwable; }
    private void failure(Action action, String message) throws Throwable { try { action.run(); } catch (Throwable e) { if (e instanceof AssertionError) throw e; check(true, message); return; } throw new AssertionError(message); }
    private static void write(File file, String value) throws Exception { file.getParentFile().mkdirs(); try (FileOutputStream output = new FileOutputStream(file)) { output.write(value.getBytes(StandardCharsets.UTF_8)); } }
    private static String read(File file) throws Exception { try (InputStream input = new FileInputStream(file)) { return new String(bytes(input), StandardCharsets.UTF_8); } }
    private static byte[] bytes(InputStream input) throws Exception { ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[32768]; int size; while ((size = input.read(buffer)) >= 0) output.write(buffer, 0, size); return output.toByteArray(); }
    private static Set<String> entries(File file) throws Exception { Set<String> names = new HashSet<>(); try (ZipFile zip = new ZipFile(file)) { Enumeration<? extends ZipEntry> e = zip.entries(); while (e.hasMoreElements()) names.add(e.nextElement().getName()); } return names; }
    private static void rewrite(File original, File destination, String replacementName, String replacement) throws Exception {
        try (ZipFile source = new ZipFile(original); ZipOutputStream output = new ZipOutputStream(new FileOutputStream(destination))) {
            Enumeration<? extends ZipEntry> entries = source.entries(); while (entries.hasMoreElements()) { ZipEntry entry = entries.nextElement(); output.putNextEntry(new ZipEntry(entry.getName())); if (entry.getName().equals(replacementName)) output.write(replacement.getBytes(StandardCharsets.UTF_8)); else try (InputStream input = source.getInputStream(entry)) { output.write(bytes(input)); } output.closeEntry(); }
        }
    }
    private static void delete(File file) { if (file.isDirectory()) for (File child : Objects.requireNonNull(file.listFiles())) delete(child); if (file.exists() && !file.delete()) throw new IllegalStateException("fixture cleanup failed"); }
}
