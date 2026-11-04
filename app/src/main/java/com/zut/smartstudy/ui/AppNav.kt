package com.zut.smartstudy.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: StudyViewModel = viewModel() // activity-scoped, shared by all screens

    NavHost(navController = nav, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                vm = vm,
                onPickDocument = { nav.navigate("picker") },
                onOpenNote = { nav.navigate("summary") }
            )
        }
        composable("picker") {
            PickerScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onGenerated = { nav.navigate("summary") }
            )
        }
        composable("summary") {
            SummaryScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
