package dev.cfmobile.app.data.remote

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/**
 * Cloudflare page-rule / setting values can be a string, number, boolean, object or array
 * depending on the field. Moshi has no built-in adapter for [Any], so this fills that gap
 * for the handful of DTOs that model "value" as a dynamic JSON type.
 */
class AnyJsonAdapter : JsonAdapter<Any>() {

    override fun fromJson(reader: JsonReader): Any? {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> {
                val list = mutableListOf<Any?>()
                reader.beginArray()
                while (reader.hasNext()) list.add(fromJson(reader))
                reader.endArray()
                list
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                val map = mutableMapOf<String, Any?>()
                reader.beginObject()
                while (reader.hasNext()) map[reader.nextName()] = fromJson(reader)
                reader.endObject()
                map
            }
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> {
                val asString = reader.nextString()
                asString.toLongOrNull() ?: asString.toDoubleOrNull() ?: asString
            }
            JsonReader.Token.BOOLEAN -> reader.nextBoolean()
            JsonReader.Token.NULL -> reader.nextNull()
            else -> throw JsonDataException("Unexpected token ${reader.peek()} at ${reader.path}")
        }
    }

    override fun toJson(writer: JsonWriter, value: Any?) {
        when (value) {
            null -> writer.nullValue()
            is String -> writer.value(value)
            is Boolean -> writer.value(value)
            is Number -> writer.value(value)
            is Map<*, *> -> {
                writer.beginObject()
                for ((k, v) in value) {
                    writer.name(k.toString())
                    toJson(writer, v)
                }
                writer.endObject()
            }
            is List<*> -> {
                writer.beginArray()
                for (v in value) toJson(writer, v)
                writer.endArray()
            }
            else -> throw JsonDataException("Unsupported type ${value::class.java}")
        }
    }
}
