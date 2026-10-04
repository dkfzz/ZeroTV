package com.tiantian.movie.data

/** 单个视频条目（列表/搜索结果） */
data class Vod(
    val vodId: String,
    val title: String,
    val pic: String,
    val remarks: String = "",
    val typeName: String = "",
    val year: String = "",
    val area: String = "",
    val actor: String = "",
    val director: String = "",
    val content: String = "",
    val sourceIndex: Int = 0,
)

/** 播放线路 */
data class PlaySource(
    val name: String,
    val episodes: List<Episode>,
)

/** 单集 */
data class Episode(
    val name: String,
    val url: String,
)

/** 资源站 */
data class SourceInfo(
    val name: String,
    val api: String,
)

/** 分类 */
data class VodClass(
    val typeId: String,
    val typeName: String,
)

/** 父分类（TVBox 式两级：父分类一行，选中后显示子分类一行） */
data class Category(
    val name: String,
    val children: List<VodClass>,
)
