package com.hotelapp.staff.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * Decodes both JSON string decimals (e.g. rust_decimal's `"125.00"`)
 * and JSON numeric values (e.g. `125.0`, `0`) into [BigDecimal].
 */
object FlexibleDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element !is JsonPrimitive) {
                throw SerializationException("Expected JSON primitive for decimal, got $element")
            }
            val raw = element.content.trim()
            if (raw.isEmpty() || raw == "null") {
                throw SerializationException("Unexpected empty or null decimal primitive")
            }
            return raw.toBigDecimalOrNull()
                ?: throw SerializationException("Invalid decimal value: '$raw'")
        }
        val raw = decoder.decodeString().trim()
        return raw.toBigDecimalOrNull()
            ?: throw SerializationException("Invalid decimal value: '$raw'")
    }
}
