package com.streamer.app.domain

object FilenameParser {

    data class ParsedTitle(
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val resolution: String? = null,
        val videoCodec: String? = null,
        val audioCodec: String? = null,
        val source: String? = null
    )

    fun parse(filename: String): ParsedTitle {
        val nameWithoutExt = filename.substringBeforeLast('.')

        // Extract technical metadata before cleaning
        val resolution = extractResolution(nameWithoutExt)
        val videoCodec = extractVideoCodec(nameWithoutExt)
        val audioCodec = extractAudioCodec(nameWithoutExt)
        val source = extractSource(nameWithoutExt)

        val tvRegex = Regex("""(.+?)\s*[Ss](\d{1,2})[Ee](\d{1,2})""")
        val yearParenRegex = Regex("""(.+?)\s*\((\d{4})\)""")
        val yearDotRegex = Regex("""(.+?)[\.\s](\d{4})[\.\s]""")

        tvRegex.find(nameWithoutExt)?.let { match ->
            return ParsedTitle(
                title = cleanTitle(match.groupValues[1]),
                season = match.groupValues[2].toIntOrNull(),
                episode = match.groupValues[3].toIntOrNull(),
                resolution = resolution,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                source = source
            )
        }

        yearParenRegex.find(nameWithoutExt)?.let { match ->
            return ParsedTitle(
                title = cleanTitle(match.groupValues[1]),
                year = match.groupValues[2].toIntOrNull(),
                resolution = resolution,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                source = source
            )
        }

        yearDotRegex.find(nameWithoutExt)?.let { match ->
            return ParsedTitle(
                title = cleanTitle(match.groupValues[1]),
                year = match.groupValues[2].toIntOrNull(),
                resolution = resolution,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                source = source
            )
        }

        return ParsedTitle(
            title = cleanTitle(nameWithoutExt),
            resolution = resolution,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            source = source
        )
    }

    private fun extractResolution(name: String): String? {
        val regex = Regex("""\b(2160p|4[kK]|1080p|720p|480p|360p)\b""", RegexOption.IGNORE_CASE)
        return regex.find(name)?.value?.let {
            if (it.equals("4k", ignoreCase = true)) "2160p" else it
        }
    }

    private fun extractVideoCodec(name: String): String? {
        val regex = Regex("""\b(x264|x265|[hH]\.?264|[hH]\.?265|HEVC|AV1|VP9|MPEG4)\b""", RegexOption.IGNORE_CASE)
        return regex.find(name)?.value?.uppercase()?.let {
            when {
                it.contains("264") -> "H.264"
                it.contains("265") || it == "HEVC" -> "H.265"
                else -> it
            }
        }
    }

    private fun extractAudioCodec(name: String): String? {
        val regex = Regex("""\b(AAC|AC3|AC-3|EAC3|E-AC-3|DTS|DTS-HD|TrueHD|Atmos|FLAC|Opus|MP3|LPCM)\b""", RegexOption.IGNORE_CASE)
        return regex.find(name)?.value?.uppercase()?.let {
            when (it) {
                "AC-3" -> "AC3"
                "E-AC-3" -> "EAC3"
                "DTS-HD" -> "DTS-HD"
                else -> it
            }
        }
    }

    private fun extractSource(name: String): String? {
        val regex = Regex("""\b(BluRay|Blu-Ray|BDRip|BRRip|WEB-?DL|WEBRip|HDRip|DVDRip|HDTV|REMUX|CAM|TS|DVDSCR)\b""", RegexOption.IGNORE_CASE)
        return regex.find(name)?.value?.let {
            when (it.uppercase()) {
                "BLURAY", "BLU-RAY" -> "BluRay"
                "BDRIP", "BRRIP" -> "BDRip"
                "WEBDL", "WEB-DL" -> "WEB-DL"
                "WEBRIP" -> "WEBRip"
                else -> it
            }
        }
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(
                Regex(
                    """\b(720p|1080p|2160p|4[kK]|[bB]lu[Rr]ay|WEB[Rr]ip|WEB-?DL|HDRip|BRRip|DVDRip|x264|x265|HEVC|H\.?264|H\.?265|AAC|DTS|AC3|YIFY|RARBG|YTS|AMZN|NF|REMUX)\b""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("""\[.*?]"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }
}
