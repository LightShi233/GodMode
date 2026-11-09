package tiiehenry.viewcontroller.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tiiehenry.viewcontroller.rule.ViewRule
import tiiehenry.viewcontroller.ui.screens.HomeScreen
import tiiehenry.viewcontroller.ui.screens.RuleDetailScreen
import tiiehenry.viewcontroller.ui.screens.RuleListScreen

object Routes {
    const val HOME = "home"
    const val RULE_LIST = "rule_list"
    const val RULE_DETAIL = "rule_detail/{timestamp}"

    fun ruleDetail(timestamp: Long) = "rule_detail/$timestamp"
}

/**
 * Compose Navigation 图（取代 Fragment 事务栈）
 */
@Composable
fun GmNavHost(
    viewModel: GmViewModel,
) {
    val navController = rememberNavController()

    // 详情页通过 ViewModel 持有当前规则引用（ViewRule 非序列化友好，用 timestamp 作路由参数）
    var currentRule: ViewRule? = null

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(250),
            ) + fadeIn(tween(250))
        },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(250),
            ) + fadeOut(tween(250))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenApp = { navController.navigate(Routes.RULE_LIST) },
            )
        }

        composable(Routes.RULE_LIST) {
            RuleListScreen(
                viewModel = viewModel,
                packageName = viewModel.selectedPackage ?: "",
                onBack = { navController.popBackStack() },
                onOpenRule = { rule ->
                    currentRule = rule
                    navController.navigate(Routes.ruleDetail(rule.timestamp))
                },
            )
        }

        composable(
            route = Routes.RULE_DETAIL,
            arguments = listOf(navArgument("timestamp") { type = NavType.LongType }),
        ) { entry ->
            val timestamp = entry.arguments?.getLong("timestamp") ?: 0L
            val rule = currentRule?.takeIf { it.timestamp == timestamp }
                ?: viewModel.viewRules.value?.firstOrNull { it.timestamp == timestamp }
            if (rule != null) {
                RuleDetailScreen(
                    viewModel = viewModel,
                    rule = rule,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
