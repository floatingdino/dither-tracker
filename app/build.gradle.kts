import java.util.Properties

plugins {
    id("com.android.application")
}

// ---- Release signing (T23): never in the repo. See README.md "Release build and signing". ----
// Read, first match wins per key: env var DITHER_<KEY> → Gradle property dither.<key> (e.g. in
// ~/.gradle/gradle.properties) → local/keystore.properties (gitignored `/local/`), key `<key>`.
// Keys: storeFile, storePassword, keyAlias, keyPassword. Without a storeFile the release APK is built
// unsigned (app-release-unsigned.apk); `-PdebugSignRelease` signs it with the debug key instead, for
// local testing only.
val keystoreProps = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local/keystore.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

fun signingValue(key: String): String? =
    providers.environmentVariable("DITHER_" + key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()).orNull
        ?: providers.gradleProperty("dither.$key").orNull
        ?: keystoreProps.getProperty(key)

val releaseStoreFile: File? = signingValue("storeFile")?.let { rootProject.file(it) }
val debugSignRelease = providers.gradleProperty("debugSignRelease").isPresent

// `-PappIdSuffix=.t58` installs a build beside the owner's app (its own data) for on-device checks
// (T58: the release build's hidden-API answer). Default: none. Uninstall the suffixed package after.
val appIdSuffix: String? = providers.gradleProperty("appIdSuffix").orNull?.takeIf { it.isNotEmpty() }

android {
    namespace = "au.com.josephmark.dithertracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "au.com.josephmark.dithertracker"
        minSdk = 26
        targetSdk = 37
        // Bump versionCode for every APK handed to a device as an update; versionName is semver.
        // The release workflow passes both (-Pdither.versionCode / -Pdither.versionName): the code is
        // the workflow run number, the name comes from the tag.
        versionCode = providers.gradleProperty("dither.versionCode").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("dither.versionName").orNull ?: "0.1.0"
        appIdSuffix?.let { applicationIdSuffix = it }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword") ?: signingValue("storePassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 (full mode) + resource shrinking. The app needs no keep rules of its own: nothing is
            // looked up by name (no Class.forName, no enum valueOf, saves are numeric codes), and the
            // reflective calls (eink/SupernoteFullRefresh, eink/EinkViewMode) look up methods of framework
            // classes by string, which R8 never sees or renames (T58, docs/eink.md).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = when {
                releaseStoreFile != null -> signingConfigs.getByName("release")
                debugSignRelease -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        // BuildConfig.DEBUG gates the adb-only QA hooks in MainActivity, so release compiles them out.
        buildConfig = true
        resValues = false
    }
}

if (releaseStoreFile != null && !releaseStoreFile.isFile &&
    gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
) {
    throw GradleException("Release storeFile not found: $releaseStoreFile (see README.md, Release build and signing)")
}

// Say how the release APK was signed every time it is assembled (also on a configuration-cache hit).
val releaseSigningNote: String = when {
    releaseStoreFile != null -> "Release APK signed with ${releaseStoreFile.name}: app/build/outputs/apk/release/app-release.apk"
    debugSignRelease -> "WARNING: release APK signed with the DEBUG key (-PdebugSignRelease). For testing only; do not distribute."
    else -> "WARNING: no release keystore configured, so app/build/outputs/apk/release/app-release-unsigned.apk " +
        "is unsigned and cannot be installed until signed. See README.md, Release build and signing."
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    val note = releaseSigningNote
    val warn = note.startsWith("WARNING")
    doLast { if (warn) logger.warn(note) else logger.lifecycle(note) }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

// ---- Icons: design/icons/*.txt (16×16, '#' ink, '.' paper) → generated Kotlin enum `Icon` ----

/**
 * Converts every `design/icons/<name>.txt` into one generated `Icon.kt` (enum of packed 16-bit rows),
 * so the app loads icons with zero parsing at runtime. Picks up whatever files exist at build time;
 * fails the build with file:line detail on any malformed icon.
 */
@CacheableTask
abstract class GenerateIconsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val iconFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val size = 16
        val errors = mutableListOf<String>()
        val icons = sortedMapOf<String, Pair<String, IntArray>>() // enum name -> (id, rows)
        val nameRe = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        for (f in iconFiles.files.sortedBy { it.name }) {
            val id = f.nameWithoutExtension.lowercase()
            if (!nameRe.matches(id)) {
                errors += "${f.path}: icon name '${f.nameWithoutExtension}' must match [a-z0-9]+(-[a-z0-9]+)*"
                continue
            }
            val lines = f.readText().replace("\r\n", "\n").removeSuffix("\n").split("\n")
            val fileErrors = mutableListOf<String>()
            if (lines.size != size) fileErrors += "${f.path}: expected $size lines, found ${lines.size}"
            val rows = IntArray(size)
            lines.take(size).forEachIndexed { y, line ->
                if (line.length != size) {
                    fileErrors += "${f.path}:${y + 1}: expected $size chars, found ${line.length}: \"$line\""
                }
                line.forEachIndexed { x, c ->
                    when (c) {
                        '#' -> if (x < size) rows[y] = rows[y] or (1 shl (size - 1 - x))
                        '.' -> {}
                        else -> fileErrors += "${f.path}:${y + 1}:${x + 1}: invalid char '$c' (only '#' and '.')"
                    }
                }
            }
            if (fileErrors.isNotEmpty()) {
                errors += fileErrors
                continue
            }
            var enumName = id.uppercase().replace('-', '_')
            if (enumName[0].isDigit()) enumName = "I_$enumName"
            val clash = icons[enumName]
            if (clash != null) {
                errors += "${f.path}: icon id '$id' clashes with '${clash.first}'"
                continue
            }
            icons[enumName] = id to rows
        }
        if (errors.isNotEmpty()) {
            throw GradleException(
                "Malformed icon(s) (each must be exactly 16 lines × 16 chars of '#'/'.'):\n  " +
                    errors.joinToString("\n  "),
            )
        }
        val pkg = "au.com.josephmark.dithertracker.dither"
        val out = outputDir.get().asFile.resolve(pkg.replace('.', '/')).resolve("Icon.kt")
        out.parentFile.mkdirs()
        out.writeText(
            buildString {
                appendLine("// GENERATED by :app:generateIcons from design/icons/*.txt — do not edit.")
                appendLine("package $pkg")
                appendLine()
                appendLine("/**")
                appendLine(" * 16×16 1-bit icons; rows packed MSB = leftmost column. Draw with [DitherRenderer.icon].")
                appendLine(" * [halo]: the icon's ${size + 2}×${size + 2} paper cut-out (T41; see [HaloMask] for the rule), one")
                appendLine(" * pixel wider than the icon on every side: draw it at (x − 1, y − 1).")
                appendLine(" */")
                appendLine("enum class Icon(val id: String, private val rows: IntArray, haloRows: IntArray) : MonoImage {")
                for ((enumName, v) in icons) {
                    val hex = v.second.joinToString(", ") { "0x%04X".format(it) }
                    val halo = haloRows(v.second, size, size).joinToString(", ") { "0x%05X".format(it) }
                    appendLine("    $enumName(\"${v.first}\", intArrayOf($hex),")
                    appendLine("        intArrayOf($halo)),")
                }
                appendLine("    ;")
                appendLine()
                appendLine("    override val width: Int get() = SIZE")
                appendLine("    override val height: Int get() = SIZE")
                appendLine("    override fun isInk(x: Int, y: Int): Boolean = (rows[y] ushr (SIZE - 1 - x)) and 1 == 1")
                appendLine()
                appendLine("    /** The paper cut-out behind this icon on a dither fill: [HALO_SIZE]², centred on the icon. */")
                appendLine("    val halo: MonoImage = PackedImage(${size + 2}, ${size + 2}, haloRows)")
                appendLine()
                appendLine("    companion object {")
                appendLine("        const val SIZE = $size")
                appendLine("        const val HALO_SIZE = ${size + 2}")
                appendLine("        private val byId: Map<String, Icon> = entries.associateBy { it.id }")
                appendLine()
                appendLine("        /** Looks up an icon by its file name (without `.txt`), e.g. `\"chevron-left\"`. */")
                appendLine("        fun byName(name: String): Icon? = byId[name.lowercase()]")
                appendLine("    }")
                appendLine("}")
            },
        )
    }

    companion object {
        /**
         * The halo rule (T41, "halo 1"), shared by the icon and font steps; `dither/HaloMask.kt` is the
         * runtime twin for glyphs built at layout time. From a w×h glyph (`rows` MSB-first):
         * 1. fill the glyph's holes (paper not 4-connected to the glyph box's edge becomes solid);
         * 2. dilate by 1 px, 8-connected (3×3 square), into a (w+2)×(h+2) frame — steps 1–2 are exactly
         *    `design/icons/candidates/heavy/tools.py` `halo_mask(ink, 'f1')`;
         * 3. clean up, repeated until nothing changes: fill pixels the halo encloses (not 4-connected to
         *    the frame's edge), then fill every 1-px sliver (a pixel with halo both left and right, or
         *    both above and below, judged on the state at the start of the pass), so no stray dither is
         *    trapped between the glyph's parts.
         * Returns (h+2) rows of (w+2) bits, MSB-first.
         */
        fun haloRows(rows: IntArray, w: Int, h: Int, bridge: Boolean = false): IntArray {
            require(w + 2 <= 32) { "halo frame ${w + 2} px wide does not fit an Int row" }
            val ink = BooleanArray(w * h) { i -> (rows[i / w] ushr (w - 1 - i % w)) and 1 == 1 }
            val solid = fillHoles(ink, w, h)
            val fw = w + 2
            val fh = h + 2
            val out = BooleanArray(fw * fh)
            for (y in 0 until h) for (x in 0 until w) {
                if (!solid[y * w + x]) continue
                for (dy in 0..2) for (dx in 0..2) out[(y + dy) * fw + x + dx] = true
            }
            clean(out, fw, fh)
            if (bridge) {
                // Text (step 4): a row whose halo reaches the glyph box's second column from an edge runs
                // on to the frame's edge, so neighbouring glyphs' halos meet instead of leaving a 1-px
                // dither column between them (holds for tracking <= 2).
                for (y in 0 until fh) {
                    val r = y * fw
                    if (out[r + 2]) { out[r] = true; out[r + 1] = true }
                    if (out[r + fw - 3]) { out[r + fw - 1] = true; out[r + fw - 2] = true }
                }
                clean(out, fw, fh)
            }
            return IntArray(fh) { y ->
                var bits = 0
                for (x in 0 until fw) if (out[y * fw + x]) bits = bits or (1 shl (fw - 1 - x))
                bits
            }
        }

        /** Step 3, in place: enclosed pixels, then 1-px slivers (on the pass's starting state), until stable. */
        fun clean(out: BooleanArray, fw: Int, fh: Int) {
            while (true) {
                val a = fillHoles(out, fw, fh)
                var changed = !a.contentEquals(out)
                val b = a.copyOf()
                for (y in 0 until fh) for (x in 0 until fw) {
                    val i = y * fw + x
                    if (a[i]) continue
                    val lr = x > 0 && x < fw - 1 && a[i - 1] && a[i + 1]
                    val ud = y > 0 && y < fh - 1 && a[i - fw] && a[i + fw]
                    if (lr || ud) { b[i] = true; changed = true }
                }
                b.copyInto(out)
                if (!changed) break
            }
        }

        /** [set] plus every unset pixel not 4-connected to the w×h box's edge through unset pixels. */
        fun fillHoles(set: BooleanArray, w: Int, h: Int): BooleanArray {
            val outside = BooleanArray(w * h)
            val stack = IntArray(w * h)
            var sp = 0
            fun seed(x: Int, y: Int) {
                val i = y * w + x
                if (!set[i] && !outside[i]) { outside[i] = true; stack[sp++] = i }
            }
            for (x in 0 until w) { seed(x, 0); seed(x, h - 1) }
            for (y in 0 until h) { seed(0, y); seed(w - 1, y) }
            while (sp > 0) {
                val i = stack[--sp]
                val x = i % w
                val y = i / w
                if (x > 0) seed(x - 1, y)
                if (x < w - 1) seed(x + 1, y)
                if (y > 0) seed(x, y - 1)
                if (y < h - 1) seed(x, y + 1)
            }
            return BooleanArray(w * h) { !outside[it] }
        }
    }
}

val generateIcons = tasks.register<GenerateIconsTask>("generateIcons") {
    // -PiconsDir=<dir> lets the step be exercised against another folder (e.g. a malformed fixture).
    val dir = providers.gradleProperty("iconsDir").getOrElse(rootProject.file("design/icons").path)
    iconFiles.from(fileTree(dir) { include("*.txt") })
    outputDir.set(layout.buildDirectory.dir("generated/source/icons"))
}

// ---- Fonts: design/font/<face>/glyphs.txt → generated Kotlin object `Fonts` (see design/font/README.md) ----

/**
 * Converts every `design/font/<face>/glyphs.txt` into one generated `Fonts.kt`: one [Font] per face,
 * glyph rows packed MSB-first into an `intArrayOf(...)` literal, so there is zero parsing at runtime.
 * Fails the build with file:line detail on any malformed face.
 */
@CacheableTask
abstract class GenerateFontsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val faceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private class Face(val id: String, val w: Int, val h: Int, val tracking: Int, val capTop: Int, val capH: Int) {
        val chars = StringBuilder()
        val rows = ArrayList<Int>()
        /** Drop-shadow mask rows (`:` cells), parallel to [rows]; all zero for faces without a shadow. */
        val shadow = ArrayList<Int>()
        var notdef: IntArray? = null
        var notdefShadow: IntArray? = null
        val hasShadow: Boolean get() = shadow.any { it != 0 } || (notdefShadow?.any { it != 0 } ?: false)
    }

    @TaskAction
    fun generate() {
        val errors = mutableListOf<String>()
        val faces = sortedMapOf<String, Face>()
        val nameRe = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        for (f in faceFiles.files.sortedBy { it.parentFile.name }) {
            val id = f.parentFile.name
            if (!nameRe.matches(id)) {
                errors += "${f.path}: face directory '$id' must match [a-z0-9]+(-[a-z0-9]+)*"
                continue
            }
            val before = errors.size
            val face = parseFace(f, id, errors)
            if (face != null && errors.size == before) faces[id] = face
        }
        if (faces.isEmpty() && errors.isEmpty()) errors += "no design/font/<face>/glyphs.txt found"
        if (errors.isNotEmpty()) {
            throw GradleException("Malformed font face(s) (format: design/font/README.md):\n  " + errors.joinToString("\n  "))
        }
        val pkg = "au.com.josephmark.dithertracker.type"
        val out = outputDir.get().asFile.resolve(pkg.replace('.', '/')).resolve("Fonts.kt")
        out.parentFile.mkdirs()
        out.writeText(
            buildString {
                appendLine("// GENERATED by :app:generateFonts from design/font/*/glyphs.txt — do not edit.")
                appendLine("package $pkg")
                appendLine()
                appendLine("/** The bitmap faces. Glyph `i` occupies rows `[i*h, (i+1)*h)`; the last glyph is `.notdef`. */")
                appendLine("object Fonts {")
                for ((id, fc) in faces) {
                    val const = id.uppercase().replace('-', '_')
                    val all = fc.rows + fc.notdef!!.toList()
                    appendLine("    /** `$id`: ${fc.w}×${fc.h}, tracking ${fc.tracking}, cap ${fc.capTop}+${fc.capH}, ${fc.chars.length} glyphs + notdef. */")
                    appendLine("    @JvmField val $const: Font = Font(")
                    appendLine("        \"$id\", ${fc.w}, ${fc.h}, ${fc.tracking}, ${fc.capTop}, ${fc.capH},")
                    appendLine("        \"${kotlinEscape(fc.chars.toString())}\",")
                    appendLine("        intArrayOf(")
                    all.chunked(fc.h).forEach { g ->
                        appendLine("            " + g.joinToString(", ") { if (it >= 0) "0x%X".format(it) else it.toString() } + ",")
                    }
                    appendLine("        ),")
                    if (fc.hasShadow) {
                        val sh = fc.shadow + fc.notdefShadow!!.toList()
                        appendLine("        shadowRows = intArrayOf(")
                        sh.chunked(fc.h).forEach { g ->
                            appendLine("            " + g.joinToString(", ") { if (it >= 0) "0x%X".format(it) else it.toString() } + ",")
                        }
                        appendLine("        ),")
                    }
                    if (id in HALO_FACES) {
                        require(fc.tracking <= 2) { "$id: halo faces need tracking <= 2 (the bridge rule)" }
                        // T41: each glyph's paper cut-out, (w+2)×(h+2), by the icons' halo rule.
                        appendLine("        haloRows = intArrayOf(")
                        all.chunked(fc.h).forEach { g ->
                            val halo = GenerateIconsTask.haloRows(g.toIntArray(), fc.w, fc.h, bridge = true)
                            appendLine("            " + halo.joinToString(", ") { "0x%X".format(it) } + ",")
                        }
                        appendLine("        ),")
                    }
                    appendLine("    )")
                    appendLine()
                }
                appendLine("    @JvmField val all: List<Font> = listOf(${faces.keys.joinToString(", ") { it.uppercase().replace('-', '_') }})")
                appendLine("}")
            },
        )
    }

    private companion object {
        /** Faces that get generated halo rows (text drawn on a dither fill, T41): the small faces. */
        val HALO_FACES = setOf("ui", "num-m")
    }

    private fun kotlinEscape(s: String) = buildString {
        for (c in s) {
            if (c.code in 0x20..0x7E && c != '"' && c != '\\' && c != '$') append(c)
            else append("\\u%04X".format(c.code))
        }
    }

    private fun parseFace(f: File, id: String, errors: MutableList<String>): Face? {
        val lines = f.readText().replace("\r\n", "\n").split("\n")
        val hdr = HashMap<String, List<Int>>()
        var i = 0
        fun err(line: Int, msg: String) { errors += "${f.path}:${line + 1}: $msg" }
        // Header: `#` comments, blank lines, `key values...` until the first `--- ` glyph line.
        while (i < lines.size && !lines[i].startsWith("--- ")) {
            val l = lines[i].trim()
            if (l.isNotEmpty() && !l.startsWith("#")) {
                val parts = l.split(Regex("\\s+"))
                val key = parts[0]
                if (key == "face") {
                    if (parts.size != 2 || parts[1] != id) err(i, "face name must be '$id' (its directory), got '${parts.drop(1).joinToString(" ")}'")
                    hdr[key] = emptyList()
                } else {
                    val want = mapOf("size" to 2, "tracking" to 1, "cap" to 2)[key]
                    val nums = parts.drop(1).map { it.toIntOrNull() }
                    when {
                        want == null -> err(i, "unknown header key '$key' (face, size, tracking, cap)")
                        nums.size != want || nums.any { it == null } -> err(i, "'$key' needs $want integer(s)")
                        key in hdr -> err(i, "duplicate '$key'")
                        else -> hdr[key] = nums.map { it!! }
                    }
                }
            }
            i++
        }
        for (k in listOf("face", "size", "tracking", "cap")) if (k !in hdr) errors += "${f.path}: missing header '$k'"
        val size = hdr["size"] ?: return null
        val (w, h) = size
        val tracking = hdr["tracking"]?.get(0) ?: return null
        val (capTop, capH) = hdr["cap"] ?: return null
        if (w !in 1..32 || h < 1) { errors += "${f.path}: size ${w}x$h out of range (width 1..32, height >= 1)"; return null }
        if (tracking < 0) errors += "${f.path}: tracking must be >= 0"
        if (capTop < 0 || capH < 1 || capTop + capH > h) errors += "${f.path}: cap $capTop $capH must fit inside height $h"
        val face = Face(id, w, h, tracking, capTop, capH)
        val seen = HashMap<String, Int>()
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank()) { i++; continue }
            if (!l.startsWith("--- ")) { err(i, "expected '--- <char>' glyph header, found \"$l\""); i++; continue }
            val headerLine = i
            val key = l.substring(4)
            val ch: Char? = when {
                key == "notdef" -> null
                key.length == 1 && key[0] != ' ' -> key[0]
                Regex("^U\\+[0-9A-F]{4}$").matches(key) -> key.substring(2).toInt(16).toChar()
                else -> { err(i, "bad glyph key '$key' (one char, U+XXXX with uppercase hex, or notdef)"); '\u0000' }
            }
            val norm = ch?.let { "U+%04X".format(it.code) } ?: "notdef"
            seen[norm]?.let { err(i, "duplicate glyph $norm (first at line ${it + 1})") }
            seen[norm] = i
            i++
            val rows = IntArray(h)
            val shadowRows = IntArray(h)
            var ok = true
            for (y in 0 until h) {
                if (i >= lines.size || lines[i].startsWith("--- ") || lines[i].isEmpty()) {
                    err(headerLine, "glyph $norm has $y rows, expected $h"); ok = false; break
                }
                val row = lines[i]
                if (row.length != w) { err(i, "glyph $norm row ${y + 1} has ${row.length} chars, expected $w: \"$row\""); ok = false }
                row.forEachIndexed { x, c ->
                    when (c) {
                        '#' -> if (x < w) rows[y] = rows[y] or (1 shl (w - 1 - x))
                        '.' -> {}
                        ':' -> if (x < w) shadowRows[y] = shadowRows[y] or (1 shl (w - 1 - x))
                        else -> { err(i, "glyph $norm: invalid char '$c' at column ${x + 1} (only '#', '.' and ':')"); ok = false }
                    }
                }
                i++
            }
            if (ok && i < lines.size && lines[i].isNotBlank() && !lines[i].startsWith("--- ")) {
                err(i, "glyph $norm has more than $h rows"); ok = false
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].startsWith("--- ")) i++
            }
            if (!ok || ch == '\u0000') continue
            if (ch == null) { face.notdef = rows; face.notdefShadow = shadowRows } else {
                face.chars.append(ch); face.rows.addAll(rows.toList()); face.shadow.addAll(shadowRows.toList())
            }
        }
        if (face.notdef == null) errors += "${f.path}: missing required 'notdef' glyph (drawn for unknown chars)"
        return face
    }
}

val generateFonts = tasks.register<GenerateFontsTask>("generateFonts") {
    // -PfontDir=<dir> lets the step be exercised against another folder (e.g. a malformed fixture).
    val dir = providers.gradleProperty("fontDir").getOrElse(rootProject.file("design/font").path)
    faceFiles.from(fileTree(dir) { include("*/glyphs.txt") })
    outputDir.set(layout.buildDirectory.dir("generated/source/fonts"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateIcons, GenerateIconsTask::outputDir)
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateFonts, GenerateFontsTask::outputDir)
    }
}

// ---- Unit tests: pattern ASCII mirror lives in design/patterns/ ----
// `./gradlew :app:testDebugUnitTest -PupdatePatterns` rewrites the mirror from Patterns.kt.
val patternsDir = rootProject.file("design/patterns")
val updatePatterns = providers.gradleProperty("updatePatterns").isPresent
tasks.withType<Test>().configureEach {
    systemProperty("dither.patternsDir", patternsDir.absolutePath)
    systemProperty("dither.updatePatterns", updatePatterns.toString())
    systemProperty("dither.fontDir", rootProject.file("design/font").absolutePath)
    inputs.files(rootProject.fileTree("design/font")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.fileTree(patternsDir)).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("updatePatterns", updatePatterns)
    if (updatePatterns) outputs.upToDateWhen { false }
}

// ---- Effects: design/fx/<name>/{meta.txt,frame-N.txt} → generated Kotlin enum `Fx` (see design/fx/README.md) ----

/**
 * Converts every effect directory directly under an fx folder that has a `meta.txt` (one level deep
 * only; nested folders such as `design/fx/options/` are ignored) into one generated Kotlin file: per
 * effect an `FxSpec` with its size, anchor, role, per-frame hold, `tile` declarations and each frame
 * split into packed 1-bit layers by symbol (`#` ink, `o` knock-out, `x` cut, `1`–`9` tile layers;
 * `.` transparent), run-length coded in string constants so even 233×175 tile-scale frames stay
 * far below the JVM's 64 KB method limit. [target] `enum` writes the app's `enum class Fx`;
 * `options` writes the test-only `object FxOptions` (round-two options, never shipped). Fails the
 * build with file:line detail on any malformed effect.
 */
@CacheableTask
abstract class GenerateFxTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fxFiles: ConfigurableFileCollection

    /** `design/patterns/<NAME>.txt`: the tile names a `tile` line may use (mirror of Patterns.kt). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val patternFiles: ConfigurableFileCollection

    /** `enum` (the app's `Fx`) or `options` (test-only `FxOptions`). */
    @get:Input
    abstract val target: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private class Effect(val id: String, val w: Int, val h: Int, val role: String, val ref: String, val dx: Int, val dy: Int, val holds: List<Int>) {
        /** (digit, tile name, mode), ascending digit. */
        val tiles = ArrayList<Triple<Int, String, String>>()
        val ink = ArrayList<IntArray>()
        val ko = ArrayList<IntArray>()
        val cut = ArrayList<IntArray>()
        /** Per frame, digit → layer. */
        val tileLayers = ArrayList<Map<Int, IntArray>>()
    }

    private companion object {
        // T55: the round-three triggers (counters, toggles, day/night, table moments) join the life roles.
        val ROLES = listOf("damage", "heal", "big-hit", "ko", "win", "commander", "poison", "energy", "experience", "rad", "storm",
            "monarch", "initiative", "ring", "night", "day", "turn", "first-player", "revive")
        val REFS = listOf("centre", "tl", "tr", "bl", "br", "top", "bottom", "left", "right", "tile")
        val MODES = listOf("ink", "paper", "fill")
        // design/fx/options/ is the design archive (test-only FxOptions): its metas carry no `role`
        // line and the engine tests don't depend on one, so a missing role there is just "damage".
        // Shipped effects (design/fx/) must declare it.
        const val UNSET_ROLE = "damage"
        val KEYS = listOf("size", "frames", "anchor", "hold_ms", "ink_px", "knockout_px", "cut_px", "tile_px", "flips", "role", "tile")
        const val RLE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        const val RLE_PART = 16000 // chars per string constant (well under the 65535-byte limit)
    }

    @TaskAction
    fun generate() {
        val errors = mutableListOf<String>()
        val nameRe = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        val tileNames = patternFiles.files.map { it.nameWithoutExtension }.filter { it != "INDEX" }.toSortedSet()
        val byDir = fxFiles.files.groupBy { it.parentFile }
        val effects = sortedMapOf<String, Effect>()
        val options = when (target.get()) {
            "enum" -> false
            "options" -> true
            else -> throw GradleException("generateFx target must be enum|options, was '${target.get()}'")
        }
        for ((dir, files) in byDir.entries.sortedBy { it.key.name }) {
            val meta = files.firstOrNull { it.name == "meta.txt" } ?: continue
            val id = dir.name
            if (!nameRe.matches(id)) { errors += "${dir.path}: effect name '$id' must match [a-z0-9]+(-[a-z0-9]+)*"; continue }
            val before = errors.size
            val fx = parseEffect(id, meta, files, tileNames, errors, roleRequired = !options)
            if (fx != null && errors.size == before) effects[id] = fx
        }
        if (errors.isNotEmpty()) {
            throw GradleException("Malformed effect(s) (format: design/fx/README.md, design/fx/options/README.md):\n  " + errors.joinToString("\n  "))
        }
        val pkg = "au.com.josephmark.dithertracker.fx"
        val out = outputDir.get().asFile.resolve(pkg.replace('.', '/')).resolve(if (options) "FxOptions.kt" else "Fx.kt")
        out.parentFile.mkdirs()
        fun enumName(id: String) = id.uppercase().replace('-', '_').let { if (it[0].isDigit()) "FX_$it" else it }
        fun dataName(id: String) = (if (options) "FxOptData" else "FxData") + id.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
        out.writeText(
            buildString {
                appendLine("// GENERATED by :app:${name} from ${if (options) "design/fx/options" else "design/fx"}/*/{meta,frame-N}.txt — do not edit.")
                appendLine("package $pkg")
                appendLine()
                appendLine("import au.com.josephmark.dithertracker.dither.Patterns")
                appendLine()
                if (options) {
                    appendLine("/** Round-two effect options (design/fx/options/), test-only: the engine is exercised on them, the app never ships them. */")
                    appendLine("object FxOptions {")
                    appendLine("    @JvmField val all: List<FxSpec> = listOf(")
                    for (id in effects.keys) appendLine("        ${dataName(id)}.SPEC,")
                    appendLine("    )")
                    appendLine()
                    appendLine("    fun byName(name: String): FxSpec? = all.firstOrNull { it.id == name.lowercase() }")
                    appendLine("}")
                } else {
                    appendLine("/**")
                    appendLine(" * The hit/heal effects. Frame `i` (0 until [frameCount]) is held [holdMs]`(i)` ms; the last frame")
                    appendLine(" * is always empty (the clean end state). Draw with [FxRenderer]; play with [FxPlayer]. All data is in [spec].")
                    appendLine(" */")
                    appendLine("enum class Fx(val spec: FxSpec) {")
                    for (id in effects.keys) appendLine("    ${enumName(id)}(${dataName(id)}.SPEC),")
                    appendLine("    ;")
                    appendLine()
                    appendLine("    val id: String get() = spec.id")
                    appendLine("    val role: FxRole get() = spec.role")
                    appendLine("    /** Frame size in dither px, in the seat's upright frame. */")
                    appendLine("    val width: Int get() = spec.width")
                    appendLine("    val height: Int get() = spec.height")
                    appendLine("    val anchorRef: FxAnchorRef get() = spec.anchorRef")
                    appendLine("    val anchorDx: Int get() = spec.anchorDx")
                    appendLine("    val anchorDy: Int get() = spec.anchorDy")
                    appendLine("    /** Number of frames, including the final empty one. */")
                    appendLine("    val frameCount: Int get() = spec.frameCount")
                    appendLine("    /** Unscaled hold of [frame] in ms (0 for the last, empty frame). */")
                    appendLine("    fun holdMs(frame: Int): Int = spec.holdMs(frame)")
                    appendLine("    /** The black (`#`) layer of [frame]. */")
                    appendLine("    fun ink(frame: Int): FxLayer = spec.ink(frame)")
                    appendLine("    /** The knock-out (`o`, paper) layer of [frame]. */")
                    appendLine("    fun knockout(frame: Int): FxLayer = spec.knockout(frame)")
                    appendLine("    /** The cut (`x`) layer of [frame]. */")
                    appendLine("    fun cut(frame: Int): FxLayer = spec.cut(frame)")
                    appendLine("    /** The `tile` declarations (see [FxSpec.tiles]). */")
                    appendLine("    val tiles: List<FxTileSpec> get() = spec.tiles")
                    appendLine("    /** The layer of [frame] painted by `tiles[index]`. */")
                    appendLine("    fun tileLayer(index: Int, frame: Int): FxLayer = spec.tileLayer(index, frame)")
                    appendLine()
                    appendLine("    companion object {")
                    appendLine("        private val byId: Map<String, Fx> = entries.associateBy { it.id }")
                    appendLine()
                    appendLine("        /** Looks up an effect by its directory name, e.g. `\"pow\"`. */")
                    appendLine("        fun byName(name: String): Fx? = byId[name.lowercase()]")
                    appendLine()
                    appendLine("        /** Every effect with [role], in name order. */")
                    appendLine("        fun withRole(role: FxRole): List<Fx> = entries.filter { it.role == role }")
                    appendLine("    }")
                    appendLine("}")
                }
                fun layer(e: Effect, words: IntArray, indent: String) {
                    val parts = rle(words, e.w, e.h)
                    if (parts == null) appendLine("${indent}FxLayer.empty(${e.w}, ${e.h}),")
                    else appendLine("${indent}FxLayer.rle(${e.w}, ${e.h}, " + parts.joinToString(", ") { "\"$it\"" } + "),")
                }
                // One object per effect keeps each class initialiser small; pixel data lives in string
                // constants (constant pool), not in bytecode.
                for ((id, e) in effects) {
                    val role = "FxRole." + e.role.uppercase().replace('-', '_')
                    val ref = "FxAnchorRef." + e.ref.uppercase()
                    appendLine()
                    appendLine("private object ${dataName(id)} {")
                    appendLine("    @JvmField val SPEC = FxSpec(")
                    appendLine("        \"$id\", $role, ${e.w}, ${e.h}, $ref, ${e.dx}, ${e.dy},")
                    appendLine("        intArrayOf(${e.holds.joinToString(", ")}),")
                    if (e.tiles.isEmpty()) appendLine("        emptyList(),")
                    else appendLine("        listOf(" + e.tiles.joinToString(", ") { (d, t, m) -> "FxTileSpec($d, Patterns.byName(\"$t\")!!, FxTileMode.${m.uppercase()})" } + "),")
                    for (layers in listOf(e.ink, e.ko, e.cut)) {
                        appendLine("        arrayOf(")
                        for (words in layers) layer(e, words, "            ")
                        appendLine("        ),")
                    }
                    appendLine("        arrayOf(")
                    for ((d, _, _) in e.tiles) {
                        appendLine("            arrayOf( // tile $d")
                        for (frame in e.tileLayers) layer(e, frame.getValue(d), "                ")
                        appendLine("            ),")
                    }
                    appendLine("        ),")
                    appendLine("    )")
                    appendLine("}")
                }
            },
        )
    }

    /**
     * Run-length code of a packed layer, as decoded by `FxLayer.rle`: alternating unset/set runs over
     * the pixels in row-major order (starting unset, trailing unset run dropped), each a little-endian
     * varint of 5-bit groups ([RLE_ALPHABET] index ≥ 32 = more follow). Null when nothing is set.
     */
    private fun rle(words: IntArray, w: Int, h: Int): List<String>? {
        if (words.all { it == 0 }) return null
        val stride = (w + 31) / 32
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        fun emit(run: Int) {
            var v = run
            while (true) {
                val d = v and 31
                v = v ushr 5
                if (v == 0) { sb.append(RLE_ALPHABET[d]); break }
                sb.append(RLE_ALPHABET[32 + d])
            }
            if (sb.length >= RLE_PART) { parts += sb.toString(); sb.setLength(0) }
        }
        var cur = false
        var run = 0
        for (y in 0 until h) for (x in 0 until w) {
            val b = (words[y * stride + (x ushr 5)] ushr (31 - (x and 31))) and 1 == 1
            if (b != cur) { emit(run); run = 0; cur = b }
            run++
        }
        if (cur) emit(run)
        if (sb.isNotEmpty()) parts += sb.toString()
        return parts
    }

    private fun parseEffect(id: String, meta: File, files: List<File>, tileNames: Set<String>, errors: MutableList<String>, roleRequired: Boolean): Effect? {
        val vals = HashMap<String, List<String>>()
        val lineOf = HashMap<String, Int>()
        val tileLines = ArrayList<Pair<Int, List<String>>>() // (line index, values)
        meta.readText().replace("\r\n", "\n").split("\n").forEachIndexed { i, raw ->
            val l = raw.substringBefore('#').trim()
            if (l.isEmpty()) return@forEachIndexed
            val parts = l.split(Regex("\\s+"))
            val key = parts[0]
            when {
                key !in KEYS -> errors += "${meta.path}:${i + 1}: unknown key '$key' (${KEYS.joinToString(", ")})"
                key == "tile" -> tileLines += i to parts.drop(1) // repeatable: one line per digit
                key in vals -> errors += "${meta.path}:${i + 1}: duplicate '$key' (first at line ${lineOf[key]!! + 1})"
                else -> { vals[key] = parts.drop(1); lineOf[key] = i }
            }
        }
        fun err(key: String, msg: String) { errors += "${meta.path}:${(lineOf[key] ?: -1) + 1}: $msg" }
        fun ints(key: String): List<Int>? {
            val v = vals[key] ?: return null
            val n = v.map { it.toIntOrNull() }
            if (n.any { it == null }) { err(key, "'$key' values must be integers, got '${v.joinToString(" ")}'"); return null }
            return n.map { it!! }
        }
        for (k in listOf("size", "frames", "anchor", "hold_ms")) if (k !in vals) errors += "${meta.path}: missing '$k'"
        val size = ints("size") ?: return null
        if (size.size != 2 || size.any { it < 1 }) { err("size", "'size' needs 2 positive integers W H"); return null }
        val (w, h) = size
        val n = ints("frames")?.singleOrNull() ?: run { err("frames", "'frames' needs 1 integer"); return null }
        if (n < 2) { err("frames", "'frames' must be >= 2 (drawn frames + the final empty one)"); return null }
        val a = vals["anchor"] ?: return null
        val (ref, dxs, dys) = when (a.size) {
            3 -> Triple(a[0], a[1], a[2])
            2 -> Triple("centre", a[0], a[1])
            else -> { err("anchor", "'anchor' needs <ref> dx dy"); return null }
        }
        if (ref !in REFS) err("anchor", "anchor ref '$ref' must be one of ${REFS.joinToString("|")}")
        val dx = dxs.toIntOrNull()
        val dy = dys.toIntOrNull()
        if (dx == null || dy == null) { err("anchor", "anchor dx dy must be integers"); return null }
        val holds = ints("hold_ms") ?: return null
        if (holds.size != n) err("hold_ms", "'hold_ms' has ${holds.size} values, expected $n (frames)")
        else {
            if (holds.last() != 0) err("hold_ms", "last hold must be 0 (the empty end frame)")
            if (holds.dropLast(1).any { it <= 0 }) err("hold_ms", "holds before the last frame must be > 0")
        }
        for (k in listOf("ink_px", "knockout_px", "cut_px", "tile_px", "flips")) {
            val v = ints(k) ?: continue
            if (v.size != n) err(k, "'$k' has ${v.size} values, expected $n (frames)")
        }
        val role = vals["role"]?.let { r ->
            if (r.size != 1 || r[0] !in ROLES) { err("role", "'role' must be one of ${ROLES.joinToString("|")}"); null } else r[0]
        } ?: if (roleRequired) null else UNSET_ROLE
        if (role == null && "role" !in vals) errors += "${meta.path}: missing 'role' (one of ${ROLES.joinToString("|")})"
        val e = Effect(id, w, h, role ?: "", ref, dx, dy, holds) // a missing role is already an error
        // Tile layers: `tile <digit 1-9> <TILE> <ink|paper|fill>`, one line per digit.
        val tileLine = HashMap<Int, Int>()
        for ((i, v) in tileLines) {
            val at = "${meta.path}:${i + 1}"
            if (v.size != 3) { errors += "$at: 'tile' needs <digit> <TILE> <mode>, got '${v.joinToString(" ")}'"; continue }
            val d = v[0].singleOrNull()?.takeIf { it in '1'..'9' }?.digitToInt()
            if (d == null) { errors += "$at: tile digit must be 1-9, got '${v[0]}'"; continue }
            if (d in tileLine) { errors += "$at: duplicate 'tile $d' (first at line ${tileLine[d]!! + 1})"; continue }
            if (v[1] !in tileNames) { errors += "$at: unknown tile '${v[1]}' (known: ${tileNames.joinToString(" ")})"; continue }
            if (v[2] !in MODES) { errors += "$at: tile mode must be one of ${MODES.joinToString("|")}, got '${v[2]}'"; continue }
            tileLine[d] = i
            e.tiles += Triple(d, v[1], v[2])
        }
        e.tiles.sortBy { it.first }
        // Frames: exactly frame-1.txt … frame-N.txt.
        val frameRe = Regex("^frame-(\\d+)\\.txt$")
        val frameFiles = HashMap<Int, File>()
        for (f in files) {
            val m = frameRe.matchEntire(f.name) ?: continue
            val k = m.groupValues[1].toInt()
            if (k !in 1..n) errors += "${f.path}: unexpected frame file (meta says frames $n)"
            else frameFiles[k] = f
        }
        val stride = (w + 31) / 32
        val usedDigits = HashSet<Int>()
        for (k in 1..n) {
            val f = frameFiles[k]
            if (f == null) { errors += "${meta.parentFile.path}/frame-$k.txt: missing (meta says frames $n)"; continue }
            val lines = f.readText().replace("\r\n", "\n").removeSuffix("\n").split("\n")
            if (lines.size != h) errors += "${f.path}: expected $h lines (size $w $h), found ${lines.size}"
            val ink = IntArray(stride * h)
            val ko = IntArray(stride * h)
            val cut = IntArray(stride * h)
            val tl = e.tiles.associate { it.first to IntArray(stride * h) }
            val undeclared = HashSet<Char>()
            lines.take(h).forEachIndexed { y, line ->
                if (line.length != w) errors += "${f.path}:${y + 1}: expected $w chars (size $w $h), found ${line.length}"
                line.forEachIndexed { x, c ->
                    // Columns past the width are reported above and ignored here.
                    val i = y * stride + minOf(x, w - 1) / 32
                    val bit = if (x < w) 1 shl (31 - (x and 31)) else 0
                    when (c) {
                        '#' -> ink[i] = ink[i] or bit
                        'o' -> ko[i] = ko[i] or bit
                        'x' -> cut[i] = cut[i] or bit
                        '.' -> {}
                        in '1'..'9' -> {
                            val layer = tl[c.digitToInt()]
                            if (layer == null) {
                                if (undeclared.add(c)) errors += "${f.path}:${y + 1}:${x + 1}: tile digit '$c' has no 'tile $c <TILE> <mode>' line in ${meta.name}"
                            } else { layer[i] = layer[i] or bit; usedDigits += c.digitToInt() }
                        }
                        else -> errors += "${f.path}:${y + 1}:${x + 1}: invalid char '$c' (only '#' ink, 'o' knock-out, 'x' cut, '1'-'9' tile layers, '.' transparent)"
                    }
                }
            }
            val all = listOf(ink, ko, cut) + tl.values
            if (k == n && all.any { l -> l.any { it != 0 } }) errors += "${f.path}: the last frame must be empty (it is the clean end state)"
            e.ink += ink
            e.ko += ko
            e.cut += cut
            e.tileLayers += tl
            // Pixel counts are informational (author.py / author2.py); a stale count only warns.
            fun count(l: IntArray) = l.sumOf { Integer.bitCount(it) }
            val counts = mapOf("ink_px" to count(ink), "knockout_px" to count(ko), "cut_px" to count(cut), "tile_px" to tl.values.sumOf { count(it) })
            for ((key, px) in counts) {
                vals[key]?.getOrNull(k - 1)?.toIntOrNull()?.let { if (it != px) logger.warn("${meta.path}: $key for frame $k says $it, frame has $px") }
            }
        }
        for ((d, i) in tileLine) if (d !in usedDigits) logger.warn("${meta.path}:${i + 1}: 'tile $d' is never used by a frame")
        return e
    }
}

val fxDir = providers.gradleProperty("fxDir").getOrElse(rootProject.file("design/fx").path)
val fxOptionsDir = rootProject.file("design/fx/options")
// Only <dir>/<name>/meta.txt and frame-N.txt one level down; nested folders (options/…) never match.
fun fxSources(dir: Any) = fileTree(dir) { include("*/meta.txt", "*/frame-*.txt") }
val fxPatterns = fileTree(rootProject.file("design/patterns")) { include("*.txt"); exclude("INDEX.txt") }

val generateFx = tasks.register<GenerateFxTask>("generateFx") {
    // -PfxDir=<dir> lets the step be exercised against another folder (e.g. a malformed fixture).
    fxFiles.from(fxSources(fxDir))
    patternFiles.from(fxPatterns)
    target.set("enum")
    outputDir.set(layout.buildDirectory.dir("generated/source/fx"))
}

// design/fx/options/ (round-two candidates, T28d) → test-only `FxOptions`, so the engine is built and
// tested against every option without shipping any of them.
val generateFxOptions = tasks.register<GenerateFxTask>("generateFxOptions") {
    fxFiles.from(fxSources(fxOptionsDir))
    patternFiles.from(fxPatterns)
    target.set("options")
    outputDir.set(layout.buildDirectory.dir("generated/source/fxOptions"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateFx, GenerateFxTask::outputDir)
        (variant as? com.android.build.api.variant.HasUnitTest)?.unitTest?.sources?.kotlin
            ?.addGeneratedSourceDirectory(generateFxOptions, GenerateFxTask::outputDir)
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("dither.fxDir", file(fxDir).absolutePath)
    systemProperty("dither.fxOptionsDir", fxOptionsDir.absolutePath)
    inputs.files(fxSources(fxDir)).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fxSources(fxOptionsDir)).withPathSensitivity(PathSensitivity.RELATIVE)
}
