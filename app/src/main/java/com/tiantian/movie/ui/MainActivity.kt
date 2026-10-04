package com.tiantian.movie.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    AppNav()
                }
            }
        }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val vm: HomeViewModel = viewModel()
            HomeScreen(
                vm = vm,
                onOpenSearch = { nav.navigate("search") },
                onOpenDetail = { s, id -> nav.navigate("detail/$s/$id") },
            )
        }
        composable("search") {
            val vm: SearchViewModel = viewModel()
            SearchScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenDetail = { s, id -> nav.navigate("detail/$s/$id") },
            )
        }
        composable(
            "detail/{sourceIndex}/{vodId}",
            arguments = listOf(
                navArgument("sourceIndex") { type = NavType.IntType },
                navArgument("vodId") { type = NavType.StringType },
            ),
        ) { entry ->
            val vm: DetailViewModel = viewModel()
            DetailScreen(
                vm = vm,
                sourceIndex = entry.arguments!!.getInt("sourceIndex"),
                vodId = entry.arguments!!.getString("vodId")!!,
                onPlay = { url, title ->
                    PlayerHolder.url = url
                    PlayerHolder.title = title
                    nav.navigate("player")
                },
            )
        }
        composable("player") {
            PlayerScreen(onBack = { nav.popBackStack() })
        }
    }
}
