package com.tiantian.movie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.tiantian.movie.data.Api
import com.tiantian.movie.data.PlaySource
import com.tiantian.movie.data.SOURCES
import com.tiantian.movie.data.Vod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel : ViewModel() {
    var vod by mutableStateOf<Vod?>(null); private set
    var sources by mutableStateOf<List<PlaySource>>(emptyList()); private set
    var selectedSource by mutableStateOf(0); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    fun load(sourceIndex: Int, vodId: String) {
        loading = true
        error = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Api.fetchDetail(sourceIndex, vodId) }
            if (r == null) {
                error = "详情加载失败"
            } else {
                vod = r.first
                sources = r.second
                selectedSource = 0
            }
            loading = false
        }
    }

    fun selectSource(i: Int) { selectedSource = i }
}

@Composable
fun DetailScreen(
    vm: DetailViewModel,
    sourceIndex: Int,
    vodId: String,
    onPlay: (url: String, title: String) -> Unit,
) {
    LaunchedEffect(sourceIndex, vodId) { vm.load(sourceIndex, vodId) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(16.dp),
    ) {
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            vm.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(vm.error!!, color = TextGray)
            }
            else -> {
                val vod = vm.vod!!
                Row(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = vod.pic,
                        contentDescription = vod.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(150.dp)
                            .height(200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardBg),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vod.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOf(vod.year, vod.area, vod.typeName, vod.remarks)
                                .filter { it.isNotEmpty() }.joinToString(" · "),
                            color = TextGray, fontSize = 13.sp,
                        )
                        if (vod.director.isNotEmpty()) Text("导演：${vod.director}", color = TextGray, fontSize = 13.sp)
                        if (vod.actor.isNotEmpty()) Text(
                            "主演：${vod.actor.take(60)}", color = TextGray, fontSize = 13.sp,
                            maxLines = 2,
                        )
                        Text("来源：${SOURCES[vod.sourceIndex].name}", color = TextGray, fontSize = 13.sp)
                    }
                }
                if (vod.content.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        vod.content.take(300),
                        color = TextGray, fontSize = 13.sp, maxLines = 4,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // 线路选择
                if (vm.sources.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.sources.size) { i ->
                            FocusButton(
                                text = vm.sources[i].name,
                                selected = i == vm.selectedSource,
                                onClick = { vm.selectSource(i) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 选集
                val eps = vm.sources.getOrNull(vm.selectedSource)?.episodes.orEmpty()
                Text("选集（${eps.size}）", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(eps) { ep ->
                        FocusButton(
                            text = ep.name,
                            onClick = { onPlay(ep.url, "${vod.title} ${ep.name}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
