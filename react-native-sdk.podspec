require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-sdk"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.description  = <<-DESC
                  react-native-sdk
                   DESC
  s.homepage     = "https://github.com/veriff/react-native-sdk"
  s.license      = "MIT"
  # s.license    = { :type => "MIT", :file => "FILE_LICENSE" }
  s.authors      = { "Veriff" => "support@veriff.com" }
  s.platforms    = { :ios => "13.4" }
  s.source       = { :git => "https://github.com/veriff/react-native-sdk.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,swift}"
  s.requires_arc = true

  s.dependency "React"
  s.dependency "VeriffSDK", "7.6.0"
end
