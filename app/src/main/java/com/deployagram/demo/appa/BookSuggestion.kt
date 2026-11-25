package com.deployagram.demo.appa

import org.json.JSONObject

/** A book that BookOverflow suggests, as returned by GET /suggestion?email=... */
data class BookSuggestion(
    val name: String,
    val edition: String,
    val format: String,
    val authors: List<String>,
) {
    fun describe(): String = "$name ($edition edition, $format)\nby ${authors.joinToString(", ")}"

    companion object {
        fun fromJson(json: String): BookSuggestion {
            val obj = JSONObject(json)
            val authorsJson = obj.getJSONArray("authors")
            val authors = (0 until authorsJson.length()).map { authorsJson.getString(it) }
            return BookSuggestion(
                name = obj.getString("name"),
                edition = obj.getString("edition"),
                format = obj.getString("format"),
                authors = authors,
            )
        }
    }
}
