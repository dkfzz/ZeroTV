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
        val categories: List<Category>,
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
        val body = get(sb.toString()) ?: return ListResult(emptyList(), 0, emptyList(), emptyList())
        return try {
            val root = JSONObject(body)
            if (root.optInt("code", 0) != 1) return ListResult(emptyList(), 0, emptyList(), emptyList())
            val pageCount = root.optInt("pagecount", 1)
            val classes = mutableListOf<VodClass>()
            val classArr = root.optJSONArray("class")
            if (classArr != null) {
                for (i in 0 until classArr.length()) {
                    val c = classArr.getJSONObject(i)
                    classes.add(VodClass(c.optString("type_id"), c.optString("type_name")))
                }
            }
            val categories = buildCategories(classes)
            val items = mutableListOf<Vod>()
            val listArr = root.optJSONArray("list") ?: return ListResult(emptyList(), pageCount, classes, categories)
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
            ListResult(items, pageCount, classes, categories)
        } catch (_: Exception) {
            ListResult(emptyList(), 0, emptyList(), emptyList())
        }
    }

    /**
     * 把扁平的 class 列表整理成 TVBox 式「父分类 + 子分类」两级结构。
     *
     * 各采集源 type_id 编号规则不统一，但分类名高度一致，因此按名称匹配：
     *  - 父分类（占位、不直接挂片）：电影/连续剧/电视剧/综艺/动漫
     *  - 子分类（真正挂片）：动作片/国产剧/大陆综艺/国产动漫 等
     * 归类不进的子分类统一放进「其他」。
     */
    private fun buildCategories(classes: List<VodClass>): List<Category> {
        if (classes.isEmpty()) return emptyList()
        // 父分类定义：name -> (匹配自身的正则, 子分类关键词列表)
        data class Parent(val name: String, val selfRe: Regex, val childKeys: List<String>)
        val parents = listOf(
            Parent("电影", Regex("^(电影|电影片)$"), listOf(
                "动作片", "喜剧片", "爱情片", "科幻片", "恐怖片", "剧情片", "战争片",
                "惊悚片", "灾难片", "悬疑片", "犯罪片", "奇幻片", "古装片", "历史片",
                "家庭片", "家庭篇", "西部片", "伦理", "理论片", "纪录片", "记录片", "短片"
            )),
            Parent("连续剧", Regex("^(连续剧|电视剧|剧集)$"), listOf(
                "国产剧", "内地剧", "香港剧", "港剧", "韩国剧", "韩剧", "欧美剧",
                "日本剧", "日剧", "台湾剧", "台剧", "泰国剧", "泰剧", "海外剧", "马泰剧"
            )),
            Parent("综艺", Regex("^(综艺|综艺片)$"), listOf(
                "大陆综艺", "港台综艺", "日韩综艺", "欧美综艺", "演唱会"
            )),
            Parent("动漫", Regex("^(动漫|动漫片)$"), listOf(
                "国产动漫", "日韩动漫", "欧美动漫", "港台动漫", "海外动漫", "中国动漫", "日本动漫",
                "动画片", "动画电影", "动漫电影", "里番动漫", "有声动漫"
            )),
        )

        // 先识别父分类节点
        val parentIdx = mutableMapOf<String, Int>()  // typeId -> parents 下标
        val used = mutableSetOf<String>()            // 已归类的 typeId
        val parentChildren = mutableListOf<MutableList<VodClass>>()
        parents.forEachIndexed { pi, p ->
            parentChildren.add(mutableListOf())
        }

        for (c in classes) {
            for (pi in parents.indices) {
                if (parents[pi].selfRe.matches(c.typeName)) {
                    parentIdx[c.typeId] = pi
                    used.add(c.typeId)
                    break
                }
            }
        }

        // 归类子分类
        val others = mutableListOf<VodClass>()
        for (c in classes) {
            if (c.typeId in used) continue  // 父分类自身跳过
            var matched = false
            for (pi in parents.indices) {
                if (parents[pi].childKeys.any { c.typeName.contains(it) || it.contains(c.typeName) }) {
                    parentChildren[pi].add(c)
                    used.add(c.typeId)
                    matched = true
                    break
                }
            }
            if (!matched) others.add(c)
        }

        val result = mutableListOf<Category>()
        for (pi in parents.indices) {
            val name = parents[pi].name
            // 如果该源没有这个父分类节点，但只要它有匹配的子分类，也建组
            if (parentChildren[pi].isNotEmpty() || parentIdx.values.any { it == pi }) {
                result.add(Category(name, parentChildren[pi]))
            }
        }
        if (others.isNotEmpty()) result.add(Category("其他", others))
        return result
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
