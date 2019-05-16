package net.daskelly45.fileeventshandler.core

import java.nio.file.Path
import java.nio.file.Paths

fun handleFileEventsFor(dirPath: String, config: FileSystemEventHandler.() -> Unit): FileSystemEventHandler =
    handleFileEventsFor(Paths.get(dirPath), config)

fun handleFileEventsFor(dirPath: Path, config: FileSystemEventHandler.() -> Unit): FileSystemEventHandler =
    FileSystemEventHandler(dirPath).apply(config)

typealias FileEventHandlerFunctionType = (fileFullPath: Path, fileName: String) -> Any


fun String.replaceSurrounding(oldPrefix:String, oldSuffix: String, newPrefix: String, newSuffix: String): String =
    this.removeSurrounding(oldPrefix, oldSuffix).surround(newPrefix, newSuffix)

fun String.replaceSurrounding(old: Pair<String, String>, new: Pair<String, String>): String =
    this.removeSurrounding(old.first, old.second).surround(new.first, new.second)

fun String.removeSurrounding(prefix: Char, suffix: Char) = this.removeSurrounding(prefix.toString(), suffix.toString())

fun String.surround(prefix: String, suffix: String) = "$prefix$this$suffix"

fun String.surround(prefix: Char, suffix: Char) = "$prefix$this$suffix"

fun String.surround(prefix: CharSequence, suffix: CharSequence) = "$prefix$this$suffix"
