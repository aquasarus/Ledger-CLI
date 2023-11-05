package com.millspills.ledgercli.proto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object LedgerFileSerializer : Serializer<Ledger.LedgerFileProto> {
    override val defaultValue: Ledger.LedgerFileProto = Ledger.LedgerFileProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Ledger.LedgerFileProto {
        try {
            return Ledger.LedgerFileProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: Ledger.LedgerFileProto,
        output: OutputStream
    ) = t.writeTo(output)
}

object AliasSerializer : Serializer<Alias.AliasGroupsMap> {
    override val defaultValue: Alias.AliasGroupsMap = Alias.AliasGroupsMap.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Alias.AliasGroupsMap {
        try {
            return Alias.AliasGroupsMap.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: Alias.AliasGroupsMap,
        output: OutputStream
    ) = t.writeTo(output)
}
