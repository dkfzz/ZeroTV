package com.tiantian.movie.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 苹果 CMS 标准 provide 接口封装。
 * 列表: ?ac=list&pg=1[&t=typeId][&wd=keyword]
 * 详情: ?ac=detail&ids=vodId
 */
object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    data class ListResult(
        val items: List<Vod>,
        val pageCount: Int,
        val classes: List<VodClass>,
    )

    private fun get(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** 列表 / 分类 / 搜索 */
    fun fetchList(
        sourceIndex: Int,
        page: Int,
        typeId: String? = null,
        keyword: String? = null,
    ): ListResult {
        val base = SOURCES[sourceIndex].api
        val sb = StringBuilder(base).append("?ac=list&pg=").append(page)
        if (!typeId.isNullOrEmpty()) sb.append("&t=").append(enc(typeId))
        if (!keyword.isNullOrEmpty()) sb.append("&wd=").append(enc(keyword))
        val body = get(sb.toString()) ?: return ListResult(emptyList(), 0, emptyList())
        return try {
            val root = JSONObject(body)
            if (root.optInt("code", 0) != 1) return ListResult(emptyList(), 0, emptyList())
            val pageCount = root.optInt("pagecount", 1)
            val classes = mutableListOf<VodClass>()
            val classArr = root.optJSONArray("class")
            if (classArr != null) {
                for (i in 0 until classArr.length()) {
                    val c = classArr.getJSONObject(i)
                    classes.add(VodClass(c.optString("type_id"), c.optString("type_name")))
                }
            }
            val items = mutableListOf<Vod>()
            val listArr = root.optJSONArray("list") ?: return ListResult(emptyList(), pageCount, classes)
            for (i in 0 until listArr.length()) {
                val o = listArr.getJSONObject(i)
                items.add(
                    Vod(
                        vodId = o.optString("vod_id"),
                        title = o.optString("vod_name"),
                        pic = o.optString("vod_pic"),
                        remarks = o.optString("vod_remarks"),
                        typeName = o.optString("type_name"),
                        year = o.optString("vod_year"),
                        area = o.optString("vod_area"),
                        actor = o.optString("vod_actor"),
                        director = o.optString("vod_director"),
                        content = o.optString("vod_content").replace(Regex("<[^>]*>"), ""),
                        sourceIndex = sourceIndex,
                    )
                )
            }
            ListResult(items, pageCount, classes)
        } catch (_: Exception) {
            ListResult(emptyList(), 0, emptyList())
        }
    }

    /** 详情 + 播放线路/选集 */
    fun fetchDetail(sourceIndex: Int, vodId: String): Pair<Vod, List<PlaySource>>? {
        val url = SOURCES[sourceIndex].api + "?ac=detail&ids=" + enc(vodId)
        val body = get(url) ?: return null
        return try {
            val root = JSONObject(body)
            if (root.optInt("code", 0) != 1) return null
            val o = root.getJSONArray("list").getJSONObject(0)
            val vod = Vod(
                vodId = o.optString("vod_id"),
                title = o.optString("vod_name"),
                pic = o.optString("vod_pic"),
                remarks = o.optString("vod_remarks"),
                typeName = o.optString("type_name"),
                year = o.optString("vod_year"),
                area = o.optString("vod_area"),
                actor = o.optString("vod_actor"),
                director = o.optString("vod_director"),
                content = o.optString("vod_content").replace(Regex("<[^>]*>"), ""),
                sourceIndex = sourceIndex,
            )
            val fromRaw = o.optString("vod_play_from")
            val urlRaw = o.optString("vod_play_url")
            val froms = if (fromRaw.isEmpty()) listOf("默认线路") else fromRaw.split("\$\$\$")
            val urlGroups = if (urlRaw.isEmpty()) emptyList() else urlRaw.split("\$\$\$")
            val sources = mutableListOf<PlaySource>()
            for (gi in froms.indices) {
                val name = froms[gi].ifEmpty { "线路${gi + 1}" }
                val group = urlGroups.getOrNull(gi) ?: continue
                val episodes = mutableListOf<Episode>()
                for (part in group.split("#")) {
                    if (part.isEmpty()) continue
                    val idx = part.lastIndexOf('$')
                    if (idx <= 0) continue
                    val epName = part.substring(0, idx).ifEmpty { "正片" }
                    val epUrl = part.substring(idx + 1).trim()
                    if (epUrl.isEmpty()) continue
                    episodes.add(Episode(epName, epUrl))
                }
                if (episodes.isNotEmpty()) sources.add(PlaySource(name, episodes))
            }
            // 兜底：from 与 url 数量不一致时按单组解析
            if (sources.isEmpty() && urlRaw.isNotEmpty()) {
                val episodes = mutableListOf<Episode>()
                for (part in urlRaw.split("#")) {
                    if (part.isEmpty()) continue
                    val idx = part.lastIndexOf('$')
                    if (idx <= 0) continue
                    val epUrl = part.substring(idx + 1).trim()
                    if (epUrl.isEmpty()) continue
                    episodes.add(Episode(part.substring(0, idx).ifEmpty { "正片" }, epUrl))
                }
                if (episodes.isNotEmpty()) sources.add(PlaySource("默认线路", episodes))
            }
            Pair(vod, sources)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 批量补海报：列表/搜索接口不返回 vod_pic，逐条拉详情补充。
     * onPoster 在 IO 线程回调，调用方自行切回主线程更新 UI。
     */
    suspend fun enrichPosters(items: List<Vod>, onPoster: suspend (Vod, String) -> Unit) = coroutineScope {
        items.filter { it.pic.isEmpty() }.map { vod ->
            async(Dispatchers.IO) {
                try {
                    val pic = fetchDetail(vod.sourceIndex, vod.vodId)?.first?.pic.orEmpty()
                    if (pic.isNotEmpty()) onPoster(vod, pic)
                } catch (_: Exception) {
                }
            }
        }.awaitAll()
    }
}
