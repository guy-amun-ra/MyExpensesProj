package org.totschnig.myexpenses.sync

import org.totschnig.myexpenses.util.io.getFileExtension
import org.totschnig.myexpenses.util.io.getNameWithoutExtension
import java.io.IOException
import java.util.regex.Pattern

interface ShardingResourceStorage<Res> {
    companion object {
        val FILE_PATTERN: Pattern = Pattern.compile("_\\d+")
    }

    fun collectionForShard(shardNumber: Int): Res?

    /**
     * @param folder if null must return resources in account folder
     */
    fun childrenForCollection(folder: Res?): Collection<Res>

    fun nameForResource(resource: Res): String?

    fun isCollection(resource: Res): Boolean

    val extensionForData: String

    fun folderForShard(shardNumber: Int): String {
        check(shardNumber > 0)
        return "_$shardNumber"
    }

    fun getSequenceFromFileName(fileName: String?): Int {
        return fileName?.let {
            try {
                getNameWithoutExtension(fileName).takeIf { it.isNotEmpty() && it.startsWith("_") }
                    ?.substring(1)?.toInt()
            } catch (e: NumberFormatException) {
                null
            }
        } ?: 0
    }

    fun isNewerJsonFile(sequenceNumber: Int, name: String): Boolean {
        val fileName = getNameWithoutExtension(name)
        val fileExtension = getFileExtension(name)
        return fileExtension == extensionForData && FILE_PATTERN.matcher(fileName)
            .matches() && fileName.substring(1).toInt() > sequenceNumber
    }

    fun isAtLeastShardDir(shardNumber: Int, name: String): Boolean {
        return FILE_PATTERN.matcher(name).matches() &&
                name.substring(1).toInt() >= shardNumber
    }

    /**
     * return a list of pairs (shardNumber to resource) that were written after sequenceNumber
     */
    fun shardResolvingFilterStrategy(sequenceNumber: SequenceNumber): List<Pair<Int, Res>> =
        buildList {
            var nextShard = sequenceNumber.shard
            var startNumber = sequenceNumber.number
            while (true) {
                val nextShardResource = collectionForShard(nextShard)
                if (nextShardResource != null) {
                    childrenForCollection(nextShardResource)
                        .sortedBy { nameForResource(it)?.let { name -> getSequenceFromFileName(name) } }
                        .filter {
                            nameForResource(it)?.let { name ->
                                isNewerJsonFile(
                                    startNumber,
                                    name
                                )
                            } == true
                        }
                        .map { nextShard to it }
                        .forEach { add(it) }
                    nextShard++
                    startNumber = 0
                } else {
                    break
                }
            }
        }


    /**
     * calculates the last sequence written to this storage
     */
    @Throws(IOException::class)
    fun getLastSequence(start: SequenceNumber): SequenceNumber {
        val resourceComparator = Comparator<Res> { o1: Res, o2: Res ->
            getSequenceFromFileName(nameForResource(o1)).compareTo(
                getSequenceFromFileName(
                    nameForResource(o2)
                )
            )
        }
        val mainEntries = childrenForCollection(null)
        val lastShardOptional = mainEntries
            .filter { metadata ->
                isCollection(metadata) && nameForResource(metadata)?.let {
                    isAtLeastShardDir(
                        start.shard,
                        it
                    )
                } == true
            }
            .maxWithOrNull(resourceComparator)
        val lastShard: Collection<Res>
        val lastShardInt: Int
        val reference: Int
        if (lastShardOptional != null) {
            lastShard = childrenForCollection(lastShardOptional)
            lastShardInt = getSequenceFromFileName(nameForResource(lastShardOptional))
            reference = if (lastShardInt == start.shard) start.number else 0
        } else {
            if (start.shard > 0) return start
            lastShard = mainEntries
            lastShardInt = 0
            reference = start.number
        }
        return lastShard
            .filter {
                nameForResource(it)?.let { name ->
                    isNewerJsonFile(
                        reference,
                        name
                    )
                } == true
            }
            .maxWithOrNull(resourceComparator)
            ?.let {
                SequenceNumber(
                    lastShardInt,
                    getSequenceFromFileName(nameForResource(it))
                )
            } ?: start
    }
}