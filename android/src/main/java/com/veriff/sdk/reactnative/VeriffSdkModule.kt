package com.veriff.sdk.reactnative

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.veriff.Branding
import com.veriff.Configuration
import com.veriff.Font
import com.veriff.GeneralConfig
import com.veriff.Result
import com.veriff.Sdk.createLaunchIntent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

class VeriffSdkModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private var lastResult: Result? = null
  private var sessionToken: String? = null
  private var status: String? = null

  @ReactMethod
  fun recoverLastResult(promise: Promise) {
    if (lastResult != null && status != null && sessionToken != null) {
      sendResult(lastResult, status, sessionToken, promise)
      lastResult = null
      sessionToken = null
      status = null
    }
  }

  override fun getName(): String {
    return JS_NAME
  }

  override fun getConstants(): Map<String, Any> {
    return EXPORTED_CONSTANTS
  }

  @ReactMethod
  fun launchVeriff(configuration: ReadableMap, promise: Promise) {
    try {
      val sessionToken: String?
      val startUrl: String?
      if (configuration.hasKey(KEY_SESSION_URL) &&
        !configuration.getString(KEY_SESSION_URL).isNullOrEmpty()
      ) {
        startUrl = configuration.getString(KEY_SESSION_URL)
        sessionToken = if (startUrl != null) extractToken(startUrl) else null
        if (startUrl.isNullOrEmpty() || sessionToken.isNullOrEmpty()) {
          promise.reject(ERROR_INVALID_ARGS, "Invalid session url $startUrl")
          return
        }
      } else {
        sessionToken =
          if (configuration.hasKey(KEY_TOKEN)) configuration.getString(KEY_TOKEN) else null
        if (sessionToken.isNullOrEmpty()) {
          promise.reject(
            ERROR_INVALID_ARGS,
            "No sessionToken in Veriff SDK configuration"
          )
          return
        }
        var baseUrl: String? = null
        if (configuration.hasKey(KEY_BASE_URL)) {
          baseUrl = configuration.getString(KEY_BASE_URL)
        }
        if (baseUrl.isNullOrEmpty()) {
          baseUrl = DEFAULT_BASE_URL
        }
        if (baseUrl.endsWith("/")) {
          baseUrl = baseUrl.substring(0, baseUrl.length - 1)
        }
        startUrl = "$baseUrl/v/$sessionToken"
      }

      this.sessionToken = sessionToken
      lastResult = null
      status = null

      val activity = currentActivity
      if (activity == null) {
        promise.reject(ERROR_ACTIVITY_NOT_ATTACHED, "No activity attached while launching Veriff")
        return
      }

      val listener = object : BaseActivityEventListener() {
        override fun onActivityResult(
          activity: Activity,
          requestCode: Int,
          resultCode: Int,
          data: Intent?
        ) {
          if (requestCode == VERIFF_REQUEST_CODE) {
            if (data == null) {
              return
            }
            val token = data.getStringExtra(GeneralConfig.INTENT_EXTRA_SESSION_URL)
            if (sessionToken != token && startUrl != token) {
              return
            }
            reactContext.removeActivityEventListener(this)
            val veriffResult = Result.fromResultIntent(data)
            lastResult = veriffResult
            status = statusToString(getVeriffStatus(resultCode, veriffResult))
            sendResult(veriffResult, status, sessionToken, promise)
          }
        }
      }
      reactContext.addActivityEventListener(listener)
      val configBuilder = Configuration.Builder()
      if (configuration.hasKey(KEY_CUSTOM_INTRO_SCREEN)) {
        configBuilder.customIntroScreen(configuration.getBoolean(KEY_CUSTOM_INTRO_SCREEN))
      }
      if (configuration.hasKey(KEY_BRANDING)) {
        val brandConfig = configuration.getMap(KEY_BRANDING)
        if (brandConfig != null) {
          val branding = Branding.Builder()
          if (brandConfig.hasKey(KEY_LOGO)) {
            handleLogo(activity, KEY_LOGO, branding, brandConfig)
          }
          if (brandConfig.hasKey(KEY_BACKGROUND)) {
            branding.background(parseColor(brandConfig.getString(KEY_BACKGROUND)))
          }
          if (brandConfig.hasKey(KEY_ON_BACKGROUND)) {
            branding.onBackground(parseColor(brandConfig.getString(KEY_ON_BACKGROUND)))
          }
          if (brandConfig.hasKey(KEY_ON_BACKGROUND_SECONDARY)) {
            branding.onBackgroundSecondary(
              parseColor(brandConfig.getString(KEY_ON_BACKGROUND_SECONDARY))
            )
          }
          if (brandConfig.hasKey(KEY_ON_BACKGROUND_TERTIARY)) {
            branding.onBackgroundTertiary(
              parseColor(brandConfig.getString(KEY_ON_BACKGROUND_TERTIARY))
            )
          }
          if (brandConfig.hasKey(KEY_PRIMARY)) {
            branding.primary(parseColor(brandConfig.getString(KEY_PRIMARY)))
          }
          if (brandConfig.hasKey(KEY_ON_PRIMARY)) {
            branding.onPrimary(parseColor(brandConfig.getString(KEY_ON_PRIMARY)))
          }
          if (brandConfig.hasKey(KEY_SECONDARY)) {
            branding.secondary(parseColor(brandConfig.getString(KEY_SECONDARY)))
          }
          if (brandConfig.hasKey(KEY_ON_SECONDARY)) {
            branding.onSecondary(parseColor(brandConfig.getString(KEY_ON_SECONDARY)))
          }
          if (brandConfig.hasKey(KEY_CAMERA_OVERLAY)) {
            branding.cameraOverlay(parseColor(brandConfig.getString(KEY_CAMERA_OVERLAY)))
          }
          if (brandConfig.hasKey(KEY_ON_CAMERA_OVERLAY)) {
            branding.onCameraOverlay(
              parseColor(brandConfig.getString(KEY_ON_CAMERA_OVERLAY))
            )
          }
          if (brandConfig.hasKey(KEY_OUTLINE)) {
            branding.outline(parseColor(brandConfig.getString(KEY_OUTLINE)))
          }
          if (brandConfig.hasKey(KEY_SUCCESS)) {
            branding.success(parseColor(brandConfig.getString(KEY_SUCCESS)))
          }
          if (brandConfig.hasKey(KEY_ERROR)) {
            branding.error(parseColor(brandConfig.getString(KEY_ERROR)))
          }
          if (brandConfig.hasKey(KEY_BUTTON_RADIUS)) {
            branding.buttonRadius(brandConfig.getDouble(KEY_BUTTON_RADIUS).toFloat())
          }
          if (brandConfig.hasKey(KEY_FONT)) {
            brandConfig.getMap(KEY_FONT)?.let { srcFont ->
              val font = buildFont(activity, srcFont)
              branding.font(font)
            }
          }
          configBuilder.branding(branding.build())
        }
      }

      if (configuration.hasKey(KEY_LOCALE)) {
        configuration.getString(KEY_LOCALE)?.let { locale ->
          configBuilder.locale(Locale.forLanguageTag(locale))
        }
      }

      if (configuration.hasKey(KEY_RN_VENDOR_DATA)) {
        configuration.getString(KEY_RN_VENDOR_DATA)?.let { vendorData ->
          configBuilder.vendorData(vendorData)
        }
      }

      val intent = createLaunchIntent(activity, startUrl, configBuilder.build())
      activity.startActivityForResult(intent, VERIFF_REQUEST_CODE)
    } catch (t: Throwable) {
      Log.e(TAG, "starting verification failed", t)
      promise.reject(t)
    }
  }

  private fun sendResult(
    veriffResult: Result?,
    status: String?,
    sessionToken: String?,
    promise: Promise
  ) {
    val result = Arguments.createMap()
    result.putString(KEY_TOKEN, sessionToken)
    result.putString(KEY_STATUS, status)
    if (veriffResult != null && veriffResult.error != null) {
      result.putString(KEY_RESULT_ERROR, codeToError(veriffResult.error))
    }
    promise.resolve(result)
  }

  private fun buildFont(context: Context, src: ReadableMap): Font {
    val regularFont = getFontId(context, src.getString(KEY_FONT_REGULAR))
    val mediumFont = getFontId(context, src.getString(KEY_FONT_MEDIUM))
    val boldFont = getFontId(context, src.getString(KEY_FONT_BOLD))
    Log.v(
      TAG, String.format(
        "Regular font: [%s], value [%s]",
        src.getString(KEY_FONT_REGULAR), regularFont
      )
    )
    Log.v(
      TAG, String.format(
        "Medium font: [%s], value [%s]",
        src.getString(KEY_FONT_MEDIUM), mediumFont
      )
    )
    Log.v(
      TAG, String.format(
        "Bold font: [%s], value [%s]",
        src.getString(KEY_FONT_BOLD), boldFont
      )
    )
    return Font.Builder()
      .setRegular(regularFont)
      .setMedium(mediumFont)
      .setBold(boldFont)
      .build()
  }

  private fun handleLogo(
    context: Context,
    key: String,
    branding: Branding.Builder,
    brandConfig: ReadableMap
  ) {
    val type = brandConfig.getType(key)
    Log.d(TAG, "Logo type$type")
    if (ReadableType.String == type) {
      branding.logo(getDrawableId(context, brandConfig.getString(key)))
    } else if (ReadableType.Map == type) {
      // check if it's a native RN image
      val image = brandConfig.getMap(key)
      if (image != null) {
        if (image.hasKey(KEY_RN_IMAGE_URI)) {
          val url = image.getString(KEY_RN_IMAGE_URI)
          if (!url.isNullOrEmpty()) {
            // check if url has an async scheme
            if (isAsyncLogoUrl(url)) {
              Log.d(TAG, "Async logo$url")
              branding.logo(ReactNativeImageProvider(url))
            } else {
              Log.d(TAG, "Logo from resource$url")
              branding.logo(getDrawableId(context, url))
            }
          } else {
            Log.w(TAG, "Image url is empty: $url")
          }
        } else {
          for (imageKey in image.toHashMap().keys) {
            Log.w(TAG, imageKey)
          }
        }
      } else {
        Log.w(TAG, "Provided image is null")
      }
    } else {
      Log.w(TAG, "Unexpected image type: $type")
    }
  }

  private fun extractToken(startUrl: String): String? {
    // this is suboptimal but it is what it is.. the SDK returns us a token in the
    // INTENT_EXTRA_SESSION_URL for matching :(
    val url = startUrl.toHttpUrlOrNull() ?: return null
    return if (url.encodedPathSegments.size < 2) {
      null
    } else url.encodedPathSegments[1]
  }

  private fun isAsyncLogoUrl(url: String): Boolean {
    return url.startsWith("https://") || url.startsWith("http://") || url.startsWith("file://")
  }

  private fun parseColor(hexcolor: String?): Int {
    if (hexcolor == null) {
      return 0
    }
    if (hexcolor.startsWith("#")) {
      return parseColor(hexcolor.substring(1))
    }
    var color = hexcolor.toLong(16)
    var a = 255
    if (hexcolor.length > 6) {
      a = color.toInt() and 0xff
      color = color ushr 8
    }
    val r = (color ushr 16).toInt() and 0xff
    val g = (color ushr 8).toInt() and 0xff
    val b = (color ushr 0).toInt() and 0xff
    return Color.argb(a, r, g, b)
  }

  companion object {
    /**
     * Indicates that the parameters passed to [.launchVeriff] were invalid.
     */
    private const val ERROR_INVALID_ARGS = "E_VERIFF_INVALID_ARGUMENTS"

    /**
     * Indicates that the activity is no longer attached when the
     * [.launchVeriff] method was invoked.
     */
    private const val ERROR_ACTIVITY_NOT_ATTACHED = "E_VERIFF_ACTIVITY_NOT_ATTACHED"

    /**
     * The user canceled left the flow before completing it.
     */
    private const val STATUS_CANCELED = "STATUS_CANCELED"

    /**
     * The flow was completed, note that this doesn't mean there's a decision yet.
     */
    private const val STATUS_DONE = "STATUS_DONE"

    /**
     * The flow did not complete successfully.
     */
    private const val STATUS_ERROR = "STATUS_ERROR"
    private const val DEFAULT_BASE_URL = "https://magic.veriff.me"
    const val TAG = "@veriff/RN-SDK"
    private const val JS_NAME = "VeriffSdk"
    private const val VERIFF_REQUEST_CODE = 47239
    private const val KEY_TOKEN = "sessionToken"
    private const val KEY_BASE_URL = "baseUrl"
    private const val KEY_SESSION_URL = "sessionUrl"
    private const val KEY_BRANDING = "branding"
    private const val KEY_LOCALE = "locale"
    private const val KEY_STATUS = "status"
    private const val KEY_RESULT_ERROR = "result_error"
    private const val KEY_LOGO = "logo"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_ON_BACKGROUND = "onBackground"
    private const val KEY_ON_BACKGROUND_SECONDARY = "onBackgroundSecondary"
    private const val KEY_ON_BACKGROUND_TERTIARY = "onBackgroundTertiary"
    private const val KEY_PRIMARY = "primary"
    private const val KEY_ON_PRIMARY = "onPrimary"
    private const val KEY_SECONDARY = "secondary"
    private const val KEY_ON_SECONDARY = "onSecondary"
    private const val KEY_CAMERA_OVERLAY = "cameraOverlay"
    private const val KEY_ON_CAMERA_OVERLAY = "onCameraOverlay"
    private const val KEY_OUTLINE = "outline"
    private const val KEY_SUCCESS = "success"
    private const val KEY_ERROR = "error"
    const val KEY_BUTTON_RADIUS = "buttonRadius"
    private const val KEY_CUSTOM_INTRO_SCREEN = "customIntroScreen"
    private const val KEY_RN_IMAGE_URI = "uri"
    private const val KEY_RN_VENDOR_DATA = "vendorData"
    private const val KEY_FONT = "androidFont"
    private const val KEY_FONT_REGULAR = "regular"
    private const val KEY_FONT_MEDIUM = "medium"
    private const val KEY_FONT_BOLD = "bold"
    private const val ERROR_UNABLE_TO_ACCESS_CAMERA = "UNABLE_TO_ACCESS_CAMERA"
    private const val ERROR_UNABLE_TO_RECORD_AUDIO = "UNABLE_TO_RECORD_AUDIO"
    private const val ERROR_UNABLE_TO_START_CAMERA = "UNABLE_TO_START_CAMERA"
    private const val ERROR_NO_IDENTIFICATION_METHODS_AVAILABLE =
      "NO_IDENTIFICATION_METHODS_AVAILABLE"
    private const val ERROR_UNSUPPORTED_SDK_VERSION = "UNSUPPORTED_SDK_VERSION"
    private const val ERROR_SESSION = "SESSION_ERROR"
    private const val ERROR_SETUP = "SETUP_ERROR"
    private const val ERROR_NETWORK = "NETWORK_ERROR"
    private const val ERROR_UNKNOWN = "UNKNOWN_ERROR"
    private const val ERROR_NFC_DISABLED = "NFC_DISABLED"
    private const val ERROR_DEVICE_HAS_NO_NFC = "DEVICE_HAS_NO_NFC"
    private val EXPORTED_CONSTANTS: MutableMap<String, Any> = HashMap()

    init {
      // promise reject errors
      EXPORTED_CONSTANTS["errorInvalidArgs"] = ERROR_INVALID_ARGS
      EXPORTED_CONSTANTS["errorActivityNotAttached"] =
        ERROR_ACTIVITY_NOT_ATTACHED

      // promise resolve statuses
      EXPORTED_CONSTANTS["statusCanceled"] = STATUS_CANCELED
      EXPORTED_CONSTANTS["statusDone"] = STATUS_DONE
      EXPORTED_CONSTANTS["statusError"] = STATUS_ERROR
    }

    private fun getVeriffStatus(resultCode: Int, veriffResult: Result?): Result.Status {
      return veriffResult?.status
        ?: if (resultCode == Activity.RESULT_CANCELED) Result.Status.CANCELED else Result.Status.ERROR
    }

    private fun getDrawableId(context: Context, name: String?): Int {
      return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    private fun getFontId(context: Context, name: String?): Int {
      return context.resources.getIdentifier(name, "font", context.packageName)
    }

    private fun codeToError(error: Result.Error?): String {
      return when (error) {
        Result.Error.UNABLE_TO_ACCESS_CAMERA -> ERROR_UNABLE_TO_ACCESS_CAMERA
        Result.Error.UNABLE_TO_RECORD_AUDIO -> ERROR_UNABLE_TO_RECORD_AUDIO
        Result.Error.UNABLE_TO_START_CAMERA -> ERROR_UNABLE_TO_START_CAMERA
        Result.Error.UNSUPPORTED_SDK_VERSION -> ERROR_UNSUPPORTED_SDK_VERSION
        Result.Error.SESSION_ERROR -> ERROR_SESSION
        Result.Error.NETWORK_ERROR -> ERROR_NETWORK
        Result.Error.SETUP_ERROR -> ERROR_SETUP
        Result.Error.NFC_DISABLED -> ERROR_NFC_DISABLED
        Result.Error.DEVICE_HAS_NO_NFC -> ERROR_DEVICE_HAS_NO_NFC
        Result.Error.UNKNOWN_ERROR -> ERROR_UNKNOWN
        else -> ERROR_UNKNOWN
      }
    }

    private fun statusToString(status: Result.Status): String {
      return when (status) {
        Result.Status.DONE -> STATUS_DONE
        Result.Status.CANCELED -> STATUS_CANCELED
        else -> STATUS_ERROR
      }
    }
  }
}
