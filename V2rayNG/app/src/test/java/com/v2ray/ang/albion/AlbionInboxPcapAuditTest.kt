package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipInputStream

class AlbionInboxPcapAuditTest {
    @Test fun analyzeEveryPcapInEveryInboxZipAndPersistReports() {
        val root = findRepoRoot()
        val inbox = root.resolve("research/inbox")
        val output = root.resolve("research/analysis")
        output.mkdirs()
        val archives = inbox.walkTopDown().filter { it.isFile && it.extension.equals("zip", true) }.toList()
        assertTrue(archives.isNotEmpty(), "No capture ZIP archives found in research/inbox")
        val temp = createTempDir(prefix = "albion-pcap-audit-")
        try {
            var totalPcaps = 0
            val index = mutableListOf<String>()
            for (archive in archives.sortedBy { it.name }) {
                val archiveDir = File(temp, archive.nameWithoutExtension).apply { mkdirs() }
                ZipInputStream(archive.inputStream().buffered()).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".pcap")) {
                            val target = File(archiveDir, File(entry.name).name)
                            target.outputStream().buffered().use { out -> zis.copyTo(out) }
                        }
                        zis.closeEntry()
                    }
                }
                val pcaps = archiveDir.walkTopDown().filter { it.isFile && it.extension.equals("pcap", true) }.toList()
                index += "## " + archive.name + " (" + pcaps.size + " PCAP files)"
                for (pcap in pcaps.sortedBy { it.name }) {
                    val report = try { AlbionCaptureAnalyzer.analyze(pcap) }
                    catch (e: Throwable) {
                        index += "### " + pcap.name + "\nANALYSIS_ERROR: " + e.javaClass.simpleName + ": " + e.message
                        continue
                    }
                    totalPcaps++
                    index += "### " + pcap.name + "\n" + report.asText()
                }
            }
            assertTrue(totalPcaps > 0, "ZIPs contained no readable .pcap files")
            File(output, "2026-11-02-all-inbox-pcap-analysis.md").writeText(
                "# Complete inbox PCAP analysis\n\nArchives: " + archives.size + "\nPCAP files analyzed: " + totalPcaps + "\n\n" + index.joinToString("\n\n"),
                Charsets.UTF_8
            )
        } finally { temp.deleteRecursively() }
    }

    private fun findRepoRoot(): File {
        var current = File(System.getProperty("user.dir")).canonicalFile
        repeat(8) {
            if (File(current, "research/inbox").isDirectory) return current
            current = current.parentFile ?: return@repeat
        }
        throw IllegalStateException("Could not locate repository research/inbox from " + System.getProperty("user.dir"))
    }
}
