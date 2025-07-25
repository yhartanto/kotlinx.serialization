/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.protobuf.internal

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@OptIn(ExperimentalSerializationApi::class)
internal abstract class ProtobufTaggedEncoder : ProtobufTaggedBase(), Encoder, CompositeEncoder {
    private enum class NullableMode {
        ACCEPTABLE,
        OPTIONAL,
        COLLECTION,
        LIST_ELEMENT,
        NOT_NULL
    }
    private var nullableMode: NullableMode = NullableMode.NOT_NULL

    protected abstract fun SerialDescriptor.getTag(index: Int): ProtoDesc

    protected abstract fun encodeTaggedInt(tag: ProtoDesc, value: Int)
    protected abstract fun encodeTaggedByte(tag: ProtoDesc, value: Byte)
    protected abstract fun encodeTaggedShort(tag: ProtoDesc, value: Short)
    protected abstract fun encodeTaggedLong(tag: ProtoDesc, value: Long)
    protected abstract fun encodeTaggedFloat(tag: ProtoDesc, value: Float)
    protected abstract fun encodeTaggedDouble(tag: ProtoDesc, value: Double)
    protected abstract fun encodeTaggedBoolean(tag: ProtoDesc, value: Boolean)
    protected abstract fun encodeTaggedChar(tag: ProtoDesc, value: Char)
    protected abstract fun encodeTaggedString(tag: ProtoDesc, value: String)
    protected abstract fun encodeTaggedEnum(tag: ProtoDesc, enumDescriptor: SerialDescriptor, ordinal: Int)

    protected open fun encodeTaggedInline(tag: ProtoDesc, inlineDescriptor: SerialDescriptor): Encoder = this.apply { pushTag(tag) }

    public final override fun encodeNull() {
        if (nullableMode != NullableMode.ACCEPTABLE) {
            @Suppress("REDUNDANT_ELSE_IN_WHEN")
            val message = when (nullableMode) {
                NullableMode.OPTIONAL -> "'null' is not supported for optional properties in ProtoBuf"
                NullableMode.COLLECTION -> "'null' is not supported as the value of collection types in ProtoBuf"
                NullableMode.LIST_ELEMENT -> "'null' is not supported as the value of a list element in ProtoBuf"
                NullableMode.NOT_NULL -> "'null' is not allowed for not-null properties"
                else -> "'null' is not supported in ProtoBuf";
            }
            throw SerializationException(message)
        }
    }

    public final override fun encodeBoolean(value: Boolean) {
        encodeTaggedBoolean(popTagOrDefault(), value)
    }

    public final override fun encodeByte(value: Byte) {
        encodeTaggedByte(popTagOrDefault(), value)
    }

    public final override fun encodeShort(value: Short) {
        encodeTaggedShort(popTagOrDefault(), value)
    }

    public final override fun encodeInt(value: Int) {
        encodeTaggedInt(popTagOrDefault(), value)
    }

    public final override fun encodeLong(value: Long) {
        encodeTaggedLong(popTagOrDefault(), value)
    }

    public final override fun encodeFloat(value: Float) {
        encodeTaggedFloat(popTagOrDefault(), value)
    }

    public final override fun encodeDouble(value: Double) {
        encodeTaggedDouble(popTagOrDefault(), value)
    }

    public final override fun encodeChar(value: Char) {
        encodeTaggedChar(popTagOrDefault(), value)
    }

    public final override fun encodeString(value: String) {
        encodeTaggedString(popTagOrDefault(), value)
    }

    public final override fun encodeEnum(
        enumDescriptor: SerialDescriptor,
        index: Int
    ): Unit = encodeTaggedEnum(popTagOrDefault(), enumDescriptor, index)


    public final override fun endStructure(descriptor: SerialDescriptor) {
        if (stackSize >= 0) {
            popTag()
        }
        endEncode(descriptor)
    }

    protected open fun endEncode(descriptor: SerialDescriptor) {}

    public final override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean): Unit =
        encodeTaggedBoolean(descriptor.getTag(index), value)

    public final override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte): Unit =
        encodeTaggedByte(descriptor.getTag(index), value)

    public final override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short): Unit =
        encodeTaggedShort(descriptor.getTag(index), value)

    public final override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int): Unit =
        encodeTaggedInt(descriptor.getTag(index), value)

    public final override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long): Unit =
        encodeTaggedLong(descriptor.getTag(index), value)

    public final override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float): Unit =
        encodeTaggedFloat(descriptor.getTag(index), value)

    public final override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double): Unit =
        encodeTaggedDouble(descriptor.getTag(index), value)

    public final override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char): Unit =
        encodeTaggedChar(descriptor.getTag(index), value)

    public final override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String): Unit =
        encodeTaggedString(descriptor.getTag(index), value)

    private fun SerialKind.isMapOrList() =
        this == StructureKind.MAP || this == StructureKind.LIST

    public final override fun <T : Any?> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        nullableMode =
            if (descriptor.isElementOptional(index))
                NullableMode.OPTIONAL
            else {
                val elementDescriptor = descriptor.getElementDescriptor(index)
                when {
                    !elementDescriptor.isNullable -> NullableMode.NOT_NULL
                    elementDescriptor.kind.isMapOrList() -> NullableMode.COLLECTION
                    descriptor.kind == StructureKind.LIST -> NullableMode.LIST_ELEMENT
                    else -> NullableMode.ACCEPTABLE
                }
            }

        pushTag(descriptor.getTag(index))
        encodeSerializableValue(serializer, value)
    }

    public final override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    ) {
        nullableMode = if (descriptor.isElementOptional(index))
            NullableMode.OPTIONAL
        else  {
            // we don't check nullability of element because this function called always for nullable `value`
            val elementDescriptor = descriptor.getElementDescriptor(index)
            when {
                elementDescriptor.kind.isMapOrList() -> NullableMode.COLLECTION
                descriptor.kind == StructureKind.LIST -> NullableMode.LIST_ELEMENT
                else -> NullableMode.ACCEPTABLE
            }
        }

        pushTag(descriptor.getTag(index))
        encodeNullableSerializableValue(serializer, value)
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        return encodeTaggedInline(popTag(), descriptor)
    }

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
        return encodeTaggedInline(descriptor.getTag(index), descriptor.getElementDescriptor(index))
    }
}
