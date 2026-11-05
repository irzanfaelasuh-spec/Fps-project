package com.novaforge.fpsplus.monitor

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Loaded by Shizuku as a UserService. The process runs with shell (UID 2000) or root
 * privileges, depending on how the user started Shizuku. Only fixed, read-only commands
 * needed for SurfaceFlinger frame timing are exposed through the AIDL interface.
 */
class FrameProbeService : IFrameProbe.Stub() {

    override fun getTopPackage(): String {
        val output = runCommand(arrayOf("dumpsys", "activity", "activities"))
        val componentPattern = Regex("([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+")
        val preferred = output.lineSequence().firstOrNull {
            it.contains("topResumedActivity") || it.contains("mResumedActivity") || it.contains("ResumedActivity")
        }
        return preferred?.let { componentPattern.find(it)?.groupValues?.getOrNull(1) }
            ?: componentPattern.find(output)?.groupValues?.getOrNull(1)
            ?: ""
    }

    override fun getLayerList(): String =
        runCommand(arrayOf("dumpsys", "SurfaceFlinger", "--list"))

    override fun getLatency(layerName: String): String {
        // The layer name is passed as one process argument; it is never evaluated by a shell.
        if (layerName.isBlank() || layerName.length > 512 || layerName.contains('\u0000')) return ""
        return runCommand(arrayOf("dumpsys", "SurfaceFlinger", "--latency", layerName))
    }

    override fun getThermalDump(): String =
        runCommand(arrayOf("dumpsys", "thermalservice"))

    private fun runCommand(command: Array<String>): String {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val bytes = ByteArrayOutputStream()
            // Drain stdout on a separate thread so a full pipe cannot prevent the timeout check.
            val reader = Thread {
                try {
                    process.inputStream.use { input ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            if (bytes.size() < 1_000_000) bytes.write(buffer, 0, count)
                        }
                    }
                } catch (_: Throwable) { }
            }.apply { isDaemon = true; name = "FPSPlus-command-reader"; start() }

            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroy()
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                reader.join(500)
                return ""
            }
            reader.join(750)
            bytes.toString(Charsets.UTF_8.name())
        } catch (_: Throwable) {
            ""
        }
    }

    override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
        // Shizuku's UserService destroy transaction. Cleanly terminate the remote process.
        if (code == 16777115) {
            reply?.writeNoException()
            Thread { android.os.Process.killProcess(android.os.Process.myPid()) }.start()
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }
}
