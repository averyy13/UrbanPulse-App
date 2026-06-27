package com.example.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.screens.HomeScreen
import com.example.screens.ReportIssueScreen
import com.example.screens.LoginScreen
import com.example.screens.RegisterScreen
import com.example.screens.ForgotPasswordScreen
import com.example.screens.ProfileScreen
import com.example.screens.MyReportsScreen
import com.example.screens.MapScreen
import com.example.screens.ReportDetailsScreen
import com.example.viewmodel.AuthState
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.IssueViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")
    object Home : Screen("home")
    object Report : Screen("report")
    object Profile : Screen("profile")
    object MyReports : Screen("my_reports")
    object Map : Screen("map")
    object ReportDetails : Screen("details/{issueId}") {
        fun createRoute(issueId: String) = "details/$issueId"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel { AuthViewModel() }
) {
    val authState by authViewModel.authState.collectAsState()
    
    // Instantiate a shared IssueViewModel so reports and votes are instantly synchronized
    val issueViewModel: IssueViewModel = viewModel { IssueViewModel() }

    // Determine initial route: if successfully logged in, start at Home; otherwise Login.
    val startDestination = if (authState is AuthState.Success) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                viewModel = authViewModel
            )
        }
        
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
                viewModel = authViewModel
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = authViewModel
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToReport = { navController.navigate(Screen.Report.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToDetails = { id -> navController.navigate(Screen.ReportDetails.createRoute(id)) },
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                viewModel = issueViewModel
            )
        }
        
        composable(Screen.Report.route) {
            ReportIssueScreen(
                onNavigateBack = { navController.popBackStack() },
                issueViewModel = issueViewModel,
                authViewModel = authViewModel
            )
        }

        composable(Screen.Map.route) {
            MapScreen(
                onNavigateToDetails = { id -> navController.navigate(Screen.ReportDetails.createRoute(id)) },
                onNavigateToHome = { navController.navigate(Screen.Home.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToReport = { navController.navigate(Screen.Report.route) },
                viewModel = issueViewModel
            )
        }

        composable(
            route = Screen.ReportDetails.route,
            arguments = listOf(navArgument("issueId") { type = NavType.StringType })
        ) { backStackEntry ->
            val issueId = backStackEntry.arguments?.getString("issueId") ?: ""
            ReportDetailsScreen(
                issueId = issueId,
                onNavigateBack = { navController.popBackStack() },
                issueViewModel = issueViewModel,
                authViewModel = authViewModel
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToMyReports = {
                    navController.navigate(Screen.MyReports.route)
                },
                viewModel = authViewModel,
                issueViewModel = issueViewModel
            )
        }
        
        composable(Screen.MyReports.route) {
            MyReportsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { id -> navController.navigate(Screen.ReportDetails.createRoute(id)) },
                issueViewModel = issueViewModel,
                authViewModel = authViewModel
            )
        }
    }
}
