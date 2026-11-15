package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AboutDataScreen
import com.example.ui.screens.DiscoveriesScreen
import com.example.ui.screens.InvestigateScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.theme.EarthPulseTheme
import com.example.ui.viewmodel.EarthPulseViewModel

object EarthPulseRoutes {
    const val OVERVIEW = "overview"
    const val INVESTIGATE = "investigate"
    const val RESULTS = "results"
    const val DISCOVERIES = "discoveries"
    const val ABOUT = "about"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EarthPulseTheme {
                EarthPulseApp()
            }
        }
    }
}

@Composable
fun EarthPulseApp(
    viewModel: EarthPulseViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: EarthPulseRoutes.OVERVIEW

    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
    val aiExplanation by viewModel.aiExplanation.collectAsStateWithLifecycle()
    val aiSuggestion by viewModel.aiVariableSuggestion.collectAsStateWithLifecycle()
    val savedDiscoveries by viewModel.savedDiscoveries.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val pendingDiscard by viewModel.pendingDiscardConfirmation.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val destinations = remember {
        listOf(
            NavDestination(
                route = EarthPulseRoutes.OVERVIEW,
                label = "Overview",
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_overview",
            ),
            NavDestination(
                route = EarthPulseRoutes.INVESTIGATE,
                label = "Investigate",
                selectedIcon = Icons.Filled.Explore,
                unselectedIcon = Icons.Outlined.Explore,
                testTag = "nav_investigate",
            ),
            NavDestination(
                route = EarthPulseRoutes.DISCOVERIES,
                label = "Discoveries",
                selectedIcon = Icons.Filled.Bookmarks,
                unselectedIcon = Icons.Outlined.Bookmarks,
                testTag = "nav_discoveries",
            ),
            NavDestination(
                route = EarthPulseRoutes.ABOUT,
                label = "About & Data",
                selectedIcon = Icons.Filled.MenuBook,
                unselectedIcon = Icons.Outlined.MenuBook,
                testTag = "nav_about",
            ),
        )
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToResults() {
        navController.navigate(EarthPulseRoutes.RESULTS) {
            launchSingleTop = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route ||
                                (dest.route == EarthPulseRoutes.INVESTIGATE && currentRoute == EarthPulseRoutes.RESULTS)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route ||
                                (dest.route == EarthPulseRoutes.INVESTIGATE && currentRoute == EarthPulseRoutes.RESULTS)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = EarthPulseRoutes.OVERVIEW,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(EarthPulseRoutes.OVERVIEW) {
                        OverviewScreen(
                            onStartInvestigation = { navigateTopLevel(EarthPulseRoutes.INVESTIGATE) },
                            onLaunchDemo = { demo ->
                                viewModel.launchFeaturedDemo(demo) { navigateToResults() }
                            },
                            onOpenAboutData = { navigateTopLevel(EarthPulseRoutes.ABOUT) },
                        )
                    }

                    composable(EarthPulseRoutes.INVESTIGATE) {
                        InvestigateScreen(
                            formState = formState,
                            aiSuggestion = aiSuggestion,
                            showDiscardDialog = pendingDiscard,
                            onStepSelected = viewModel::setStep,
                            onLocationQueryChange = viewModel::updateLocationQuery,
                            onSelectRegion = viewModel::selectRegion,
                            onSelectCustomPoint = viewModel::selectCustomCoordinates,
                            onApplyCustomInputs = viewModel::applyCustomLatLonInputs,
                            onToggleVariable = viewModel::toggleVariable,
                            onAskAiSuggestion = viewModel::askAiForVariableSuggestion,
                            onApplyAiSuggestion = viewModel::applySuggestedVariables,
                            onDateRangeChange = viewModel::updateDateRange,
                            onToggleAdvanced = viewModel::toggleAdvancedSettings,
                            onAggregationChange = viewModel::updateAggregation,
                            onTrendMethodChange = viewModel::updateTrendMethod,
                            onSlopeMethodChange = viewModel::updateSlopeMethod,
                            onAlphaChange = viewModel::updateAlpha,
                            onDataSourceModeChange = viewModel::updateDataSourceMode,
                            onAnalyseClick = {
                                viewModel.requestAnalysis(forceDiscardPrevious = false) {
                                    navigateToResults()
                                }
                            },
                            onConfirmDiscardAndAnalyse = {
                                viewModel.requestAnalysis(forceDiscardPrevious = true) {
                                    navigateToResults()
                                }
                            },
                            onDismissDiscardDialog = viewModel::dismissDiscardConfirmation,
                            onOpenAboutData = { navigateTopLevel(EarthPulseRoutes.ABOUT) },
                        )
                    }

                    composable(EarthPulseRoutes.RESULTS) {
                        ResultsScreen(
                            analysisState = analysisState,
                            aiExplanation = aiExplanation,
                            onBackToInvestigate = {
                                navigateTopLevel(EarthPulseRoutes.INVESTIGATE)
                            },
                            onSaveResult = viewModel::saveCurrentResult,
                            onCompareRegion = viewModel::runRegionComparison,
                            onBuildExportCsv = viewModel::buildExportCsv,
                            onBuildExportJson = viewModel::buildExportJson,
                            onNotifyToast = viewModel::showToast,
                            onOpenAboutData = { navigateTopLevel(EarthPulseRoutes.ABOUT) },
                        )
                    }

                    composable(EarthPulseRoutes.DISCOVERIES) {
                        DiscoveriesScreen(
                            savedDiscoveries = savedDiscoveries,
                            filterState = filterState,
                            onFilterChange = viewModel::updateDiscoveryFilter,
                            onOpenSavedDiscovery = { summary ->
                                viewModel.openSavedDiscovery(summary) { navigateToResults() }
                            },
                            onDeleteDiscovery = viewModel::deleteDiscovery,
                            onLaunchDemo = { demo ->
                                viewModel.launchFeaturedDemo(demo) { navigateToResults() }
                            },
                            onStartNewInvestigation = { navigateTopLevel(EarthPulseRoutes.INVESTIGATE) },
                            onOpenAboutData = { navigateTopLevel(EarthPulseRoutes.ABOUT) },
                        )
                    }

                    composable(EarthPulseRoutes.ABOUT) {
                        AboutDataScreen()
                    }
                }
            }
        }
    }
}
