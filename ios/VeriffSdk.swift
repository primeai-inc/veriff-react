import Foundation
import Veriff
import UIKit

@objc(VeriffSdkReactWrapper)
class VeriffSdk: NSObject {
  /**
  * Indicates that the parameters passed to |launchVeriff([String: AnyObject]], RCTPromiseResolveBlock,RCTPromiseRejectBlock|  were invalid.
  */
  private static let ERROR_INVALID_ARGS = "E_VERIFF_INVALID_ARGUMENTS"

  private static let STATUS_DONE = "STATUS_DONE"
  private static let STATUS_CANCELED = "STATUS_CANCELED"
  private static let STATUS_ERROR = "STATUS_ERROR"

  private static let ERROR_UNABLE_TO_ACCESS_CAMERA = "UNABLE_TO_ACCESS_CAMERA"
  private static let ERROR_UNABLE_TO_ACCESS_MICROPHONE = "ERROR_UNABLE_TO_ACCESS_MICROPHONE"
  private static let ERROR_NETWORK = "NETWORK_ERROR"
  private static let ERROR_SESSION = "SESSION_ERROR"
  private static let ERROR_UNSUPPORTED_SDK_VERSION = "UNSUPPORTED_SDK_VERSION"
  private static let ERROR_UNKNOWN = "UNKNOWN_ERROR"

  private static let DEFAULT_BASE_URL = "https://magic.veriff.me"
  private static let SESSION_TOKEN = "sessionToken"
  private static let BASE_URL = "baseUrl"

  private static let SESSION_URL = "sessionUrl"
  private static let BRANDING = "branding"
  private static let LOCALE = "locale"
  private static let LOGO = "logo"
  private static let USE_CUSTOM_INTRO_SCREEN = "customIntroScreen"
  private static let VENDOR_DATA = "vendorData"

  private static let STATUS = "status"
  private static let ERROR = "error"
  private var resolve: RCTPromiseResolveBlock?
  private var reject: RCTPromiseRejectBlock?

  @objc
  func constantsToExport() -> [String: Any]! {
    return [
      "errorInvalidArgs": VeriffSdk.ERROR_INVALID_ARGS,
      // promise resolve statuses
      "statusCanceled": VeriffSdk.STATUS_CANCELED,
      "statusDone": VeriffSdk.STATUS_DONE,
      "statusError": VeriffSdk.STATUS_ERROR
    ]
  }

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }

  @objc(launchVeriff:resolver:rejecter:)
  func launchVeriff(_ configuration: [String: AnyObject], resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {

    var veriffConfiguration: Veriff.VeriffSdk.Configuration?
    var veriffBranding: Veriff.VeriffSdk.Branding?

    if let brandingConfig = (configuration[VeriffSdk.BRANDING] as? [String: AnyObject]) {
      let branding = Veriff.VeriffSdk.Branding()
      let imageProvider = VeriffSdkImageProvider(imageSource: brandingConfig[VeriffSdk.LOGO])

      if
        let imageURI = brandingConfig[VeriffSdk.LOGO],
        let imageDict = imageURI as? [String: AnyObject],
        let urlString = imageDict["uri"] as? String,
        let url = URL(string: urlString),
        !url.isFileURL
      {
        branding.logoProvider = { imageHandler in
          imageProvider.loadRemoteAsset(url: url) { image in
            guard let image else { return }
            imageHandler(image)
          }
        }
      } else {
        branding.logo = imageProvider.loadLocalAsset()
      }

      // fill the other items in branding
      branding.background = brandingConfig.color(for: "background")
      branding.onBackground = brandingConfig.color(for: "onBackground")
      branding.onBackgroundSecondary = brandingConfig.color(for: "onBackgroundSecondary")
      branding.onBackgroundTertiary = brandingConfig.color(for: "onBackgroundTertiary")
      branding.primary = brandingConfig.color(for: "primary")
      branding.onPrimary = brandingConfig.color(for: "onPrimary")
      branding.secondary = brandingConfig.color(for: "secondary")
      branding.onSecondary = brandingConfig.color(for: "onSecondary")
      branding.cameraOverlay = brandingConfig.color(for: "cameraOverlay")
      branding.onCameraOverlay = brandingConfig.color(for: "onCameraOverlay")
      branding.outline = brandingConfig.color(for: "outline")
      branding.error = brandingConfig.color(for: "error")
      branding.success = brandingConfig.color(for: "success")
      branding.buttonRadius = (brandingConfig["buttonRadius"] as? Double).map { CGFloat($0) }
      if
        let font = brandingConfig["iOSFont"] as? [String: Any],
        let regular = font["regular"] as? String,
        let medium = font["medium"] as? String,
        let bold = font["bold"] as? String
      {
        branding.font = Veriff.VeriffSdk.Branding.Font(regular: regular, medium: medium, bold: bold)
      }

      veriffBranding = branding
    }

    let sessionURL: URL
    if let sessionURLString = configuration[VeriffSdk.SESSION_URL] as? String {
      // Using new API
      guard let url = URL(string: sessionURLString) else {
        return reject(VeriffSdk.ERROR_INVALID_ARGS, "Invalid sessionUrl: \(sessionURLString)", nil)
      }
      sessionURL = url
      var locale: Locale?
      if let languageLocale = (configuration[VeriffSdk.LOCALE] as? String) {
        locale = Locale(identifier: languageLocale)
      }

      veriffConfiguration = Veriff.VeriffSdk.Configuration(branding: veriffBranding, languageLocale: locale)
    } else if let sessionToken = configuration[VeriffSdk.SESSION_TOKEN] as? String {
      // Using deprecated API
      let baseURLString = configuration[VeriffSdk.BASE_URL] as? String ?? VeriffSdk.DEFAULT_BASE_URL
      guard let baseURL = URL(string: baseURLString) else {
        return reject(VeriffSdk.ERROR_INVALID_ARGS, "Invalid baseUrl: \(baseURLString)", nil)
      }
      sessionURL = baseURL.appendingPathComponent(sessionToken)

      veriffConfiguration = Veriff.VeriffSdk.Configuration(branding: veriffBranding)
    } else {
      // Failed to configure
      reject(VeriffSdk.ERROR_INVALID_ARGS, "No sessionUrl or sessionToken in Veriff SDK configuration", nil)
      return
    }

    veriffConfiguration?.customIntroScreen = VeriffSdk.checkCustomIntro(configuration: configuration)
    veriffConfiguration?.vendorData = configuration[VeriffSdk.VENDOR_DATA] as? String

    DispatchQueue.main.async {
       self.resolve = resolve
       self.reject = reject

      let veriff = Veriff.VeriffSdk.shared
      veriff.delegate = self
      veriff.implementationType = .reactNative
      veriff.startAuthentication(sessionUrl: sessionURL.absoluteString, configuration: veriffConfiguration)
    }
  }

  private static func checkCustomIntro(configuration:[String: Any]) -> Bool {
    if let useCustomIntro = configuration[VeriffSdk.USE_CUSTOM_INTRO_SCREEN] as? Bool {
      return useCustomIntro
    } else {
      return false
    }
  }
}

extension VeriffSdk: VeriffSdkDelegate {

  func sessionDidEndWithResult(_ result: Veriff.VeriffSdk.Result) {
    var resultDict: [String: String] = [:]
    switch result.status {
    case .done:
        resultDict[VeriffSdk.STATUS] = VeriffSdk.STATUS_DONE
    case .canceled:
        resultDict[VeriffSdk.STATUS] = VeriffSdk.STATUS_CANCELED
    case .error(let err):
        resultDict[VeriffSdk.STATUS] = VeriffSdk.STATUS_ERROR
        switch err {
        case .cameraUnavailable:
            resultDict[VeriffSdk.ERROR] = VeriffSdk.ERROR_UNABLE_TO_ACCESS_CAMERA
        case .microphoneUnavailable:
            resultDict[VeriffSdk.ERROR] = VeriffSdk.ERROR_UNABLE_TO_ACCESS_MICROPHONE
        case .networkError,
             .uploadError:
            resultDict[VeriffSdk.ERROR] = VeriffSdk.ERROR_NETWORK
        case .serverError,
             .videoFailed,
             .localError:
            resultDict[VeriffSdk.ERROR] = VeriffSdk.ERROR_SESSION
        case .unknown:
            resultDict[VeriffSdk.ERROR] = VeriffSdk.ERROR_UNKNOWN
        case .deprecatedSDKVersion:
            resultDict[VeriffSdk.ERROR] = VeriffSdk.ERROR_UNSUPPORTED_SDK_VERSION
        @unknown default:
            fatalError("Unknown status.")
        }
    @unknown default:
      fatalError("Unknown status.")
    }
    self.resolve?(resultDict)
  }
}

private extension Dictionary where Key == String, Value == AnyObject {
  func image(for key: String) -> UIImage? {
    if
      let imageDict = self[key] as? [String: AnyObject],
      let urlString = imageDict["uri"] as? String,
      let url = URL(string: urlString),
      !url.isFileURL,
      let data = try? Data(contentsOf: url)
    {
      return UIImage(data: data)
    } else {
      return VeriffSdkImageProvider(imageSource: self[key]).loadLocalAsset()
    }
  }

  func color(for key: String) -> UIColor? {
    (self[key] as? String).map { $0.color }
  }
}

private extension String {
  var color: UIColor {
    if starts(with: "#") {
      return String(dropFirst()).color
    }
    var color: UInt64 = 0
    Scanner(string: self).scanHexInt64(&color)
    var a: CGFloat = 1
    if count > 7 {
      // #rrggbbaa
      a = CGFloat(color & 0xff) / 255.0
      color = color >> 8
    }
    let r = CGFloat((color >> 16) & 0xff) / 255.0
    let g = CGFloat((color >> 8) & 0xff) / 255.0
    let b = CGFloat(color & 0xff) / 255.0
    return UIColor(red: r, green: g, blue: b, alpha: a)
  }
}
