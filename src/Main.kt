import net.daskelly45.fileeventshandler.core.FileSystemEventHandler
import java.io.File

fun main() {

    FileSystemEventHandler.watch("/home/dave45/Bureau") {
        useBuiltInMatcher = true
        isRecursive = true
        registerNewDirectories = true
        ignoredFolders = arrayOf("node_modules")


        onFileCreated(FileSystemEventHandler.ANY_KIND_OF_FILES) { fileFullPath, _ ->
            File("")
            println("file \"$fileFullPath\" was just created")
        }
        onFileModified(FileSystemEventHandler.ANY_KIND_OF_FILES) { fileFullPath, _ ->
            println("file \"$fileFullPath\" was just modified")
        }
        onFileDeleted(FileSystemEventHandler.ANY_KIND_OF_FILES) { fileFullPath, _ ->
            println("file \"$fileFullPath\" was just deleted")
        }

        onInterrupt {
            println(it.message)
        }

        onInaccessibleDirectory {
            println("$it just become inaccessible")
        }

        onOverflow {
            println("=== OVERFLOW ===")
        }

        startWatching()
    }
    readLine()
}