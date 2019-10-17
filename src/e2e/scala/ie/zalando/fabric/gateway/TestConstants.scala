package ie.zalando.fabric.gateway

import com.softwaremill.sttp._

object TestConstants {

  val TestAppHost                                                            = System.getProperty("autHost")
  val AlternateTestHost                                                      = s"alt-${System.getProperty("autHost")}"
  val WhitelistTestHost                                                      = s"wl-${System.getProperty("autHost")}"
  val StackSetManagedTestHost                                                = s"ss-${System.getProperty("autHost")}"
  val MID2098BugTestHost                                                     = s"bug2098-${System.getProperty("autHost")}"
  val ValidNonWhitelistedToken                                               = System.getProperty("oauthToken")
  val ValidWhiteListToken                                                    = System.getProperty("whiteListToken")
  val ValidResourceWhiteListToken                                            = System.getProperty("resourceWhiteListToken")
  def TestAppBaseUri(host: String = TestAppHost)                             = uri"https://$host"
  def TestAppOtherResource(host: String = TestAppHost)                       = uri"https://$host/other-resource"
  def TestAppResources(host: String = TestAppHost, scheme: String = "https") = uri"$scheme://$host/resources"
  def TestAppResourcesId(host: String = TestAppHost)                         = uri"https://$host/resources/1"
  def TestAppSubResourcesId(host: String = TestAppHost)                      = uri"https://$host/resources/1/sub-resources/1"
  def RateLimitedForAll(host: String = TestAppHost)                          = uri"https://$host/limited/all"
  def RateLimitedForMe(host: String = TestAppHost)                           = uri"https://$host/limited/me"
  def RateLimitedForOther(host: String = TestAppHost)                        = uri"https://$host/limited/other"
  def TestAppResourceById(id: Long, host: String = TestAppHost)              = uri"https://$host/resources/$id"
  def TestAppStarById(id: Long, host: String = TestAppHost)                  = uri"https://$host/starsources/$id"
  def TestInvalidAppStar(host: String = TestAppHost)                         = uri"https://$host/starsources/a/b/c/d"
  def TestAppWhitelistedRoute(host: String = TestAppHost)                    = uri"https://$host/whitelisted"
}
