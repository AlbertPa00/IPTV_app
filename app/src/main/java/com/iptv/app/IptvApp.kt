package com.iptv.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ConnectedTv
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.iptv.core.designsystem.tv.LocalIsTv
import com.iptv.core.designsystem.tv.tvSafeArea
import com.iptv.feature.catalog.ui.ContentKind
import com.iptv.feature.catalog.ui.VodCatalogViewModel
import com.iptv.feature.catalog.ui.LiveTvScreen
import com.iptv.feature.catalog.ui.MovieDetailsScreen
import com.iptv.feature.catalog.ui.VodCatalogScreen
import com.iptv.feature.epg.ui.GuideScreen
import com.iptv.feature.player.ui.PlayerScreen
import com.iptv.feature.player.tv.PENDING_TV_CHANNEL_KEY
import com.iptv.feature.player.tv.TvPlayerScreen
import com.iptv.feature.series.ui.SeriesDetailsScreen
import com.iptv.feature.source.ui.AddSourceScreen
import com.iptv.feature.source.ui.OnboardingScreen
import com.iptv.feature.source.ui.SettingsScreen
import com.iptv.feature.tvlink.ui.TvLinkScreen
import kotlinx.coroutines.delay

// Intro de marca: mínimo en pantalla para que el logo se perciba (en TV va
// más corto porque la shell es player-first) y duración del fundido hacia
// la primera pantalla.
private const val SPLASH_MIN_MS = 550L
private const val SPLASH_MIN_TV_MS = 350L
private const val SPLASH_FADE_MS = 260

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
    const val TvLink = "tvlink"

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
fun IptvApp(
    isTv: Boolean,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val hasSources by viewModel.hasSources.collectAsStateWithLifecycle()
    val onboardingDone by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var startRoute by remember { mutableStateOf<String?>(null) }

    // El destino inicial se fija una sola vez: si reaccionara a hasSources en
    // vivo, el alta de la lista demo durante el tutorial recrearía el NavHost
    // y expulsaría al usuario del pager a mitad del paso. Tutorial completado
    // sin fuente también entra a Home: la app se explora vacía y la fuente se
    // añade cuando se quiera desde Ajustes.
    LaunchedEffect(hasSources, onboardingDone) {
        val sources = hasSources
        val done = onboardingDone
        if (startRoute == null && sources != null && done != null) {
            startRoute = when {
                // Shell de TV player-first: con fuentes se abre reproduciendo
                // el primer canal directo; sin ellas cae al alta/onboarding.
                isTv && sources == true -> viewModel.resolveTvStartChannel()
                    ?.let(Routes::player) ?: Routes.Home
                sources == true || done == true -> Routes.Home
                else -> Routes.Onboarding
            }
        }
    }

    // La intro se mantiene un mínimo aunque el destino resuelva antes; la
    // salida funde el logo hacia la primera pantalla sin corte a negro.
    var splashElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(if (isTv) SPLASH_MIN_TV_MS else SPLASH_MIN_MS)
        splashElapsed = true
    }

    AnimatedContent(
        targetState = startRoute == null || !splashElapsed,
        transitionSpec = {
            fadeIn(tween(SPLASH_FADE_MS)) togetherWith
                (fadeOut(tween(SPLASH_FADE_MS)) +
                    scaleOut(targetScale = 1.05f, animationSpec = tween(SPLASH_FADE_MS)))
        },
        label = "brandSplash",
    ) { splash ->
        when {
            splash -> BrandSplash()
            else -> startRoute?.let { route ->
                CompositionLocalProvider(LocalIsTv provides isTv) {
                    AppNavigation(
                        navController = navController,
                        startDestination = route,
                        isTv = isTv,
                        onOnboardingComplete = viewModel::completeOnboarding,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    isTv: Boolean,
    onOnboardingComplete: () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = backStackEntry?.destination?.route == Routes.Home
    // Pestaña activa dentro de "home": la barra inferior la conmuta sin
    // navegar, de modo que ninguna pantalla sale de la composición.
    var selectedTab by rememberSaveable { mutableStateOf(Routes.LiveTv) }

    // En TV reproducir no navega: el reproductor es la raíz y un segundo
    // player/{id} apilado tendría otro ExoPlayer sonando a la vez. Se
    // desapila hasta él y el canal llega por savedStateHandle.
    val tvPlayerExit: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            // Reproductor raíz: atrás lleva al menú, no cierra la app.
            // OJO: no usar popBackStack() para el chequeo — en la raíz
            // también desapila y NavController cierra al vaciar la pila.
            navController.navigate(Routes.Home)
        }
    }
    val playChannel: (Long) -> Unit = { channelId ->
        if (isTv && navController.popBackStack(Routes.Player, inclusive = false)) {
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(PENDING_TV_CHANNEL_KEY, channelId)
        } else {
            navController.navigate(Routes.player(channelId))
        }
    }

    val host: @Composable (Modifier) -> Unit = { hostModifier ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = hostModifier,
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(150)) },
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(
                    onSkip = {
                        onOnboardingComplete()
                        // Saltar lleva a añadir fuente PERO saca el tutorial de
                        // la pila: atrás o "Explorar sin fuente" van a Home.
                        navController.navigate(Routes.AddSource) {
                            popUpTo(Routes.Onboarding) { inclusive = true }
                        }
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
                // Raíz de la pila = venimos de "Saltar": el gesto/flecha de
                // atrás no cierra la app ni vuelve al tutorial, va a Home.
                // popUpTo(0) y no startDestination: en instalación nueva el
                // inicio era Onboarding y ya salió de la pila al saltar — sin
                // nada que hacer pop, Home quedaría apilado sobre AddSource.
                val isRoot = navController.previousBackStackEntry == null
                val goHome: () -> Unit = {
                    onOnboardingComplete()
                    navController.navigate(Routes.Home) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                if (isRoot) BackHandler { goHome() }
                AddSourceScreen(
                    onDone = goHome,
                    onBack = { if (!navController.popBackStack()) goHome() },
                    onExplore = if (isRoot) goHome else null,
                )
            }
            composable(Routes.Home) {
                if (isTv) {
                    // En TV el "menú" es el tope de la pila: atrás sale de
                    // la app. Handler por destino — se registra al componer
                    // Home, después del callback interno de NavHost, así que
                    // siempre gana la pulsación. El press que nos trajo del
                    // reproductor ya se despachó entero en aquella pantalla
                    // (la tecla BACK nunca la consume el view path), así que
                    // este handler sólo ve presses nuevos.
                    val activity = LocalActivity.current
                    BackHandler { activity?.finish() }
                }
                HomeTabs(
                    selected = selectedTab,
                    onPlayChannel = playChannel,
                    onMovieDetails = { navController.navigate(Routes.movieDetails(it)) },
                    onSeriesDetails = { navController.navigate(Routes.seriesDetails(it)) },
                    onAddSource = { navController.navigate(Routes.AddSource) },
                    onShowTutorial = { navController.navigate(Routes.Onboarding) },
                    onLinkedDevices = { navController.navigate(Routes.TvLink) },
                )
            }
            composable(
                route = Routes.Player,
                arguments = listOf(navArgument("channelId") { type = NavType.LongType }),
            ) { playerEntry ->
                if (isTv) {
                    val activity = LocalActivity.current
                    TvPlayerScreen(
                        onExit = tvPlayerExit,
                        onQuit = { activity?.finish() },
                        onBrowse = { navController.navigate(Routes.Home) },
                        savedStateHandle = playerEntry.savedStateHandle,
                    )
                } else {
                    PlayerScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(
                route = Routes.SeriesDetails,
                arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
            ) {
                SeriesDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = playChannel,
                )
            }
            composable(
                route = Routes.MovieDetails,
                arguments = listOf(navArgument("channelId") { type = NavType.LongType }),
            ) {
                MovieDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = playChannel,
                )
            }
            composable(Routes.TvLink) {
                TvLinkScreen(
                    onBack = { navController.popBackStack() },
                    onPlayLocal = playChannel,
                )
            }
        }
    }

    if (isTv) {
        // Riel lateral en vez de barra inferior: la navegación por pestañas
        // vive a la izquierda, patrón de las apps de TV (y sólo en Home).
        // La Surface hace lo que el Scaffold del móvil: fija el fondo y, lo
        // importante, el color de contenido — sin ella LocalContentColor
        // queda en su valor por defecto (negro) y todo Text sin color
        // explícito sale negro sobre negro (la pestaña Ajustes entera).
        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                if (showBottomBar) {
                    TvSideRail(selected = selectedTab, onSelect = { selectedTab = it })
                }
                Box(Modifier.weight(1f)) { host(Modifier.fillMaxSize()) }
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    MainBottomBar(selected = selectedTab, onSelect = { selectedTab = it })
                }
            },
            floatingActionButton = {
                // Atajo al mando/vinculación: un toque desde cualquier pestaña.
                if (showBottomBar) {
                    SmallFloatingActionButton(
                        onClick = { navController.navigate(Routes.TvLink) },
                    ) {
                        Icon(
                            Icons.Filled.ConnectedTv,
                            contentDescription = stringResource(
                                com.iptv.feature.tvlink.R.string.tvlink_remote_section,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            host(Modifier.padding(innerPadding))
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
    onLinkedDevices: () -> Unit,
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
                            onLinkedDevices = onLinkedDevices,
                        )
                    }
                }
            }
        }
    }
}

/** Mantiene compuesta la pestaña sin trabajo de layout cuando está oculta:
 *  sin `measure` sus LazyColumn no componen ni recolocan items (antes cada
 *  pestaña en segundo plano se medía y colocaba fuera de pantalla igual —
 *  coste de frame por pestaña visitada en dispositivos justos como la TV).
 *  El estado (scroll, ViewModels, collectors) vive en la composición y se
 *  retoma intacto al volver a la pestaña. */
private fun Modifier.keepComposed(visible: Boolean) = layout { measurable, constraints ->
    if (visible) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        layout(0, 0) {}
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

/** Equivalente vertical de [MainBottomBar] para TV: riel fijo a la
 *  izquierda, al margen del overscan y navegable con el D-pad. */
@Composable
private fun TvSideRail(selected: String, onSelect: (String) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.tvSafeArea(),
    ) {
        mainDestinations.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination.route,
                onClick = { onSelect(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
