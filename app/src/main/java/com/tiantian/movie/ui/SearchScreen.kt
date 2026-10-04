package com.tiantian.movie.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiantian.movie.data.Api
import com.tiantian.movie.data.SOURCES
import com.tiantian.movie.data.Vod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel : ViewModel() {
    val results = mutableStateListOf<Vod>()
    var searching by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set

    /** 全源并行搜索并合并去重 */
    fun search(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty() || searching) return
        searching = true
        searched = false
        results.clear()
        viewModelScope.launch {
            val merged = withContext(Dispatchers.IO) {
                SOURCES.indices.map { idx ->
                    async {
                        try {
                            Api.fetchList(idx, 1, null, kw).items
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
            // 按标题去重，保留第一个
            val seen = LinkedHashSet<String>()
            for (v in merged) {
                if (seen.add(v.title)) results.add(v)
            }
            searching = false
            searched = true
            enrichPosters(results.toList())
        }
    }

    /** 后台批量补海报 */
    private fun enrichPosters(list: List<Vod>) {
        viewModelScope.launch(Dispatchers.IO) {
            Api.enrichPosters(list) { vod, pic ->
                withContext(Dispatchers.Main) {
                    val idx = results.indexOfFirst {
                        it.sourceIndex == vod.sourceIndex && it.vodId == vod.vodId
                    }
                    if (idx >= 0) results[idx] = results[idx].copy(pic = pic)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onBack: () -> Unit,
    onOpenDetail: (sourceIndex: Int, vodId: String) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text("输入片名搜索（全源）", color = TextGray, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(keyword) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color(0xFF3A3A48),
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = { vm.search(keyword) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(8.dp),
            ) { Text("搜索") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (vm.searching) "正在全网搜索…" else if (vm.searched) "找到 ${vm.results.size} 个结果" else "← 返回",
            color = TextGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cols = if (maxWidth > 900.dp) 6 else 3
            if (vm.searching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.results, key = { "${it.sourceIndex}_${it.vodId}_${it.title}" }) { vod ->
                        PosterCard(vod = vod, onClick = { onOpenDetail(vod.sourceIndex, vod.vodId) })
                    }
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
