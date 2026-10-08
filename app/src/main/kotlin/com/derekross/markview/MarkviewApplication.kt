package com.derekross.markview

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.derekross.markview.core.data.AppContainer
import com.derekross.markview.core.data.AppContainerOwner
import com.derekross.markview.core.render.MarkviewImageLoader

class MarkviewApplication : Application(), AppContainerOwner, SingletonImageLoader.Factory {
    override val container: AppContainer by lazy { AppContainer(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader = MarkviewImageLoader.create(context)
}
