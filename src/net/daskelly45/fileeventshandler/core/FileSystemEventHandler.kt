package net.daskelly45.fileeventshandler.core

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.logging.Logger

class FileSystemEventHandler(baseDirectoryPath: Path) {
    val baseDirectoryPath: Path

    private val logger: Logger = Logger.getLogger(FileSystemEventHandler::class.java.name)

    private var filesWatchedForModification: Array<String> =  EMPTY_STRING_ARRAY
        set(value) {
            if(value.filter { it.isNotBlank() }.toTypedArray().contentDeepEquals(EMPTY_STRING_ARRAY))
                handledEvents.remove(MODIFIED)
            else {
                try {
                    pathMatcherForModificationEvent = buildPathMatcher(value)
                    handledEvents.add(MODIFIED)
                    field = value
                } catch(e: Exception) {

                }
            }
        }

    private var filesWatchedForCreation: Array<String> = EMPTY_STRING_ARRAY
        set(value) {
            if(value.filter { it.isNotBlank() }.toTypedArray().contentDeepEquals(EMPTY_STRING_ARRAY))
                handledEvents.remove(StandardWatchEventKinds.ENTRY_CREATE)
            else {
                try {
                    pathMatcherForCreationEvent = buildPathMatcher(value)
                    handledEvents.add(StandardWatchEventKinds.ENTRY_CREATE)
                    field = value
                } catch(e: Exception) {

                }
            }
        }

    private var filesWatchedForDeletion: Array<String> =  EMPTY_STRING_ARRAY
        set(value) {
            if(value.filter { it.isNotBlank() }.toTypedArray().contentDeepEquals(EMPTY_STRING_ARRAY))
                handledEvents.remove(StandardWatchEventKinds.ENTRY_DELETE)
            else {
                try {
                    pathMatcherForDeletionEvent = buildPathMatcher(value)
                    handledEvents.add(StandardWatchEventKinds.ENTRY_DELETE)
                    field = value
                } catch(e: Exception) {

                }
            }
        }


    private val handledEvents = mutableSetOf<WatchEvent.Kind<*/*Path*/>>()

    var useBuiltInMatcher = true
    var isRecursive = false
    var registerNewDirectories = true
    var customPathMatcher = "glob:**{*.htm,*html}"
    var pathMatcherSyntax = "glob"
    var ignoredFolders: Array<String> = emptyArray()
    private val watchKeyToWatchedPath = hashMapOf<WatchKey, Path>()
//    private val watchedPathToWatchKey = hashMapOf<WatchKey, Path>()
//    private lateinit var pathMatcher: PathMatcher
    private lateinit var pathMatcherForModificationEvent: PathMatcher
    private lateinit var pathMatcherForCreationEvent: PathMatcher
    private lateinit var pathMatcherForDeletionEvent: PathMatcher
    var isDebugEnabled = false


    private val _onModified: FileEventHandlerFunctionType = { fileFullPath, fileName ->
        onModified.invoke(fileFullPath, fileName)
    }
    private var onModified: FileEventHandlerFunctionType = { _, _ -> }

    private val _onCreated: FileEventHandlerFunctionType = { fileFullPath, fileName ->
        if (Files.isDirectory(fileFullPath)) {
            if(registerNewDirectories)
                registerTree(fileFullPath)
        }
        onCreated.invoke(fileFullPath, fileName)
    }
    private var onCreated: FileEventHandlerFunctionType = { _, _ -> }

    private val _onDeleted: FileEventHandlerFunctionType = { fileFullPath, fileName ->
        try {
            if(Files.isDirectory(fileFullPath)){
                val key = watchKeyToWatchedPath.filterValues { it == fileFullPath }.toList().first().first
                removeWatchKey(key)
            }
        } catch(e: Exception) {

        }

        onDeleted.invoke(fileFullPath, fileName)
    }
    private var onDeleted: FileEventHandlerFunctionType = { _, _ -> }

    private val _onInterruptCallback: (reason: InterruptedException) -> Unit = { reason ->
        onInterruptCallback.invoke(reason)
    }
    private var onInterruptCallback: (reason: InterruptedException) -> Unit = {}

    private val _onOverflowCallback: () -> Unit = {
        onOverflowCallback.invoke()
    }
    private var onOverflowCallback: () -> Unit = {}

    private val _onInaccessibleDirectoryCallback: (parentFolderPath: Path, watchKey: WatchKey) -> Unit = { parentFolderPath, watchKey  ->
        if(Files.isDirectory(parentFolderPath))
            removeWatchKey(watchKey)
        onInaccessibleDirectoryCallback.invoke(parentFolderPath)
    }
    private var onInaccessibleDirectoryCallback: (parentFolderPath: Path) -> Unit = {}

    private val _onDirRegistrationErrorCallback: (dir: Path, e: Exception /*IOException*/) -> Unit  = { dir, e ->
        onDirRegistrationErrorCallback.invoke(dir, e)
    }
    private var onDirRegistrationErrorCallback: (dir: Path, e: Exception /*IOException*/) -> Unit = { _, _ -> }

    private val _onElseCallback: () -> Unit = {
        onElseCallback.invoke()
    }
    private var onElseCallback: () -> Unit = {}

    private val watchService: WatchService = baseDirectoryPath.fileSystem.newWatchService()
    private lateinit var watchKey: WatchKey

    private fun info(msg: String) {
        println(msg)
        // logger.log(Level.INFO, msg)
    }

    fun onOverflow(block: () -> Unit) {
        onOverflowCallback = block
    }

    fun onInterrupt(block: (reason: InterruptedException) -> Unit) {
        onInterruptCallback = block
    }

    fun onInaccessibleDirectory(block: (parentFolderPath: Path) -> Unit) {
        onInaccessibleDirectoryCallback = block
    }

    fun onDirRegistrationError(block: (dir: Path, e: Exception) -> Unit) {
        onDirRegistrationErrorCallback = block
    }

    fun onElse(block: () -> Unit) {
        onElseCallback = block
    }


    @JvmOverloads fun onFileModified(fileNames: Array<String> = ANY_KIND_OF_FILES, block: FileEventHandlerFunctionType) {
        filesWatchedForModification = fileNames
        onModified = block
    }

    @JvmOverloads fun onFileCreated(fileNames: Array<String> = ANY_KIND_OF_FILES, block: FileEventHandlerFunctionType) {
        filesWatchedForCreation = fileNames
        onCreated = block
    }

    @JvmOverloads fun onFileDeleted(fileNames: Array<String> = ANY_KIND_OF_FILES, block: FileEventHandlerFunctionType) {
        filesWatchedForDeletion = fileNames
        onDeleted = block
    }

    @JvmOverloads fun onAnyFileEvent(fileNames: Array<String> = ANY_KIND_OF_FILES, block: FileEventHandlerFunctionType) {
        onFileModified(fileNames, block)
        onFileCreated(fileNames, block)
        onFileDeleted(fileNames, block)
    }


    private fun buildPathMatcher(filesPattern: Array<String> = ANY_KIND_OF_FILES): PathMatcher {
        val syntaxAndPattern: String
        val sep = File.separator
        val filePatterns = filesPattern.contentToString().replaceSurrounding("[" to "]", "{" to "}").trim()
        val recursionToggle = if(isRecursive) "**${""/*sep*/}" else "" // ${""/**sep*/}

        syntaxAndPattern = if(useBuiltInMatcher) {
            "$pathMatcherSyntax:$baseDirectoryPath$sep$recursionToggle$filePatterns" //sample = "glob:/home/dave45/Bureau/**/{*.htm,*.html}
        } else {
            customPathMatcher
        }

        if(isDebugEnabled)
            info("syntaxAndPattern = \"$syntaxAndPattern\", filesPattern = \"$filesPattern\"")

        return try {
            baseDirectoryPath.fileSystem.getPathMatcher(syntaxAndPattern)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun registerDir(dir: Path, vararg events: WatchEvent.Kind<*> = handledEvents.toTypedArray()) {
        try {
            if(dir.fileName.toString() !in ignoredFolders) {
                val key = dir.register(watchService, events)
                watchKeyToWatchedPath[key] = dir
            }
        } catch (e: IOException) {
            _onDirRegistrationErrorCallback.invoke(dir, e)
            System.err.println(e)
        }
    }

    private fun removeWatchKey(watchKey: WatchKey) {
        watchKeyToWatchedPath.remove(watchKey)
        if(watchKeyToWatchedPath.isEmpty())
            stopWatching()
    }

    private fun registerTree(dir: Path) {
        if(isRecursive) {
            Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if(dir != null)
                        registerDir(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            registerDir(dir)
        }
        // pathMatcher = buildPathMatcher()
    }

    private fun checkFilePathMatchesPatternAndInvokeFunction(
        fileFullPath: Path,
        onEvent: FileEventHandlerFunctionType,
        pathMatcher: PathMatcher) {

        if (pathMatcher.matches(fileFullPath)) {
            if(isDebugEnabled)
                info("fileFullPath = $fileFullPath DOES match with is pathMatcher: $pathMatcher")
            onEvent.invoke(fileFullPath, fileFullPath.fileName.toString())
        } else {
            if(isDebugEnabled)
                info("fileFullPath = $fileFullPath doesnt match with is pathMatcher: $pathMatcher")
        }
    }

    fun startWatching() {
        info("Handled events: $handledEvents")
        try {
            watchKey
        } catch (e: UninitializedPropertyAccessException) {
            // info("It worked: It would have thrown an error cause of non initialisation of watchKey")
            registerTree(baseDirectoryPath)
            // registerWatchKey(*handledEvents.toTypedArray())
        }

        if(isDebugEnabled) {
            info("filesWatchedForModification = $filesWatchedForModification, pathMatcherForModificationEvent = $pathMatcherForModificationEvent.")
            info("filesWatchedForDeletion = $filesWatchedForDeletion, pathMatcherForDeletionEvent = $pathMatcherForDeletionEvent")
            info("filesWatchedForCreation = $filesWatchedForCreation, pathMatcherForCreationEvent = $pathMatcherForCreationEvent")
//            info("patternsOfFilesToWatch = $patternsOfFilesToWatch, pathMatcher = $pathMatcher")
        }


        watchingLoop@ while (true)  {
            try {
                watchKey = watchService.take() // wait for the key to be signaled
            } catch (x: InterruptedException) {
                _onInterruptCallback.invoke(x)
                return
            }

            val polledEvents = watchKey.pollEvents()
            val parentPath = watchKey.watchable() as Path

            info("polledEvents.size = ${polledEvents.size}")

            for (event in polledEvents) {
                val kind = event.kind()

                event as WatchEvent<Path>
                val filePath = event.context()
                val fileFullPath = parentPath.resolve(filePath)
                // val fileName = filePath.toString() // the filename is the context of the event

                info("event.count() = ${event.count()}")

                if(isDebugEnabled) {
                    info("polledEvents.size = ${polledEvents.size}")
                    info("event = $event")
                    info("event.count() = ${event.count()}")
                    info("event.context().toAbsolutePath()  = ${event.context().toAbsolutePath()}")
                    info("parentPath = $parentPath")
                    info("goodFilePath = ${parentPath.resolve(event.context())}")
                    try {
                        if(Files.exists(event.context())){
                            info("event.context().toRealPath() = ${event.context().toRealPath()}")
                        }
                        info("event.context().toFile().canonicalPath = ${event.context().toFile().readText()}")
                        info("event.context().relativize(baseDirectoryPath) = ${baseDirectoryPath.relativize(event.context().toAbsolutePath())}")
                    } catch(e: Exception) {

                    }
                    info("event.kind() = $kind")
                }

                when(kind) {
                    CREATED -> {
                        checkFilePathMatchesPatternAndInvokeFunction(
                            fileFullPath,
                            _onCreated,
                            pathMatcherForCreationEvent
                        )
                    }
                    MODIFIED -> {
                        checkFilePathMatchesPatternAndInvokeFunction(
                            fileFullPath,
                            _onModified,
                            pathMatcherForModificationEvent
                        )
                    }
                    DELETED -> {
                        checkFilePathMatchesPatternAndInvokeFunction(
                            fileFullPath,
                            _onDeleted,
                            pathMatcherForDeletionEvent
                        )
                    }
                    OVERFLOW -> {
                        _onOverflowCallback.invoke()
                        continue@watchingLoop
                    }
                    else -> {
                        _onElseCallback.invoke()
                        continue@watchingLoop
                    }
                }
            }

            // Reset the key -- this step is critical if you want to
            // receive further watch events.  If the key is no longer valid,
            // the directory is inaccessible so exit the loop.
            val valid = watchKey.reset() // return true if the watch key is still valid
            if (!valid) {
                _onInaccessibleDirectoryCallback.invoke(parentPath, watchKey)
                if(watchKeyToWatchedPath.isEmpty())
                    break
            }
        }
    }

//    fun pauseWatching() {
//
//    }

    fun stopWatching() {
        try {
            for(key in watchKeyToWatchedPath.keys) {
                if(key.isValid) {
                    key.cancel()
                }
            }
            watchKeyToWatchedPath.clear()
        } catch (e: Exception) {
            throw e
        }
    }

    companion object {
//        private var pathOrFileOrRawString = "path"

        @JvmStatic val MODIFIED: WatchEvent.Kind<Path> = StandardWatchEventKinds.ENTRY_MODIFY
        @JvmStatic val CREATED: WatchEvent.Kind<Path> = StandardWatchEventKinds.ENTRY_CREATE
        @JvmStatic val DELETED: WatchEvent.Kind<Path> = StandardWatchEventKinds.ENTRY_DELETE
        @JvmStatic val OVERFLOW: WatchEvent.Kind<*> = StandardWatchEventKinds.OVERFLOW
        @JvmStatic val EMPTY_STRING_ARRAY = emptyArray<String>()
        @JvmStatic val ANY_KIND_OF_FILES = arrayOf("*")

        @JvmStatic fun filesWhichNameEndsWith(extension: String) = "*.$extension"

        @JvmStatic fun filesWhichNameStartsWith(start: String) = "$start*"

        @JvmStatic fun filesWhichNameContains(expr: String) = "*$expr*"

        @JvmOverloads
        @JvmStatic
        fun watch(path: Path, config: FileSystemEventHandler.() -> Unit = {}): FileSystemEventHandler {
            // return FileSystemEventHandler(path).apply(config)
            return if(Files.exists(path)) {
                if(Files.isDirectory(path)){
                    FileSystemEventHandler(path).apply(config)
                } else {
                    val parentPath = path.parent
                    if(Files.exists(parentPath)) {
                        if(Files.isDirectory(parentPath)) {
                            FileSystemEventHandler(parentPath).apply {
                                this.config()
                            }
                        } else  {
                            throw FileNotFoundException("The actual parent of the given file is not a folder or is an invalid one. Please provide a file that has a valid parent folder.")
                        }
                    } else {
                        throw FileNotFoundException("The parent folder of the given file $parentPath does not exists.")
                    }
                }
            } else {
                throw FileNotFoundException("The given path: \"$path\" does not exist.")
            }
        }

        @JvmOverloads
        @JvmStatic
        fun watch(file: File, config: FileSystemEventHandler.() -> Unit = {}): FileSystemEventHandler {
            return watch(file.toPath(), config)
        }

        @JvmOverloads
        @JvmStatic
        fun watch(pathStr: String, config: FileSystemEventHandler.() -> Unit = {}): FileSystemEventHandler {
            return watch(Paths.get(pathStr), config)
        }
    }

    init {
        if(Files.exists(baseDirectoryPath)) {
            if(Files.isDirectory(baseDirectoryPath)){
                this.baseDirectoryPath = baseDirectoryPath
            } else {
                val parentPath = baseDirectoryPath.parent
                if(Files.exists(parentPath)) {
                    if(Files.isDirectory(parentPath)) {
                        this.baseDirectoryPath = baseDirectoryPath
                    } else  {
                        throw FileNotFoundException("The actual parent of the given file is not a folder or is an invalid one. Please provide a file that has a valid parent folder.")
                    }
                } else {
                    throw FileNotFoundException("The parent folder of the given file $parentPath does not exists.")
                }
            }
        } else {
            throw FileNotFoundException("The given path: \"$baseDirectoryPath\" does not exist.")
        }
    }
}