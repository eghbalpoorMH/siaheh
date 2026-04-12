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
import ir.siaheh.ui.components.LoadingIndicator
import ir.siaheh.ui.components.UpdateDialog
import ir.siaheh.ui.group.GroupChatScreen
import ir.siaheh.ui.group.GroupListScreen
import ir.siaheh.ui.group.GroupMembersScreen

object Routes {
    const val LOGIN = "login"
    const val OTP_VERIFY = "otp_verify/{phone}"
    const val GROUPS = "groups"
    const val GROUP_CHAT = "group/{groupId}"
    const val GROUP_MEMBERS = "group/{groupId}/members"

    fun otpVerify(phone: String) = "otp_verify/$phone"
    fun groupChat(groupId: String) = "group/$groupId"
    fun groupMembers(groupId: String) = "group/$groupId/members"
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

    val startDestination = if (uiState.isAuthenticated) Routes.GROUPS else Routes.LOGIN

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
                    authViewModel.verifyOtp(phone, code) {
                        navController.navigate(Routes.GROUPS) {
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
        composable(Routes.GROUPS) {
            GroupListScreen(
                onOpenGroup = { groupId -> navController.navigate(Routes.groupChat(groupId)) },
            )
        }
        composable(
            Routes.GROUP_CHAT,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupChatScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onOpenMembers = { navController.navigate(Routes.groupMembers(groupId)) },
            )
        }
        composable(
            Routes.GROUP_MEMBERS,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupMembersScreen(groupId = groupId, onBack = { navController.popBackStack() })
        }
    }
}
