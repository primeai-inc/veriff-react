package com.veriff.sdk.reactnative

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.Log
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.veriff.Branding
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("LongLogTag")
class ReactNativeImageProvider(private val url: String?) : Branding.DrawableProvider, Parcelable {

  private val main = Handler(Looper.getMainLooper())

  protected constructor(`in`: Parcel) : this(`in`.readString())

  @Throws(IOException::class)
  override fun loadImage(context: Context): Drawable {
    val request = ImageRequestBuilder.fromRequest(ImageRequest.fromUri(url)).build()
    val source = Fresco.getImagePipeline().fetchDecodedImage(request, null)
    val imageLoadLatch = CountDownLatch(1)
    val atomicResult = AtomicReference<Result?>(null)
    source.subscribe(object : BaseBitmapDataSubscriber() {
      override fun onNewResultImpl(bitmap: Bitmap?) {
        if (bitmap == null) {
          atomicResult.set(Result(IOException("Loaded bitmap was null")))
        } else {
          atomicResult.set(Result(bitmap.copy(bitmap.config, false)))
        }
        imageLoadLatch.countDown()
      }

      override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage?>>) {
        when (val failure = dataSource.failureCause) {
          null -> {
            atomicResult.set(Result(IOException("Provided failure cause was null")))
          }

          is IOException -> {
            atomicResult.set(
              Result(
                failure
              )
            )
          }

          else -> {
            atomicResult.set(Result(IOException("Failed loading image", failure)))
          }
        }
        imageLoadLatch.countDown()
      }
    }) { r: Runnable -> main.post(r) }
    return try {
      imageLoadLatch.await()
      val result = atomicResult.get()
      if (result?.error != null) {
        Log.w(
          TAG,
          "[ReactNativeImageProvider] Loading image from [$url] failed",
          result.error
        )
        throw result.error
      } else {
        Log.w(
          TAG,
          "[ReactNativeImageProvider] Loading image [" + url + "]  got a bitmap with size w=" + result?.bitmap?.width + " h=" + result?.bitmap?.height
        )
        BitmapDrawable(context.resources, result?.bitmap)
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt() // preserve interrupt status
      throw IOException("Interrupted while loading image")
    }
  }
  override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeString(url)

  override fun describeContents() = 0

  companion object {
    private const val TAG = "ReactNativeImageProvider"

    @JvmField
    val CREATOR: Creator<ReactNativeImageProvider> =
      object : Creator<ReactNativeImageProvider> {
        override fun createFromParcel(`in`: Parcel): ReactNativeImageProvider {
          return ReactNativeImageProvider(`in`)
        }

        override fun newArray(size: Int): Array<ReactNativeImageProvider?> {
          return arrayOfNulls(size)
        }
      }
  }
}

private class Result {
  val error: IOException?
  val bitmap: Bitmap?

  constructor(error: IOException) {
    this.error = error
    bitmap = null
  }

  constructor(bitmap: Bitmap) {
    error = null
    this.bitmap = bitmap
  }
}
