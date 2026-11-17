package com.picacompose.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Android / Desktop 共用的下载存储：两边都是 java.io，打包用 java.util.zip。
 *
 * 所有写操作都走「先写临时文件、再改名」这条路 —— 手机被冻、进程被杀、甚至断电，
 * 目录里最多多出一个 `.part` / `.tmp`，绝不会有一份看起来完整、实际缺了半截的图片或 zip。
 */
internal class JvmDownloadStore(private val rootDir: File) : DownloadStore {

    override val supported: Boolean get() = true

    override val root: String get() = rootDir.absolutePath

    override fun exists(path: String): Boolean = File(path).exists()

    override fun mkdirs(path: String) {
        val dir = File(path)
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
            throw IllegalStateException("建目录失败：$path")
        }
    }

    override fun list(path: String): List<String> = File(path).list()?.toList().orEmpty()

    override fun delete(path: String) {
        File(path).delete()
    }

    override fun deleteTree(path: String) {
        File(path).deleteRecursively()
    }

    override fun readText(path: String): String? =
        runCatching { File(path).takeIf { it.isFile }?.readText() }.getOrNull()

    override fun writeTextAtomic(path: String, text: String) {
        writeBytesAtomic(path, text.toByteArray(Charsets.UTF_8))
    }

    override fun writeBytesAtomic(path: String, bytes: ByteArray) {
        val target = File(path)
        target.parentFile?.mkdirs()
        val part = File(target.parentFile ?: File("."), target.name + ".part")
        // 上一回崩在半路留下的 .part 直接盖掉：那本来就不算数。
        part.outputStream().use { out ->
            out.write(bytes)
            out.flush()
        }
        if (target.exists() && !target.delete()) throw IllegalStateException("删旧文件失败：$path")
        if (!part.renameTo(target)) throw IllegalStateException("改名失败：$path")
    }

    override fun size(path: String): Long = File(path).takeIf { it.isFile }?.length() ?: 0L

    override fun zipDir(dirPath: String, zipPath: String): Boolean {
        return runCatching {
            val dir = File(dirPath)
            if (!dir.isDirectory) return@runCatching false
            val out = File(zipPath)
            out.parentFile?.mkdirs()
            val tmp = File(out.parentFile ?: File("."), out.name + ".tmp")
            tmp.delete()
            BufferedOutputStream(FileOutputStream(tmp), 64 * 1024).use { buffered ->
                ZipOutputStream(buffered).use { zos ->
                    // 图片本来就被压过一遍，打包不再压，省 CPU 也快。
                    zos.setLevel(Deflater.NO_COMPRESSION)
                    val entries = dir.walkTopDown()
                        .filter { it.isFile }
                        .map { it.relativeTo(dir) }
                        .sortedBy { it.invariantSeparatorsPath }
                        .toList()
                    for (relative in entries) {
                        val name = relative.invariantSeparatorsPath
                        val entry = ZipEntry(name)
                        val f = File(dir, name)
                        entry.time = f.lastModified()
                        zos.putNextEntry(entry)
                        f.inputStream().use { it.copyTo(zos, 64 * 1024) }
                        zos.closeEntry()
                    }
                }
            }
            // 旧包（理论上不会有）先删，再原子改名。
            out.delete()
            if (!tmp.renameTo(out)) return@runCatching false
            true
        }.getOrElse {
            runCatching { File(zipPath + ".tmp").delete() }
            false
        }
    }
}
