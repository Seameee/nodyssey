// The iOS shell: what `:app` is for Android, on the other platform.
//
// Not `plaza.kmp.library` — that plugin's whole point is the Android target every *library* module
// here shares, and this module is the one that must not have one. It is also not a library: nothing
// depends on it, it depends on everything, and what it produces is a framework an Xcode project
// links rather than a klib another module resolves.

plugins {
    // Not `alias(libs.plugins...)`: `build-logic` already puts the Kotlin Gradle plugin on the root
    // build's classpath, and asking for it again by version fails as a duplicate request. Same line,
    // same reason, as `gallery/build.gradle.kts`.
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("plaza.dependency-locking")
}

kotlin {
    jvmToolchain(21)

    // Both arches, and both are load-bearing: the simulator is what step D3b runs on this machine,
    // and the device arch is the one an installable build needs. A framework is per-architecture, so
    // leaving either out is not a smaller build — it is a destination Xcode cannot select.
    //
    // Two arches, not three: there is no `iosX64()`, so an Intel simulator slice does not exist. That
    // is a decision rather than an omission — this project is Apple-silicon-only — but Xcode does not
    // know it, and a plain `xcodebuild -sdk iphonesimulator` asks for a universal `arm64 + x86_64`
    // binary and fails on the half that was never built. The answer lives in the Xcode project as
    // `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64`, in both configurations; it is recorded here too
    // because Xcode rewrites `project.pbxproj` freely and this file is where the reason is.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            // What the Xcode project imports. Named for what it is rather than for the app: the app
            // is the Xcode target, and this is the Kotlin half it embeds.
            baseName = "NodysseyShell"

            // Static, which is what the CMP template ships and what avoids a second signing step for
            // an embedded dynamic framework. The cost is link time, paid once per build.
            isStatic = true

            /*
             * Nothing here optimizes the generated code for size. This is a deliberate reversal of a
             * trade this file used to make, and the reason it was worth reversing is what the trade
             * actually cost — see below, because the numbers are the argument.
             *
             * What used to be here was `binaryOption("smallBinary", "true")`, which sets LLVM's size
             * level to AGGRESSIVE (`-Oz`, from a baseline of none) and turns off
             * `inlineForPerformance`, Kotlin's own pre-codegen inlining pass. It is not a free win
             * and it was never presented as one; the file said so itself: "The cost is runtime speed,
             * and it is not measured. `-Oz` and no inlining is exactly the trade its name implies,
             * and Compose's hot paths — recomposition, layout, scroll — are the code it applies to."
             *
             * That last sentence is the whole reason it is gone. Recomposition, layout and scroll are
             * not incidental to this app; they are what a reader does with it for an hour at a time,
             * and on iOS they run through the code this option de-optimized. `-Oz` also drops the
             * inlining that Compose's runtime is written to expect — `inlineForPerformance` exists
             * because the compiler inlines across the boundaries the runtime leans on — so the cost
             * lands hardest exactly where the frame budget is thinnest.
             *
             * It was also never checked against a device. `docs/kmp-migration-plan.md` step D3c
             * records that the `.ipa` produced by CI "没有在任何一台真机上装过" — re-signing needs an
             * Apple account and the build machine had none — so the one measurement that could have
             * contradicted this trade had never been taken.
             *
             * What it bought, from the same file's measurements, is small: the binary was 62,369,024 B
             * with it off and 53,063,360 B with it on (−14.9%), and the download about 2.50 MB smaller
             * — the CI build's 20.89 MB against roughly 18.4. Everything the map attributed to C++ is
             * unaffected either way: 7 MB of ICU (6 MB of it a single object file a linker cannot
             * split) and 4.7 MB of Skia are not Kotlin and not in scope. For an app that arrives by
             * sideload rather than over a cellular App Store download, two and a half megabytes is not
             * a cost. A jankier scroll is.
             *
             * Only Release is affected by any of this — the compiler ignores `smallBinary` for a debug
             * binary and says so — so a local debug build compiles the same way it always did.
             *
             * Re-adding the line is a one-line decision, and the numbers above are what it should be
             * weighed against. It should not be re-added on size grounds alone without a device
             * measurement of what it does to scroll, because that measurement is the one this
             * reversal is based on and the one nobody has.
             */
        }
    }

    sourceSets {
        iosMain.dependencies {
            // The screens, and through them `:designsys`, `:shared` and Compose Resources — the same
            // three faces `:app` reads.
            implementation(project(":ui"))

            // `ComposeUIViewController`, the one thing this module needs that a screen does not: the
            // seam between UIKit's world and the composition.
            implementation(libs.compose.ui)

            // Coil, and the reason this module names it at all: on this platform the core library
            // ships no network fetcher, so an app that does not install one draws every remote image
            // as a failure. `:app` answers by handing Coil its `OkHttpClient`; the answer here is a
            // `NetworkClient` over `NSURLSession` — see `IosImageLoader.kt`. `coil-network-core` is
            // the interface half of that, with no engine of its own.
            implementation(libs.coil.core)
            implementation(libs.coil.network.core)

            // The same two `:app` names, and each for a reason that is visible on screen rather
            // than theoretical — see `IosImageLoader.kt`.
            implementation(libs.coil.network.cache.control)
            implementation(libs.coil.svg)
        }
    }
}
