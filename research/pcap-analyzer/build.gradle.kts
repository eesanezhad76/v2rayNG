plugins {
    kotlin("jvm") version "2.4.21"
    application
}
dependencies { implementation("org.json:json:20240303") }
kotlin { jvmToolchain(17) }
sourceSets {
    main {
        kotlin.srcDir("../../V2rayNG/app/src/main/java")
        kotlin.include(
            "com/v2ray/ang/albion/AlbionCaptureAnalyzer.kt",
            "com/v2ray/ang/albion/AlbionCaptureMarker.kt",
            "com/v2ray/ang/albion/AlbionPhotonPacketDecoder.kt",
            "com/v2ray/ang/albion/AlbionProtocol18Decoder.kt",
            "com/v2ray/ang/albion/AlbionEntityEventMapper.kt",
            "com/v2ray/ang/albion/AlbionPhotonEventDecoder.kt",
            "AlbionPcapAudit.kt"
        )
    }
}
application { mainClass.set("AlbionPcapAuditKt") }
tasks.register<JavaExec>("auditInbox") {
    group = "verification"
    description = "Run the Android app's passive Photon/Albion decoders against every inbox PCAP."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("AlbionPcapAuditKt")
    workingDir = projectDir.parentFile.parentFile
}
