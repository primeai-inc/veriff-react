import Foundation
import Veriff
import UIKit.UIImage

@objc(VeriffSdkImageProvider)
final class VeriffSdkImageProvider: NSObject {
  private let imageSource: Any?

  init(imageSource: Any?) {
    self.imageSource = imageSource
    super.init()
  }

  func loadLocalAsset() -> UIImage? {
    if let imageURI = imageSource {
      if let imageDict = imageURI as? [String: AnyObject],
         let urlString = imageDict["uri"] as? String {
        if let url = URL(string: urlString),
           url.isFileURL {
          return UIImage(contentsOfFile: url.path)
        } else {
          return UIImage(named: urlString)
        }
      } else {
        let imageKey = imageURI as! String
        return UIImage(named: imageKey)
      }
    } else {
      return UIImage()
    }
  }

  func loadRemoteAsset(url: URL, completion: @escaping (UIImage?) -> Void) {
    if url.isFileURL {
        guard let image = UIImage(contentsOfFile: url.path) else {
          completion(nil)
          return
        }
      completion(image)
    } else {
      URLSession.shared.dataTask(with: url) { (data, urlResponse, error) in
        guard let data = data,
              let image = UIImage(data: data),
              error == nil else {
          completion(nil)
          return
        }
        DispatchQueue.main.async {
          completion(image)
        }
      }.resume()
    }
  }
}
