package net.kpipes.core.store

import com.google.common.io.Files
import groovy.transform.CompileStatic

@CompileStatic
class FileSystemKeyValueStore {

    private final File parentDirectory

    FileSystemKeyValueStore(File parentDirectory) {
        this.parentDirectory = parentDirectory
    }

    void save(String collection, String key, byte[] value) {
        def targetFile = new File(parentDirectory, "${collection}/${key}")
        targetFile.parentFile.mkdirs()
        Files.write(value, targetFile)
    }

    boolean exists(String collection) {
        new File(parentDirectory, collection).exists()
    }

    byte[] read(String collection, String key) {
        def targetFile = new File(parentDirectory, "${collection}/${key}")
        if(!targetFile.exists()) {
            return null
        }
        Files.toByteArray(targetFile)
    }

    void remove(String collection, String key) {
        new File(parentDirectory, "${collection}/${key}").delete()
    }

    long count(String collection) {
        def collectionFiles = new File(parentDirectory, collection).list()
        if(collectionFiles == null) {
            0
        } else {
            collectionFiles.length
        }
    }

    Map<String, byte[]> all(String collection) {
        def result = [:]
        new File(parentDirectory, collection).listFiles().each { File file ->
            result[file.name] = Files.toByteArray(file)
        }
        result
    }

}