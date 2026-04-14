package ir.siaheh.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.siaheh.ui.auth.AuthViewModel
import ir.siaheh.ui.auth.LoginScreen
import ir.siaheh.ui.auth.OtpVerifyScreen
import ir.siaheh.ui.auth.ProfileSetupScreen
import ir.siaheh.ui.components.LoadingIndicator
import ir.siaheh.ui.components.UpdateDialog
import ir.siaheh.ui.space.SpaceChatScreen
import ir.siaheh.ui.space.SpaceListScreen
import ir.siaheh.ui.space.SpaceMembersScreen

object Routes {
    const val LOGIN = "login"
    const val OTP_VERIFY = "otp_verify/{phone}"
    const val PROFILE_SETUP = "profile_setup"
    const val SPACES = "spaces"
    const val SPACE_CHAT = "space/{spaceId}"
    const val SPACE_MEMBERS = "space/{spaceId}/members"

    fun otpVerify(phone: String) = "otp_verify/$phone"
    fun spaceChat(spaceId: String) = "space/$spaceId"
    fun spaceMembers(spaceId: String) = "space/$spaceId/members"
}

@Composable
fun SiahehNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val uiState by authViewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        LoadingIndicator(fullScreen = true)
        return
    }

    val startDestination = if (uiState.isAuthenticated) {
        if (uiState.needsProfileSetup) Routes.PROFILE_SETUP else Routes.SPACES
    } else {
        Routes.LOGIN
    }

    uiState.updateInfo?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { authViewModel.dismissUpdate() },
        )
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                isLoading = uiState.isLoading,
                error = uiState.error,
                onRequestOtp = { phone ->
                    authViewModel.requestOtp(phone) {
                        navController.navigate(Routes.otpVerify(phone))
                    }
                },
                onClearError = { authViewModel.clearError() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.OTP_VERIFY,
            arguments = listOf(navArgument("phone") { type = NavType.StringType }),
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            OtpVerifyScreen(
                phone = phone,
                isLoading = uiState.isLoading,
                error = uiState.error,
                otpExpiresIn = uiState.otpExpiresIn,
                otpChannels = uiState.otpChannels,
                onVerify = { code ->
                    authViewModel.verifyOtp(phone, code) { needsProfileSetup ->
                        val next = if (needsProfileSetup) Routes.PROFILE_SETUP else Routes.SPACES
                        navController.navigate(next) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                },
                onResend = { authViewModel.requestOtp(phone) },
                onEditPhone = { navController.popBackStack() },
                onClearError = { authViewModel.clearError() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                isLoading = uiState.isLoading,
                currentDisplayName = uiState.user?.displayName.orEmpty(),
                currentUsername = uiState.user?.username.orEmpty(),
                onSubmit = { displayName, username, about, avatarUri ->
                    authViewModel.completeProfile(displayName, username, about, avatarUri) {
                        navController.navigate(Routes.SPACES) {
                            popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Routes.SPACES) {
            SpaceListScreen(
                onOpenSpace = { spaceId -> navController.navigate(Routes.spaceChat(spaceId)) },
            )
        }
        composable(
            Routes.SPACE_CHAT,
            arguments = listOf(navArgument("spaceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val spaceId = backStackEntry.arguments?.getString("spaceId") ?: ""
            SpaceChatScreen(
                spaceId = spaceId,
                onBack = { navController.popBackStack() },
                onOpenMembers = { navController.navigate(Routes.spaceMembers(spaceId)) },
            )
        }
        composable(
            Routes.SPACE_MEMBERS,
            arguments = listOf(navArgument("spaceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val spaceId = backStackEntry.arguments?.getString("spaceId") ?: ""
            SpaceMembersScreen(spaceId = spaceId, onBack = { navController.popBackStack() })
        }
    }
}
