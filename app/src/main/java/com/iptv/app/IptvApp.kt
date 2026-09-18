package com.iptv.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.iptv.core.designsystem.components.LoadingState
import com.iptv.feature.catalog.ui.ContentKind
import com.iptv.feature.catalog.ui.LiveTvScreen
import com.iptv.feature.catalog.ui.MovieDetailsScreen
import com.iptv.feature.catalog.ui.VodCatalogScreen
import com.iptv.feature.epg.ui.GuideScreen
import com.iptv.feature.player.ui.PlayerScreen
import com.iptv.feature.series.ui.SeriesDetailsScreen
import com.iptv.feature.source.ui.AddSourceScreen
import com.iptv.feature.source.ui.OnboardingScreen
import com.iptv.feature.source.ui.SettingsScreen

private object Routes {
    const val Onboarding = "onboarding"
    const val AddSource = "add-source"
    const val LiveTv = "live"
    const val Movies = "movies"
    const val Series = "series"
    const val Guide = "guide"
    const val Settings = "settings"
    const val Player = "player/{channelId}"
    const val SeriesDetails = "series-detail/{seriesId}"
    const val MovieDetails = "movie/{channelId}"

    fun player(channelId: Long) = "player/$channelId"
    fun seriesDetails(seriesId: Long) = "series-detail/$seriesId"
    fun movieDetails(channelId: Long) = "movie/$channelId"
}

private data class MainDestination(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val mainDestinations = listOf(
    MainDestination(Routes.LiveTv, R.string.nav_live, Icons.Filled.LiveTv),
    MainDestination(Routes.Movies, R.string.nav_movies, Icons.Filled.Movie),
    MainDestination(Routes.Series, R.string.nav_series, Icons.Filled.Tv),
    MainDestination(Routes.Guide, R.string.nav_guide, Icons.Filled.CalendarMonth),
    MainDestination(Routes.Settings, R.string.nav_settings, Icons.Filled.Settings),
)

private val mainRoutes = mainDestinations.mapTo(HashSet()) { it.route }

// Entre pestañas el cambio es instantáneo: el fundido de 700 ms que NavHost
// aplica por defecto hacía el cambio de pestaña perceptiblemente lento. Al
// abrir una ficha o el reproductor se mantiene un fundido corto.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in mainRoutes && targetState.destination.route in mainRoutes

@Composable
fun IptvApp(viewModel: RootViewModel = hiltViewModel()) {
    val hasSources by viewModel.hasSources.collectAsStateWithLifecycle()
    val onboardingDone by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var startRoute by remember { mutableStateOf<String?>(null) }

    // El destino inicial se fija una sola vez: si reaccionara a hasSources en
    // vivo, el alta de la lista demo durante el tutorial recrearía el NavHost
    // y expulsaría al usuario del pager a mitad del paso.
    LaunchedEffect(hasSources, onboardingDone) {
        val sources = hasSources
        val done = onboardingDone
        if (startRoute == null && sources != null && done != null) {
            startRoute = when {
                sources -> Routes.LiveTv
                done -> Routes.AddSource
                else -> Routes.Onboarding
            }
        }
    }

    when (val route = startRoute) {
        null -> LoadingState()
        else -> AppNavigation(
            navController = navController,
            startDestination = route,
            onOnboardingComplete = viewModel::completeOnboarding,
        )
    }
}

@Composable
private fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    onOnboardingComplete: () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = mainDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) MainBottomBar(navController)
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { if (isTabSwitch()) EnterTransition.None else fadeIn(tween(150)) },
            exitTransition = { if (isTabSwitch()) ExitTransition.None else fadeOut(tween(150)) },
            popEnterTransition = { if (isTabSwitch()) EnterTransition.None else fadeIn(tween(150)) },
            popExitTransition = { if (isTabSwitch()) ExitTransition.None else fadeOut(tween(150)) },
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(
                    onSkip = {
                        onOnboardingComplete()
                        navController.navigate(Routes.AddSource)
                    },
                    onAddOwnSource = { navController.navigate(Routes.AddSource) },
                    onDone = {
                        onOnboardingComplete()
                        navController.navigate(Routes.LiveTv) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                        }
                    },
                )
            }
            composable(Routes.AddSource) {
                AddSourceScreen(
                    onDone = {
                        onOnboardingComplete()
                        navController.navigate(Routes.LiveTv) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LiveTv) {
                LiveTvScreen(onChannelClick = { navController.navigate(Routes.player(it)) })
            }
            composable(
                route = Routes.Movies,
                arguments = listOf(navArgument("kind") { defaultValue = ContentKind.MOVIES.name }),
            ) {
                VodCatalogScreen(
                    onItemClick = { navController.navigate(Routes.movieDetails(it)) },
                    onResumeItem = { navController.navigate(Routes.player(it)) },
                )
            }
            composable(
                route = Routes.Series,
                arguments = listOf(navArgument("kind") { defaultValue = ContentKind.SERIES.name }),
            ) {
                VodCatalogScreen(
                    onItemClick = { navController.navigate(Routes.seriesDetails(it)) },
                    onResumeItem = { navController.navigate(Routes.player(it)) },
                )
            }
            composable(Routes.Guide) {
                GuideScreen(onChannelClick = { navController.navigate(Routes.player(it)) })
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onAddSource = { navController.navigate(Routes.AddSource) },
                    onShowTutorial = { navController.navigate(Routes.Onboarding) },
                )
            }
            composable(
                route = Routes.Player,
                arguments = listOf(navArgument("channelId") { type = NavType.LongType }),
            ) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.SeriesDetails,
                arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
            ) {
                SeriesDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Routes.player(it)) },
                )
            }
            composable(
                route = Routes.MovieDetails,
                arguments = listOf(navArgument("channelId") { type = NavType.LongType }),
            ) {
                MovieDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Routes.player(it)) },
                )
            }
        }
    }
}

@Composable
private fun MainBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        mainDestinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
