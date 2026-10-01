package com.iptv.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.iptv.feature.catalog.ui.VodCatalogViewModel
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

    /** Destino único que aloja las pestañas principales (live/movies/…). */
    const val Home = "home"
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

// Las pestañas viven dentro de "home": cambiar de pestaña ya no navega, así
// que no hay transición de NavHost que suavizar; el fundido corto sólo aplica
// al entrar/salir de fichas y del reproductor.

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
                sources -> Routes.Home
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
    val showBottomBar = backStackEntry?.destination?.route == Routes.Home
    // Pestaña activa dentro de "home": la barra inferior la conmuta sin
    // navegar, de modo que ninguna pantalla sale de la composición.
    var selectedTab by rememberSaveable { mutableStateOf(Routes.LiveTv) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MainBottomBar(selected = selectedTab, onSelect = { selectedTab = it })
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(150)) },
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
                        navController.navigate(Routes.Home) {
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
                        navController.navigate(Routes.Home) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Home) {
                HomeTabs(
                    selected = selectedTab,
                    onPlayChannel = { navController.navigate(Routes.player(it)) },
                    onMovieDetails = { navController.navigate(Routes.movieDetails(it)) },
                    onSeriesDetails = { navController.navigate(Routes.seriesDetails(it)) },
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

/**
 * Las cinco pestañas principales dentro del destino "home". Las ya visitadas
 * permanecen compuestas (colocadas fuera de pantalla las inactivas): cambiar
 * de pestaña es instantáneo y conserva composición, scroll, estado e
 * imágenes; al entrar en una ficha o en el reproductor NavHost desecha
 * "home" igual que antes.
 */
@Composable
private fun HomeTabs(
    selected: String,
    onPlayChannel: (Long) -> Unit,
    onMovieDetails: (Long) -> Unit,
    onSeriesDetails: (Long) -> Unit,
    onAddSource: () -> Unit,
    onShowTutorial: () -> Unit,
) {
    val visited = remember { mutableStateSetOf<String>() }
    SideEffect { visited += selected }

    // Instancias keyed en la entrada "home": las mismas que usarán las
    // pestañas Películas/Series. Cuando TV resuelve su primera carga se
    // precargan sus consultas a Room en segundo plano, anticipando el cambio
    // de pestaña (el usuario encuentra el contenido ya pintado).
    val moviesViewModel: VodCatalogViewModel = hiltViewModel(key = ContentKind.MOVIES.name)
    val seriesViewModel: VodCatalogViewModel = hiltViewModel(key = ContentKind.SERIES.name)
    val prefetchCatalogs = {
        moviesViewModel.attach(ContentKind.MOVIES)
        moviesViewModel.prefetch()
        seriesViewModel.attach(ContentKind.SERIES)
        seriesViewModel.prefetch()
    }

    Box(Modifier.fillMaxSize()) {
        mainDestinations.forEach { destination ->
            val isActive = destination.route == selected
            if (isActive || destination.route in visited) {
                Box(Modifier.fillMaxSize().keepComposed(isActive)) {
                    when (destination.route) {
                        Routes.LiveTv -> LiveTvScreen(
                            onChannelClick = onPlayChannel,
                            onContentReady = prefetchCatalogs,
                        )
                        Routes.Movies -> VodCatalogScreen(
                            kind = ContentKind.MOVIES,
                            onItemClick = onMovieDetails,
                            onResumeItem = onPlayChannel,
                            active = isActive,
                            viewModel = moviesViewModel,
                        )
                        Routes.Series -> VodCatalogScreen(
                            kind = ContentKind.SERIES,
                            onItemClick = onSeriesDetails,
                            onResumeItem = onPlayChannel,
                            active = isActive,
                            viewModel = seriesViewModel,
                        )
                        Routes.Guide -> GuideScreen(onChannelClick = onPlayChannel)
                        Routes.Settings -> SettingsScreen(
                            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            onAddSource = onAddSource,
                            onShowTutorial = onShowTutorial,
                        )
                    }
                }
            }
        }
    }
}

/** Mide y mantiene compuesto el contenido, pero lo coloca muy lejos a la
 *  derecha cuando no es la pestaña activa: ni se dibuja ni recibe toques. */
private fun Modifier.keepComposed(visible: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (visible) placeable.place(0, 0) else placeable.place(1_000_000, 0)
    }
}

@Composable
private fun MainBottomBar(selected: String, onSelect: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        mainDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination.route,
                onClick = { onSelect(destination.route) },
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
