package com.kinzer.data

import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.kinzer.data.local.LocalPiholeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Properties

data class SshKeyValidationResult(
    val valid: Boolean,
    val encrypted: Boolean = false,
    val message: String = ""
)

data class SshCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

data class RebootCheckResult(
    val needsReboot: Boolean,
    val ksta: Int,
    val rebootFilePresent: Boolean,
    val reason: String
)

data class NodeUpdateOutcome(
    val succeeded: Boolean,
    val rebooted: Boolean,
    val summary: String
)

/**
 * Real SSH client & zero-downtime rolling update orchestrator for Pi-hole servers.
 * Uses mwiede JSch (supports modern OpenSSH id_rsa / Ed25519 keys and rsa-sha2-512/256),
 * TOFU host key pinning, needrestart + /var/run/reboot-required detection, boot-ID
 * reboot verification, and Pi-hole API readiness verification before proceeding.
 */
class SshFleetUpdater(
    private val localRepository: LocalPiholeRepository,
    private val apiClient: PiholeApiClient
) {

    fun validatePrivateKey(
        privateKeyPem: String,
        passphrase: String = ""
    ): SshKeyValidationResult {
        val trimmed = privateKeyPem.trim()
        if (trimmed.isEmpty()) {
            return SshKeyValidationResult(false, message = "Private key is empty.")
        }
        if (!trimmed.contains("PRIVATE KEY")) {
            return SshKeyValidationResult(
                valid = false,
                message = "File does not look like an OpenSSH or PEM private key."
            )
        }
        return try {
            val jsch = JSch()
            val keyPair = KeyPair.load(
                jsch,
                trimmed.toByteArray(StandardCharsets.UTF_8),
                null
            )
            try {
                if (keyPair.isEncrypted) {
                    if (passphrase.isEmpty()) {
                        return SshKeyValidationResult(
                            valid = false,
                            encrypted = true,
                            message = "This key is passphrase-protected. Enter its passphrase below."
                        )
                    }
                    val decrypted = keyPair.decrypt(passphrase.toByteArray(StandardCharsets.UTF_8))
                    if (!decrypted) {
                        return SshKeyValidationResult(
                            valid = false,
                            encrypted = true,
                            message = "Incorrect passphrase for this SSH private key."
                        )
                    }
                }
                val typeName = when (keyPair.keyType) {
                    KeyPair.RSA -> "RSA"
                    KeyPair.ECDSA -> "ECDSA"
                    KeyPair.ED25519 -> "Ed25519"
                    KeyPair.ED448 -> "Ed448"
                    else -> "SSH"
                }
                SshKeyValidationResult(
                    valid = true,
                    encrypted = keyPair.isEncrypted,
                    message = "Valid $typeName private key ready"
                )
            } finally {
                keyPair.dispose()
            }
        } catch (e: Exception) {
            SshKeyValidationResult(
                valid = false,
                message = "Could not parse SSH key: ${e.message ?: "unsupported format"}"
            )
        }
    }

    suspend fun testConnection(
        device: PiholeDeviceProfile,
        username: String,
        port: Int,
        privateKeyPem: String,
        passphrase: String
    ): PiholeConnectionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val keyCheck = validatePrivateKey(privateKeyPem, passphrase)
        if (!keyCheck.valid) {
            return@withContext PiholeConnectionResult(connected = false, message = keyCheck.message)
        }
        var session: Session? = null
        try {
            val host = cleanHost(device.host)
            val (openedSession, _) = openSshSession(
                host = host,
                port = port,
                username = username.trim(),
                privateKeyPem = privateKeyPem,
                passphrase = passphrase,
                connectTimeoutMs = 10_000
            )
            session = openedSession
            val res = executeCommand(
                session = openedSession,
                command = "whoami && hostname",
                timeoutMs = 10_000
            )
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val lines = res.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val userOut = lines.getOrNull(0) ?: username.trim()
            val hostOut = lines.getOrNull(1) ?: host
            PiholeConnectionResult(
                connected = true,
                message = "SSH connected as $userOut@$hostOut in ${latency}ms"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PiholeConnectionResult(
                connected = false,
                message = humanizeSshError(e)
            )
        } finally {
            runCatching { session?.disconnect() }
        }
    }

    suspend fun updateSingleNode(
        device: PiholeDeviceProfile,
        apiSecret: String,
        config: SshFleetConfig,
        privateKeyPem: String,
        passphrase: String,
        onPhaseChange: (RollingUpdateNodePhase, String) -> Unit,
        onLogLine: (String) -> Unit
    ): NodeUpdateOutcome = withContext(Dispatchers.IO) {
        val host = cleanHost(device.host)
        var session: Session? = null
        try {
            // 1. Connect via SSH
            onPhaseChange(RollingUpdateNodePhase.CONNECTING, "${config.username}@$host:${config.port}")
            onLogLine("=== [${device.name}] Connecting to ${config.username}@$host:${config.port} ===")

            val (openedSession, pinnedNote) = openSshSession(
                host = host,
                port = config.port,
                username = config.username.trim(),
                privateKeyPem = privateKeyPem,
                passphrase = passphrase,
                connectTimeoutMs = 12_000
            )
            session = openedSession
            if (pinnedNote != null) {
                onLogLine("[SSH] $pinnedNote")
            }

            // Capture current Linux boot ID so we can prove a real reboot completed later
            val preBootRes = executeCommand(
                session = openedSession,
                command = "cat /proc/sys/kernel/random/boot_id 2>/dev/null || uptime -s 2>/dev/null || echo unknown",
                timeoutMs = 10_000
            )
            val preBootId = preBootRes.stdout.trim()
            onLogLine("[${device.name}] Connected (boot_id: ${preBootId.take(12)})")

            // 2. Run configured update script
            val scriptCmd = config.scriptPath.trim()
            onPhaseChange(RollingUpdateNodePhase.RUNNING_SCRIPT, scriptCmd)
            onLogLine("[${device.name}] Running: $scriptCmd")

            val scriptResult = executeCommand(
                session = openedSession,
                command = scriptCmd,
                timeoutMs = SCRIPT_TIMEOUT_MS,
                onOutputLine = { line -> onLogLine("  | $line") }
            )
            if (scriptResult.exitCode != 0) {
                val msg = "Update script exited with code ${scriptResult.exitCode}"
                onLogLine("[${device.name}] ERROR: $msg")
                onPhaseChange(RollingUpdateNodePhase.FAILED, msg)
                return@withContext NodeUpdateOutcome(
                    succeeded = false,
                    rebooted = false,
                    summary = msg
                )
            }
            onLogLine("[${device.name}] Script completed successfully (exit 0)")

            // 3. Check needrestart + /var/run/reboot-required
            onPhaseChange(RollingUpdateNodePhase.CHECKING_REBOOT, "Inspecting needrestart & reboot flag")
            onLogLine("[${device.name}] Checking needrestart & /var/run/reboot-required...")
            val rebootCheck = checkRebootRequired(openedSession)
            onLogLine(
                "[${device.name}] Reboot check: KSTA=${rebootCheck.ksta}, " +
                    "reboot-required=${if (rebootCheck.rebootFilePresent) "yes" else "no"} -> ${rebootCheck.reason}"
            )

            if (!rebootCheck.needsReboot) {
                // Disconnect SSH cleanly before verifying Pi-hole API health
                runCatching { openedSession.disconnect() }
                session = null

                onPhaseChange(RollingUpdateNodePhase.VERIFYING_DNS, "Confirming Pi-hole API health")
                onLogLine("[${device.name}] Disconnected SSH. Verifying Pi-hole DNS health...")
                val dnsHealthy = waitForPiholeHealth(
                    device = device,
                    apiSecret = apiSecret,
                    timeoutSeconds = 45,
                    onProgress = { elapsed ->
                        onPhaseChange(RollingUpdateNodePhase.VERIFYING_DNS, "Checking Pi-hole API (${elapsed}s)")
                    }
                )
                return@withContext if (dnsHealthy) {
                    onLogLine("[${device.name}] Complete — updated with no reboot required.")
                    onPhaseChange(RollingUpdateNodePhase.COMPLETED, "Updated (no reboot needed)")
                    NodeUpdateOutcome(
                        succeeded = true,
                        rebooted = false,
                        summary = "Updated without reboot"
                    )
                } else {
                    val err = "Pi-hole API did not respond healthy after update"
                    onLogLine("[${device.name}] ERROR: $err")
                    onPhaseChange(RollingUpdateNodePhase.FAILED, err)
                    NodeUpdateOutcome(
                        succeeded = false,
                        rebooted = false,
                        summary = err
                    )
                }
            }

            // 4. Reboot required -> trigger sudo reboot cleanly & disconnect
            onPhaseChange(RollingUpdateNodePhase.REBOOTING, "Triggering sudo reboot...")
            onLogLine("[${device.name}] Reboot required (${rebootCheck.reason}). Issuing sudo reboot...")
            triggerCleanReboot(openedSession)
            runCatching { openedSession.disconnect() }
            session = null
            onLogLine("[${device.name}] SSH disconnected. Waiting ${REBOOT_GRACE_SECONDS}s for shutdown...")

            for (sec in 1..REBOOT_GRACE_SECONDS) {
                currentCoroutineContext().ensureActive()
                onPhaseChange(
                    RollingUpdateNodePhase.REBOOTING,
                    "Shutting down (${sec}s / ${config.rebootTimeoutSeconds}s)"
                )
                delay(1_000L)
            }

            // 5. Wait until server comes back over SSH with a NEW boot_id
            val rebootStartMs = System.currentTimeMillis()
            val deadlineMs = rebootStartMs + (config.rebootTimeoutSeconds - REBOOT_GRACE_SECONDS).coerceAtLeast(45) * 1_000L
            var rebootConfirmed = false

            while (System.currentTimeMillis() < deadlineMs) {
                currentCoroutineContext().ensureActive()
                val totalElapsed = ((System.currentTimeMillis() - rebootStartMs) / 1_000L) + REBOOT_GRACE_SECONDS
                onPhaseChange(
                    RollingUpdateNodePhase.REBOOTING,
                    "Waiting for reboot (${totalElapsed}s / ${config.rebootTimeoutSeconds}s)"
                )

                val probeBootId = probeBootIdOverSsh(
                    host = host,
                    port = config.port,
                    username = config.username.trim(),
                    privateKeyPem = privateKeyPem,
                    passphrase = passphrase
                )
                if (probeBootId != null) {
                    if (preBootId.isBlank() || preBootId == "unknown" || probeBootId != preBootId) {
                        rebootConfirmed = true
                        onLogLine(
                            "[${device.name}] Server back online after ${totalElapsed}s " +
                                "(new boot_id: ${probeBootId.take(12)})"
                        )
                        break
                    } else {
                        onLogLine("[${device.name}] Host still shutting down (same boot_id)...")
                    }
                }
                delay(5_000L)
            }

            if (!rebootConfirmed) {
                val err = "Timed out after ${config.rebootTimeoutSeconds}s waiting for reboot"
                onLogLine("[${device.name}] ERROR: $err — halting before next server.")
                onPhaseChange(RollingUpdateNodePhase.FAILED, err)
                return@withContext NodeUpdateOutcome(
                    succeeded = false,
                    rebooted = true,
                    summary = err
                )
            }

            // 6. Confirm Pi-hole DNS API is back online before proceeding to next server
            onPhaseChange(RollingUpdateNodePhase.VERIFYING_DNS, "Waiting for Pi-hole FTL/API...")
            onLogLine("[${device.name}] Confirming Pi-hole API is responding...")
            val dnsBack = waitForPiholeHealth(
                device = device,
                apiSecret = apiSecret,
                timeoutSeconds = 90,
                onProgress = { elapsed ->
                    onPhaseChange(RollingUpdateNodePhase.VERIFYING_DNS, "Waiting for Pi-hole API (${elapsed}s)")
                }
            )
            if (!dnsBack) {
                val err = "Server rebooted, but Pi-hole API did not recover within 90s"
                onLogLine("[${device.name}] ERROR: $err — halting before next server.")
                onPhaseChange(RollingUpdateNodePhase.FAILED, err)
                return@withContext NodeUpdateOutcome(
                    succeeded = false,
                    rebooted = true,
                    summary = err
                )
            }

            onLogLine("[${device.name}] Reboot & Pi-hole recovery confirmed. Safe to proceed.")
            onPhaseChange(RollingUpdateNodePhase.COMPLETED, "Updated, rebooted & DNS healthy")
            NodeUpdateOutcome(
                succeeded = true,
                rebooted = true,
                summary = "Updated & rebooted cleanly"
            )
        } catch (e: CancellationException) {
            onLogLine("[${device.name}] Cancelled by user.")
            throw e
        } catch (e: Exception) {
            val msg = humanizeSshError(e)
            onLogLine("[${device.name}] ERROR: $msg")
            onPhaseChange(RollingUpdateNodePhase.FAILED, msg)
            NodeUpdateOutcome(
                succeeded = false,
                rebooted = false,
                summary = msg
            )
        } finally {
            runCatching { session?.disconnect() }
        }
    }

    private suspend fun checkRebootRequired(session: Session): RebootCheckResult {
        val cmd = """
            KSTA=${'$'}(sudo -n needrestart -k -b 2>/dev/null | awk '/^NEEDRESTART-KSTA:/ {print ${'$'}2}' | tr -d '\r\n')
            if [ -z "${'$'}KSTA" ]; then
              KSTA=${'$'}(sudo -n needrestart -b 2>/dev/null | awk '/^NEEDRESTART-KSTA:/ {print ${'$'}2}' | tr -d '\r\n')
            fi
            RF=0
            if [ -f /var/run/reboot-required ]; then RF=1; fi
            echo "PIPULSE_KSTA=${'$'}{KSTA:-0}"
            echo "PIPULSE_REBOOT_FILE=${'$'}RF"
        """.trimIndent()
        val res = executeCommand(session = session, command = cmd, timeoutMs = 30_000)
        var ksta = 0
        var rebootFile = false
        res.stdout.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("PIPULSE_KSTA=")) {
                ksta = trimmed.removePrefix("PIPULSE_KSTA=").trim().toIntOrNull() ?: 0
            } else if (trimmed.startsWith("PIPULSE_REBOOT_FILE=")) {
                rebootFile = trimmed.removePrefix("PIPULSE_REBOOT_FILE=").trim() == "1"
            }
        }
        val kernelNeedsReboot = ksta == 2 || ksta == 3
        val needsReboot = kernelNeedsReboot || rebootFile
        val reason = when {
            kernelNeedsReboot && rebootFile -> "Kernel update (KSTA=$ksta) & /var/run/reboot-required"
            kernelNeedsReboot -> "needrestart kernel update pending (KSTA=$ksta)"
            rebootFile -> "/var/run/reboot-required present"
            else -> "No reboot required"
        }
        return RebootCheckResult(
            needsReboot = needsReboot,
            ksta = ksta,
            rebootFilePresent = rebootFile,
            reason = reason
        )
    }

    private suspend fun triggerCleanReboot(session: Session) {
        // Schedule reboot 2s in the background so SSH closes cleanly without a broken-pipe error
        val res = runCatching {
            executeCommand(
                session = session,
                command = "sudo -n sh -c 'nohup sh -c \"sleep 2 && reboot\" >/dev/null 2>&1 &'",
                timeoutMs = 10_000
            )
        }.getOrNull()
        if (res == null || res.exitCode != 0) {
            runCatching {
                executeCommand(session = session, command = "sudo -n reboot", timeoutMs = 6_000)
            }
        }
    }

    private suspend fun probeBootIdOverSsh(
        host: String,
        port: Int,
        username: String,
        privateKeyPem: String,
        passphrase: String
    ): String? {
        var session: Session? = null
        return try {
            val (opened, _) = openSshSession(
                host = host,
                port = port,
                username = username,
                privateKeyPem = privateKeyPem,
                passphrase = passphrase,
                connectTimeoutMs = 5_000
            )
            session = opened
            val res = executeCommand(
                session = opened,
                command = "cat /proc/sys/kernel/random/boot_id 2>/dev/null || uptime -s 2>/dev/null",
                timeoutMs = 5_000
            )
            res.stdout.trim().takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            runCatching { session?.disconnect() }
        }
    }

    private suspend fun waitForPiholeHealth(
        device: PiholeDeviceProfile,
        apiSecret: String,
        timeoutSeconds: Int,
        onProgress: (Long) -> Unit
    ): Boolean {
        val startMs = System.currentTimeMillis()
        val deadlineMs = startMs + timeoutSeconds * 1_000L
        while (System.currentTimeMillis() < deadlineMs) {
            currentCoroutineContext().ensureActive()
            val elapsed = (System.currentTimeMillis() - startMs) / 1_000L
            onProgress(elapsed)
            val health = apiClient.fetchDeviceHealth(device, apiSecret)
            if (health.online) return true
            delay(3_000L)
        }
        return false
    }

    private suspend fun openSshSession(
        host: String,
        port: Int,
        username: String,
        privateKeyPem: String,
        passphrase: String,
        connectTimeoutMs: Int
    ): Pair<Session, String?> {
        val hostPortKey = "${host.lowercase()}_$port"
        val savedFp = localRepository.getTrustedHostKey(hostPortKey)
        var newlyPinnedFp: String? = null

        val jsch = JSch()
        val keyBytes = privateKeyPem.trim().toByteArray(StandardCharsets.UTF_8)
        val passBytes = passphrase.takeIf { it.isNotEmpty() }?.toByteArray(StandardCharsets.UTF_8)
        jsch.addIdentity("pipulse_fleet_key", keyBytes, null, passBytes)

        jsch.hostKeyRepository = object : HostKeyRepository {
            override fun check(hostArg: String?, key: ByteArray?): Int {
                if (key == null) return HostKeyRepository.NOT_INCLUDED
                val fp = sha256Fingerprint(key)
                return when {
                    savedFp == null -> {
                        newlyPinnedFp = fp
                        HostKeyRepository.OK
                    }
                    savedFp == fp -> HostKeyRepository.OK
                    else -> HostKeyRepository.CHANGED
                }
            }

            override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
            override fun remove(hostArg: String?, type: String?) = Unit
            override fun remove(hostArg: String?, type: String?, key: ByteArray?) = Unit
            override fun getKnownHostsRepositoryID(): String = "PiPulseTofuVault"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(hostArg: String?, type: String?): Array<HostKey> = emptyArray()
        }

        val session = jsch.getSession(username, host, port)
        val configProps = Properties().apply {
            put("StrictHostKeyChecking", "yes")
            put("PreferredAuthentications", "publickey")
            put("ServerAliveInterval", "15")
            put("ServerAliveCountMax", "3")
        }
        session.setConfig(configProps)
        session.timeout = 20_000
        session.connect(connectTimeoutMs)

        val note = newlyPinnedFp?.let { fp ->
            localRepository.saveTrustedHostKey(hostPortKey, fp)
            "Pinned host key for $host:$port ($fp)"
        }
        return session to note
    }

    private suspend fun executeCommand(
        session: Session,
        command: String,
        timeoutMs: Long,
        onOutputLine: ((String) -> Unit)? = null
    ): SshCommandResult = coroutineScope {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.setInputStream(null)
        val stdoutStream = channel.inputStream
        val stderrStream = channel.errStream
        channel.connect(10_000)

        val stdoutBuf = StringBuilder()
        val stderrBuf = StringBuilder()

        val stdoutJob = async(Dispatchers.IO) {
            BufferedReader(InputStreamReader(stdoutStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    stdoutBuf.appendLine(line)
                    onOutputLine?.invoke(line)
                }
            }
        }
        val stderrJob = async(Dispatchers.IO) {
            BufferedReader(InputStreamReader(stderrStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    stderrBuf.appendLine(line)
                    onOutputLine?.invoke("[err] $line")
                }
            }
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            while (!channel.isClosed) {
                currentCoroutineContext().ensureActive()
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect()
                    throw IllegalStateException("SSH command timed out after ${timeoutMs / 1000}s")
                }
                delay(150L)
            }
            stdoutJob.await()
            stderrJob.await()
            SshCommandResult(
                exitCode = channel.exitStatus,
                stdout = stdoutBuf.toString(),
                stderr = stderrBuf.toString()
            )
        } finally {
            stdoutJob.cancel()
            stderrJob.cancel()
            runCatching { channel.disconnect() }
        }
    }

    private fun sha256Fingerprint(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
    }

    private fun cleanHost(rawHost: String): String = rawHost.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .trimEnd('/')

    private fun humanizeSshError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("HostKey has been changed", ignoreCase = true) ->
                "SSH host key changed for this server. If you re-imaged the Pi, clear trusted host keys in SSH Config."
            msg.contains("Auth fail", ignoreCase = true) || msg.contains("Auth cancel", ignoreCase = true) ->
                "SSH authentication failed. Verify username, id_rsa key, and ~/.ssh/authorized_keys on the Pi."
            msg.contains("Connection refused", ignoreCase = true) ->
                "SSH connection refused. Verify sshd is running and port is correct."
            msg.contains("UnknownHostException", ignoreCase = true) || msg.contains("Unable to resolve", ignoreCase = true) ->
                "Cannot resolve host. Ensure you are on the local LAN or VPN."
            msg.contains("timeout", ignoreCase = true) ->
                "SSH connection timed out."
            msg.isNotBlank() -> msg
            else -> "SSH connection failed."
        }
    }

    companion object {
        private const val REBOOT_GRACE_SECONDS = 15
        private const val SCRIPT_TIMEOUT_MS = 10L * 60L * 1_000L // 10 min max for apt/pihole updates
    }
}
