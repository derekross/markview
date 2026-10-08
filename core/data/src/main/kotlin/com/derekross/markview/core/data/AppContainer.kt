package com.derekross.markview.core.data

import android.content.Context

/** Manual dependency container; one instance per process, owned by the Application. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val documents = DocumentRepository(appContext)
    val library = LibraryRepository(appContext)
    val settings = SettingsRepository(appContext)
    val folders = FolderRepository(appContext)
}

/** Implemented by the Application so features can reach the container without a DI framework. */
interface AppContainerOwner {
    val container: AppContainer
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AppContainerOwner).container
