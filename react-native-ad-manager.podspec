require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "@ascendeum_ads/rn-gam-aps-nimbus"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.description  = <<-DESC
                  react-native-ad-manager
                   DESC
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "11.0" }
  s.source       = { :git => "https://github.com/ascendeum/react-native-ad-manager.git", :tag => "v#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm}"
  s.requires_arc = true

  s.dependency "React-Core"
  s.dependency 'Google-Mobile-Ads-SDK', '~> 9.0.0'
  s.dependency 'AmazonPublisherServicesSDK', '4.4.3'
  s.dependency 'AmazonPublisherServicesAdMobAdapter', '2.1.1'
  s.dependency "GoogleMobileAdsMediationFacebook"
end
