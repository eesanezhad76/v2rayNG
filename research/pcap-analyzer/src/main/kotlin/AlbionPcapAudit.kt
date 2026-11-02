import com.v2ray.ang.albion.AlbionCaptureAnalyzer
import java.io.File
import java.util.zip.ZipInputStream

fun main() {
    val repo = File(System.getProperty("user.dir")).canonicalFile
    val inbox = File(repo, "research/inbox")
    val out = File(repo, "research/analysis").apply { mkdirs() }
    require(inbox.isDirectory) { "Missing research/inbox at " + inbox }
    val archives = inbox.walkTopDown().filter { it.isFile && it.extension.equals("zip", true) }.sortedBy { it.name }.toList()
    require(archives.isNotEmpty()) { "No ZIP archives found in research/inbox" }
    val temp = kotlin.io.path.createTempDirectory("albion-pcap-audit-").toFile()
    val index = mutableListOf<String>()
    var total = 0
    var failed = 0
    try {
        for (archive in archives) {
            val dir = File(temp, archive.nameWithoutExtension).apply { mkdirs() }
            var expanded = 0L
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".pcap")) {
                        val target = File(dir, File(entry.name).name)
                        target.outputStream().buffered().use { stream ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                expanded += n
                                require(expanded <= 1500000000L) { "Archive expands beyond 1.5 GB: " + archive.name }
                                stream.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val pcaps = dir.walkTopDown().filter { it.isFile && it.extension.equals("pcap", true) }.sortedBy { it.name }.toList()
            println("ARCHIVE " + archive.name + " PCAPS " + pcaps.size)
            index += "## " + archive.name + " (" + pcaps.size + " PCAP files)"
            for (pcap in pcaps) {
                total++
                val text = try {
                    val report = AlbionCaptureAnalyzer.analyze(pcap)
                    report.asText()
                } catch (e: Throwable) {
                    failed++
                    "ANALYSIS_ERROR: " + e.javaClass.simpleName + ": " + e.message
                }
                val safe = (archive.nameWithoutExtension + "--" + pcap.nameWithoutExtension)
                    .replace(Regex("[^A-Za-z0-9._-]+"), "_").take(110) + "-" + total.toString().padStart(2, '0')
                File(out, safe + "-pcap-report.md").writeText(
                    "# PCAP analysis: " + archive.name + " / " + pcap.name + "\n\n" + text,
                    Charsets.UTF_8
                )
                index += "### " + pcap.name + "\n" + text
                println("PCAP " + archive.name + " :: " + pcap.name + " :: " + text.lineSequence().take(9).joinToString(" | "))
            }
        }
        File(out, "2026-11-02-all-inbox-pcap-analysis.md").writeText(
            "# Full inbox PCAP analysis\n\nArchives: " + archives.size + "\nPCAP files: " + total + "\nAnalysis errors: " + failed + "\n\n" + index.joinToString("\n\n"),
            Charsets.UTF_8
        )
        println("PCAP_AUDIT_COMPLETE archives=" + archives.size + " pcaps=" + total + " errors=" + failed)
        require(total > 0) { "No PCAP files were analyzed" }
    } finally {
        temp.deleteRecursively()
    }
}
