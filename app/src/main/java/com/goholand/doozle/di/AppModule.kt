package com.goholand.doozle.di

import com.goholand.doozle.data.ProjectRepository
import com.goholand.doozle.data.ProjectRepositoryImpl
import com.goholand.doozle.ui.screens.picker.ProjectPickerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.File

val appModule = module {
    single<ProjectRepository> {
        ProjectRepositoryImpl(File(androidContext().filesDir, "doozle"))
    }

    viewModel { ProjectPickerViewModel(get()) }
}
