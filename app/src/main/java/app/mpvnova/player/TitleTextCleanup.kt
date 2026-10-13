package app.mpvnova.player

internal fun cleanTitleBrackets(value: String): String {
    if (value.none { it in "()[]" }) return value
    val title = value.replace(RELEASE_GROUP_TAG_PATTERN, " ")
    val unmatched = mutableSetOf<Int>()
    val openings = ArrayDeque<Int>()
    title.forEachIndexed { index, char ->
        when (char) {
            '(', '[' -> openings.addLast(index)
            ')', ']' -> {
                val expected = if (char == ')') '(' else '['
                if (openings.isNotEmpty() && title[openings.last()] == expected) {
                    openings.removeLast()
                } else {
                    unmatched.add(index)
                }
            }
        }
    }
    unmatched.addAll(openings)
    // Splitting a filename at an episode or codec tag can leave an orphan bracket.
    // Keep matched pairs: years and parenthetical phrases can be part of real titles.
    return title.filterIndexed { index, _ -> index !in unmatched }
        .replace(TITLE_YEAR_SPACING_PATTERN, " $1")
        .replace(TITLE_WHITESPACE_PATTERN, " ")
        .trim()
}

// Only recognized release groups belong here; an arbitrary acronym may be a real title.
private const val KNOWN_RELEASE_GROUPS = "(?:CBM|TTGA)"
private val RELEASE_GROUP_TAG_PATTERN = Regex("""(?i)\(\s*$KNOWN_RELEASE_GROUPS\s*\)|\[\s*$KNOWN_RELEASE_GROUPS\s*\]""")
private val RELEASE_GROUP_CREDIT_PATTERN = Regex(
    """(?i)(?:presented\s+by\s+)?$KNOWN_RELEASE_GROUPS(?:@[\w.-]+)?""",
)
internal fun isReleaseGroupCredit(value: String): Boolean =
    RELEASE_GROUP_CREDIT_PATTERN.matches(value.trim()) ||
        (RELEASE_GROUP_TAG_PATTERN.containsMatchIn(value) && cleanTitleBrackets(value).isBlank())
private val TITLE_YEAR_SPACING_PATTERN = Regex("""(?<=\S)(\(\d{4}\))""")
private val TITLE_WHITESPACE_PATTERN = Regex("\\s+")
