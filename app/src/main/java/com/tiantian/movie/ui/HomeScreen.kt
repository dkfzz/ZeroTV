package com.tiantian.movie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiantian.movie.data.Api
import com.tiantian.movie.data.Category
import com.tiantian.movie.data.SOURCES
import com.tiantian.movie.data.Vod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {
    var sourceIndex by mutableStateOf(0); private set
    var typeId: String? by mutableStateOf(null); private set
    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var selectedCat by mutableStateOf(0); private set
    val items = mutableStateListOf<Vod>()
    var page by mutableStateOf(1); private set
    var pageCount by mutableStateOf(1); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    /** 空分类提示：API 正常返回但该分类下无数据 */
    var emptyCategory by mutableStateOf(false); private set

    fun selectSource(i: Int) {
        if (i == sourceIndex) return
        sourceIndex = i
        typeId = null
        selectedCat = 0
        categories = emptyList()
        reload()
    }

    fun selectCategory(cat: Int) {
        if (cat == selectedCat) return
        selectedCat = cat
        // 切到父分类后，自动选中其第一个子分类（若有）
        val children = categories.getOrNull(cat)?.children.orEmpty()
        typeId = children.firstOrNull()?.typeId
        reload()
    }

    fun selectType(id: String?) {
        if (id == typeId) return
        typeId = id
        reload()
    }

    fun reload() {
        items.clear()
        page = 1
        pageCount = 1
        error = null
        emptyCategory = false
        loadMore()
    }

    fun loadMore() {
        if (loading || page > pageCount) return

        // 首次进入：还没选分类，先「只拉分类」，拿到后选默认子分类再真正拉片
        if (categories.isEmpty() && typeId == null && page == 1 && items.isEmpty()) {
            loadCategoriesFirst()
            return
        }

        loading = true
        val s = sourceIndex
        val p = page
        val t = typeId
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Api.fetchList(s, p, t, null) }
                if (r.items.isEmpty() && p == 1) {
                    if (r.classes.isNotEmpty()) {
                        emptyCategory = true
                    } else {
                        error = "加载失败，换个资源站试试"
                    }
                } else {
                    emptyCategory = false
                    items.addAll(r.items)
                    pageCount = r.pageCount.coerceAtLeast(1)
                    page = p + 1
                    if (r.categories.isNotEmpty() && categories.isEmpty()) {
                        categories = r.categories
                    }
                    enrichPosters(r.items)
                }
            } catch (_: Exception) {
                if (p == 1 && items.isEmpty()) error = "加载失败，换个资源站试试"
            }
            loading = false
        }
    }

    /** 首次加载：只拉分类列表，自动选中第一个有子分类的父分类，再拉片 */
    private fun loadCategoriesFirst() {
        loading = true
        val s = sourceIndex
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Api.fetchList(s, 1, null, null) }
                categories = r.categories
                if (r.categories.isNotEmpty()) {
                    val firstWithChildren = r.categories.firstOrNull { it.children.isNotEmpty() }
                    if (firstWithChildren != null) {
                        selectedCat = r.categories.indexOf(firstWithChildren)
                        typeId = firstWithChildren.children.first().typeId
                    }
                }
                loading = false
                if (typeId != null) {
                    reload()
                } else {
                    error = "加载失败，换个资源站试试"
                }
            } catch (_: Exception) {
                loading = false
                error = "加载失败，换个资源站试试"
            }
        }
    }

    /** 后台批量补海报（列表接口不返回海报） */
    private fun enrichPosters(newItems: List<Vod>) {
        viewModelScope.launch(Dispatchers.IO) {
            Api.enrichPosters(newItems) { vod, pic ->
                withContext(Dispatchers.Main) {
                    val idx = items.indexOfFirst {
                        it.sourceIndex == vod.sourceIndex && it.vodId == vod.vodId
                    }
                    if (idx >= 0) items[idx] = items[idx].copy(pic = pic)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenSearch: () -> Unit,
    onOpenDetail: (sourceIndex: Int, vodId: String) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (vm.items.isEmpty()) vm.reload()
    }
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(12.dp),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "天天影视",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "v1.2",
                color = TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
            var searchFocused by remember { mutableStateOf(false) }
            Text(
                "🔍 搜索",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .onFocusChanged { searchFocused = it.isFocused }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenSearch() }
                    .focusRequester(focusRequester)
                    .background(
                        if (searchFocused) Accent else CardBg,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        // 资源站选择
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SOURCES.size) { i ->
                var focused by remember { mutableStateOf(false) }
                Text(
                    SOURCES[i].name,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { vm.selectSource(i) }
                        .background(
                            when {
                                i == vm.sourceIndex -> Accent
                                focused -> Color(0xFF3A3A48)
                                else -> CardBg
                            },
                            RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 分类（TVBox 式两级：父分类一行，选中后下方显示子分类一行）
        if (vm.categories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.categories.size) { i ->
                    var focused by remember { mutableStateOf(false) }
                    Text(
                        vm.categories[i].name,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { vm.selectCategory(i) }
                            .background(
                                when {
                                    i == vm.selectedCat -> Accent
                                    focused -> Color(0xFF3A3A48)
                                    else -> CardBg
                                },
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // 子分类
            val children = vm.categories.getOrNull(vm.selectedCat)?.children.orEmpty()
            if (children.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(children) { c ->
                        var focused by remember { mutableStateOf(false) }
                        Text(
                            c.typeName,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .onFocusChanged { focused = it.isFocused }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { vm.selectType(c.typeId) }
                                .background(
                                    when {
                                        vm.typeId == c.typeId -> Accent
                                        focused -> Color(0xFF3A3A48)
                                        else -> CardBg
                                    },
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // 海报墙
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cols = if (maxWidth > 900.dp) 6 else 3
            if (vm.error != null && vm.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(vm.error!!, color = TextGray, fontSize = 15.sp)
                }
            } else if (vm.emptyCategory && vm.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该分类暂无内容，试试其他子分类（如动作片、喜剧片）", color = TextGray, fontSize = 15.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(vm.items, key = { _, v -> "${v.sourceIndex}_${v.vodId}" }) { index, vod ->
                        if (index == vm.items.size - 1) {
                            LaunchedEffect(Unit) { vm.loadMore() }
                        }
                        PosterCard(vod = vod, onClick = { onOpenDetail(vod.sourceIndex, vod.vodId) })
                    }
                    if (vm.loading) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = Accent) }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
