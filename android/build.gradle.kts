// Root build file for the ChattlyX Android client.
//
// All shared configuration lives in `build-logic` convention plugins
// (chattlyx.android.*, chattlyx.jvm.library). This file intentionally stays
// empty so module build files only declare plugins and dependencies.
//
// Phase 2 tech debt — static analysis on Android:
//  * detekt 1.23.8's DetektAndroid references com.android.build.gradle
//    .BaseExtension, which AGP 9 removed (NoClassDefFoundError on apply).
//  * ktlint-gradle 14.2.0 needs AGP classes on its own classloader; applied
//    from the root it cannot see AGP (which lives in build-logic).
//  Re-introduce both as convention plugins inside build-logic once detekt 2.x
//  stable supports AGP 9; until then lint runs in Phase 7 hardening.
